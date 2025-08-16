package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.AssignmentRequest;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.AssignmentResult;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.ConfirmedAssignment;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.ConflictException;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.HolidayConfirmationRequest;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.HolidayDecision;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.HolidayWarning;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.ScheduleAssignment;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.ScheduleConflict;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.ValidationException;
import sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.ValidationResult;
import sp.sistemaspalacios.api_chronos.dto.EmployeeResponse;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio principal de asignaciones.
 * - Crea schedules + días/bloques
 * - Conflictos y festivos
 * - Integra con ScheduleAssignmentGroupService para que se GUARDE en schedule_assignment_group
 */
@Service
public class EmployeeScheduleService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final RestTemplate restTemplate;
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final GeneralConfigurationService configService;
    private final HolidayService holidayService;

    // >>> NUEVO: inyección del servicio de grupos
    private final ScheduleAssignmentGroupService groupService;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public EmployeeScheduleService(
            EmployeeScheduleRepository employeeScheduleRepository,
            ShiftsRepository shiftsRepository,
            RestTemplate restTemplate,
            EmployeeScheduleDayRepository employeeScheduleDayRepository,
            GeneralConfigurationService configService,
            HolidayService holidayService,
            ScheduleAssignmentGroupService groupService
    ) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.restTemplate = restTemplate;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.configService = configService;
        this.holidayService = holidayService;
        this.groupService = groupService;
    }

    // =================== HELPERS FECHA SEGUROS ===================

    /** java.util.Date → LocalDate (maneja java.sql.Date sin toInstant()) */
    private static LocalDate toLocalDate(java.util.Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** java.sql.Date → LocalDate */
    private static LocalDate toLocalDate(java.sql.Date date) {
        if (date == null) return null;
        return date.toLocalDate();
    }

    // =================== ASIGNAR MÚLTIPLES ===================

    @Transactional
    public AssignmentResult processMultipleAssignments(AssignmentRequest request) {

        validateRequest(request);

        // 1) Conflictos
        List<ScheduleConflict> conflicts = detectScheduleConflicts(request.getAssignments());
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Conflictos de horarios detectados", conflicts);
        }

        // 2) Festivos (para confirmar)
        List<HolidayWarning> holidayWarnings = detectHolidayWarnings(request.getAssignments());
        if (!holidayWarnings.isEmpty()) {
            AssignmentResult preview = new AssignmentResult();
            preview.setSuccess(false);
            preview.setMessage("Se detectaron días festivos");
            preview.setHolidayWarnings(holidayWarnings);
            preview.setRequiresConfirmation(true);
            return preview;
        }

        // 3) Crear schedules + días/bloques
        List<EmployeeSchedule> created = new ArrayList<>();
        for (ScheduleAssignment assignment : request.getAssignments()) {
            EmployeeSchedule schedule = createScheduleFromAssignment(assignment);
            schedule.setDays(new ArrayList<>());
            EmployeeSchedule saved = employeeScheduleRepository.save(schedule);
            generateScheduleDays(saved);
            created.add(employeeScheduleRepository.save(saved));
        }

        // 4) AGRUPAR por empleado y CREAR/ACTUALIZAR grupo (¡¡aquí se guarda en schedule_assignment_group!!)
        Map<Long, List<Long>> idsPorEmpleado = created.stream()
                .collect(Collectors.groupingBy(EmployeeSchedule::getEmployeeId,
                        Collectors.mapping(EmployeeSchedule::getId, Collectors.toList())));

        for (Map.Entry<Long, List<Long>> e : idsPorEmpleado.entrySet()) {
            Long employeeId = e.getKey();
            List<Long> scheduleIds = e.getValue();
            // Esto persiste/actualiza el grupo aplicando la REGLA: horas correctas por semana
            groupService.processScheduleAssignment(employeeId, scheduleIds);
        }

        // 5) Resumen de horas por empleado basado en el grupo más reciente
        List<sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary> summaries =
                idsPorEmpleado.keySet().stream()
                        .map(this::calculateEmployeeHoursSummary)
                        .collect(Collectors.toList());

        AssignmentResult result = new AssignmentResult();
        result.setSuccess(true);
        result.setMessage("Turnos asignados correctamente");
        result.setUpdatedEmployees(summaries);
        result.setRequiresConfirmation(false);

        return result;
    }

    // =================== CONFIRMAR FESTIVOS ===================

    @Transactional
    public AssignmentResult processHolidayAssignment(HolidayConfirmationRequest request) {
        if (request == null || request.getConfirmedAssignments() == null || request.getConfirmedAssignments().isEmpty()) {
            throw new IllegalArgumentException("confirmedAssignments es requerido");
        }

        List<EmployeeSchedule> created = new ArrayList<>();

        for (ConfirmedAssignment ca : request.getConfirmedAssignments()) {
            // validar turno existe
            shiftsRepository.findById(ca.getShiftId())
                    .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + ca.getShiftId()));

            EmployeeSchedule schedule = createScheduleFromConfirmedAssignment(ca);
            schedule.setDays(new ArrayList<>());
            EmployeeSchedule saved = employeeScheduleRepository.save(schedule);

            List<HolidayDecision> safe = (ca.getHolidayDecisions() != null) ? ca.getHolidayDecisions() : Collections.emptyList();
            generateScheduleDaysWithHolidayDecisions(saved, safe);

            created.add(employeeScheduleRepository.save(saved));
        }

        // Actualizar/crear grupos por empleado
        Map<Long, List<Long>> idsPorEmpleado = created.stream()
                .collect(Collectors.groupingBy(EmployeeSchedule::getEmployeeId,
                        Collectors.mapping(EmployeeSchedule::getId, Collectors.toList())));

        for (Map.Entry<Long, List<Long>> e : idsPorEmpleado.entrySet()) {
            groupService.processScheduleAssignment(e.getKey(), e.getValue());
        }

        // Resumen
        List<sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary> summaries =
                idsPorEmpleado.keySet().stream()
                        .map(this::calculateEmployeeHoursSummary)
                        .collect(Collectors.toList());

        AssignmentResult result = new AssignmentResult();
        result.setSuccess(true);
        result.setMessage("Turnos asignados correctamente con decisiones de festivos");
        result.setUpdatedEmployees(summaries);
        result.setRequiresConfirmation(false);

        return result;
    }

    // =================== RESUMEN DE HORAS (usa GRUPOS => horas correctas por semana) ===================

    public sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary
    calculateEmployeeHoursSummary(Long employeeId) {

        ScheduleAssignmentGroupDTO latest = groupService.getLatestGroupForEmployee(employeeId);

        sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary s =
                new sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary();
        s.setEmployeeId(employeeId);

        if (latest == null) {
            s.setTotalHours(0.0);
            s.setAssignedHours(0.0);
            s.setOvertimeHours(0.0);
            s.setOvertimeType("Normal");
            s.setFestivoHours(0.0);          // NUEVO
            s.setFestivoType(null);          // NUEVO
            s.setOvertimeBreakdown(new HashMap<>());
            return s;
        }

        double total = latest.getTotalHours() != null ? latest.getTotalHours().doubleValue() : 0.0;
        double regular = latest.getRegularHours() != null ? latest.getRegularHours().doubleValue() : 0.0;
        double extra = latest.getOvertimeHours() != null ? latest.getOvertimeHours().doubleValue() : 0.0;
        double festivo = latest.getFestivoHours() != null ? latest.getFestivoHours().doubleValue() : 0.0;  // NUEVO

        s.setTotalHours(total);
        s.setAssignedHours(regular);
        s.setOvertimeHours(extra);
        s.setOvertimeType(latest.getOvertimeType() != null ? latest.getOvertimeType() : "Normal");
        s.setFestivoHours(festivo);                                                                        // NUEVO
        s.setFestivoType(latest.getFestivoType());                                                         // NUEVO

        // Incluir el desglose completo si está disponible
        if (latest.getOvertimeBreakdown() != null) {
            s.setOvertimeBreakdown(latest.getOvertimeBreakdown());
        } else {
            s.setOvertimeBreakdown(new HashMap<>());
        }

        return s;
    }
    // =================== VALIDAR SIN GUARDAR ===================

    public ValidationResult validateAssignmentOnly(AssignmentRequest request) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        try {
            validateRequest(request);

            List<ScheduleConflict> conflicts = detectScheduleConflicts(request.getAssignments());
            if (!conflicts.isEmpty()) {
                errors.add("Se detectaron conflictos de horarios");
            }

            List<HolidayWarning> holidayWarnings = detectHolidayWarnings(request.getAssignments());
            result.setHolidayWarnings(holidayWarnings);

            result.setValid(errors.isEmpty());
            result.setErrors(errors);

        } catch (Exception e) {
            errors.add(e.getMessage());
            result.setValid(false);
            result.setErrors(errors);
        }

        return result;
    }

    // =================== GENERACIÓN DE DÍAS/BLOQUES ===================

    private EmployeeSchedule createScheduleFromAssignment(ScheduleAssignment assignment) {
        EmployeeSchedule schedule = new EmployeeSchedule();
        schedule.setEmployeeId(assignment.getEmployeeId());

        Shifts shift = shiftsRepository.findById(assignment.getShiftId())
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + assignment.getShiftId()));
        schedule.setShift(shift);

        schedule.setStartDate(java.sql.Date.valueOf(assignment.getStartDate()));
        schedule.setEndDate(assignment.getEndDate() != null ? java.sql.Date.valueOf(assignment.getEndDate()) : null);
        schedule.setCreatedAt(new Date());
        return schedule;
    }

    private EmployeeSchedule createScheduleFromConfirmedAssignment(ConfirmedAssignment assignment) {
        EmployeeSchedule schedule = new EmployeeSchedule();
        schedule.setEmployeeId(assignment.getEmployeeId());

        Shifts shift = shiftsRepository.findById(assignment.getShiftId())
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + assignment.getShiftId()));
        schedule.setShift(shift);

        schedule.setStartDate(java.sql.Date.valueOf(assignment.getStartDate()));
        schedule.setEndDate(assignment.getEndDate() != null ? java.sql.Date.valueOf(assignment.getEndDate()) : null);
        schedule.setCreatedAt(new Date());
        return schedule;
    }

    private void generateScheduleDays(EmployeeSchedule schedule) {
        generateScheduleDaysWithHolidayDecisions(schedule, Collections.emptyList());
    }

    private void generateScheduleDaysWithHolidayDecisions(EmployeeSchedule schedule,
                                                          List<HolidayDecision> holidayDecisions) {

        if (schedule.getDays() == null) {
            schedule.setDays(new ArrayList<>());
        } else {
            schedule.getDays().clear();
        }

        LocalDate startDate = (schedule.getStartDate() != null)
                ? toLocalDate((java.sql.Date) schedule.getStartDate())
                : null;
        LocalDate endDate = (schedule.getEndDate() != null)
                ? toLocalDate((java.sql.Date) schedule.getEndDate())
                : startDate;

        if (startDate == null) throw new IllegalStateException("StartDate es requerido");
        if (endDate == null) endDate = startDate;

        List<ShiftDetail> details = (schedule.getShift() != null && schedule.getShift().getShiftDetails() != null)
                ? schedule.getShift().getShiftDetails()
                : Collections.emptyList();

        Map<LocalDate, HolidayDecision> decisionMap = (holidayDecisions != null ? holidayDecisions : Collections.<HolidayDecision>emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(h -> h.getHolidayDate() != null)
                .collect(Collectors.toMap(HolidayDecision::getHolidayDate, Function.identity(), (a, b) -> a));

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {

            EmployeeScheduleDay day = new EmployeeScheduleDay();
            day.setEmployeeSchedule(schedule);
            day.setDate(java.sql.Date.valueOf(d));
            day.setDayOfWeek(d.getDayOfWeek().getValue());
            day.setCreatedAt(new Date());
            day.setTimeBlocks(new ArrayList<>());

            HolidayDecision dec = decisionMap.get(d);

            for (ShiftDetail sd : details) {
                if (sd.getDayOfWeek() == null || !Objects.equals(sd.getDayOfWeek(), d.getDayOfWeek().getValue())) continue;
                if (sd.getStartTime() == null || sd.getEndTime() == null) continue;

                // Normalizar HH:mm:ss si viene HH:mm
                String sStr = sd.getStartTime().contains(":") && sd.getStartTime().split(":").length == 2
                        ? sd.getStartTime() + ":00" : sd.getStartTime();
                String eStr = sd.getEndTime().contains(":") && sd.getEndTime().split(":").length == 2
                        ? sd.getEndTime() + ":00" : sd.getEndTime();

                EmployeeScheduleTimeBlock tb = new EmployeeScheduleTimeBlock();
                tb.setEmployeeScheduleDay(day);
                tb.setStartTime(Time.valueOf(sStr));
                tb.setEndTime(Time.valueOf(eStr));
                tb.setCreatedAt(new Date());
                // si quieres guardar razón de exención en un campo, ponlo aquí usando dec.getExemptionReason()

                day.getTimeBlocks().add(tb);
            }

            schedule.getDays().add(day);
        }
    }

    // =================== VALIDACIONES / CONFLICTOS / FESTIVOS ===================

    private void validateRequest(AssignmentRequest request) {
        if (request == null || request.getAssignments() == null || request.getAssignments().isEmpty()) {
            throw new ValidationException("Debe proporcionar al menos una asignación", Arrays.asList("Sin asignaciones"));
        }

        List<String> errors = new ArrayList<>();
        for (ScheduleAssignment a : request.getAssignments()) {
            if (a.getEmployeeId() == null) errors.add("Employee ID requerido");
            if (a.getShiftId() == null) errors.add("Shift ID requerido");
            if (a.getStartDate() == null) errors.add("Fecha de inicio requerida");
            if (a.getEndDate() != null && a.getStartDate() != null && a.getEndDate().isBefore(a.getStartDate())) {
                errors.add("Fecha de fin debe ser posterior a fecha de inicio");
            }
        }
        if (!errors.isEmpty()) throw new ValidationException("Errores de validación", errors);
    }

    private List<ScheduleConflict> detectScheduleConflicts(List<ScheduleAssignment> assignments) {
        List<ScheduleConflict> conflicts = new ArrayList<>();

        Map<Long, List<ScheduleAssignment>> byEmp = assignments.stream()
                .collect(Collectors.groupingBy(ScheduleAssignment::getEmployeeId));

        for (Map.Entry<Long, List<ScheduleAssignment>> entry : byEmp.entrySet()) {
            Long employeeId = entry.getKey();
            List<ScheduleAssignment> empAssignments = entry.getValue();

            List<EmployeeSchedule> existing = employeeScheduleRepository.findByEmployeeId(employeeId);

            // 1) VALIDAR: nuevos vs existentes
            for (ScheduleAssignment newAssignment : empAssignments) {
                for (EmployeeSchedule existingSchedule : existing) {
                    ScheduleConflict conflict = checkForConflict(newAssignment, existingSchedule);
                    if (conflict != null) {

                    }
                }
            }

            // 2) VALIDAR: nuevos entre sí (si asignan múltiples turnos al mismo empleado)
            for (int i = 0; i < empAssignments.size(); i++) {
                for (int j = i + 1; j < empAssignments.size(); j++) {
                    ScheduleConflict conflict = checkForConflictBetweenAssignments(
                            empAssignments.get(i), empAssignments.get(j));
                    if (conflict != null) {

                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
        } else {
        }

        return conflicts;
    }
    private ScheduleConflict checkForConflict(ScheduleAssignment assignment, EmployeeSchedule existing) {


        LocalDate newStart = assignment.getStartDate();
        LocalDate newEnd = (assignment.getEndDate() != null) ? assignment.getEndDate() : newStart;

        LocalDate existingStart = toLocalDate(existing.getStartDate());
        LocalDate existingEnd = (existing.getEndDate() != null) ? toLocalDate(existing.getEndDate()) : existingStart;



        // Verificar si las fechas se solapan
        if (!datesOverlap(newStart, newEnd, existingStart, existingEnd)) {
            return null;
        }


        // Obtener los turnos
        Shifts newShift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
        Shifts existingShift = existing.getShift();

        if (newShift == null || existingShift == null) {
            return createConflict(assignment, newStart, "No se pudo verificar turnos");
        }

        // Verificar cada día en el período de solapamiento
        LocalDate overlapStart = Collections.max(Arrays.asList(newStart, existingStart));
        LocalDate overlapEnd = Collections.min(Arrays.asList(newEnd, existingEnd));


        for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
            int dayOfWeek = date.getDayOfWeek().getValue(); // 1=lunes, 7=domingo
            String dayName = getDayName(dayOfWeek);


            // Verificar si ambos turnos tienen actividad este día
            boolean newShiftHasThisDay = hasShiftActivityOnDay(newShift, dayOfWeek);
            boolean existingShiftHasThisDay = hasShiftActivityOnDay(existingShift, dayOfWeek);



            if (newShiftHasThisDay && existingShiftHasThisDay) {
                // Ambos turnos están activos este día - verificar solapamiento de horarios
                if (hasTimeOverlap(newShift, existingShift, dayOfWeek)) {
                    return createConflict(assignment, date,
                            "Conflicto de horarios el " + dayName + " " + date +
                                    ": El empleado ya tiene un turno asignado en horarios que se solapan");
                } else {
                }
            } else {
            }
        }

        return null;
    }




    private boolean hasShiftActivityOnDay(Shifts shift, int dayOfWeek) {
        if (shift == null || shift.getShiftDetails() == null) {
            return false;
        }

        return shift.getShiftDetails().stream()
                .anyMatch(detail -> detail.getDayOfWeek() != null &&
                        detail.getDayOfWeek().equals(dayOfWeek) &&
                        detail.getStartTime() != null &&
                        detail.getEndTime() != null);
    }



    private boolean hasTimeOverlap(Shifts shift1, Shifts shift2, int dayOfWeek) {
        List<ShiftDetail> details1 = shift1.getShiftDetails().stream()
                .filter(d -> d.getDayOfWeek() != null && d.getDayOfWeek().equals(dayOfWeek))
                .collect(Collectors.toList());

        List<ShiftDetail> details2 = shift2.getShiftDetails().stream()
                .filter(d -> d.getDayOfWeek() != null && d.getDayOfWeek().equals(dayOfWeek))
                .collect(Collectors.toList());

        if (details1.isEmpty() || details2.isEmpty()) {
            return false;
        }


        // Verificar cada combinación de horarios
        for (ShiftDetail d1 : details1) {
            for (ShiftDetail d2 : details2) {

                if (timePeriodsOverlap(d1.getStartTime(), d1.getEndTime(),
                        d2.getStartTime(), d2.getEndTime())) {
                    return true;
                } else {
                }
            }
        }

        return false;
    }



    private String getDayName(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1: return "Lunes";
            case 2: return "Martes";
            case 3: return "Miércoles";
            case 4: return "Jueves";
            case 5: return "Viernes";
            case 6: return "Sábado";
            case 7: return "Domingo";
            default: return "Día " + dayOfWeek;
        }
    }




    private boolean timePeriodsOverlap(String start1, String end1, String start2, String end2) {
        try {
            if (start1 == null || end1 == null || start2 == null || end2 == null) {
                return false;
            }

            // Normalizar formato a HH:mm:ss si es necesario
            String s1 = normalizeTimeFormat(start1);
            String e1 = normalizeTimeFormat(end1);
            String s2 = normalizeTimeFormat(start2);
            String e2 = normalizeTimeFormat(end2);

            Time startTime1 = Time.valueOf(s1);
            Time endTime1 = Time.valueOf(e1);
            Time startTime2 = Time.valueOf(s2);
            Time endTime2 = Time.valueOf(e2);

            // Los períodos se solapan si: start1 < end2 && start2 < end1
            boolean overlap = startTime1.before(endTime2) && startTime2.before(endTime1);

            System.out.println("           📊 " + s1 + "-" + e1 + " vs " + s2 + "-" + e2 + " = " +
                    (overlap ? "SOLAPAN" : "NO SOLAPAN"));

            return overlap;

        } catch (Exception e) {
            System.err.println("❌ Error comparing times: " + e.getMessage());
            return false;
        }
    }


    private String normalizeTimeFormat(String time) {
        if (time == null) return "00:00:00";

        // Si ya tiene formato HH:mm:ss, devolverlo como está
        if (time.split(":").length == 3) {
            return time;
        }

        // Si tiene formato HH:mm, agregar :00
        if (time.split(":").length == 2) {
            return time + ":00";
        }

        // Si solo tiene HH, agregar :00:00
        return time + ":00:00";
    }


    private ScheduleConflict createConflict(ScheduleAssignment assignment, LocalDate conflictDate, String message) {
        ScheduleConflict conflict = new ScheduleConflict();
        conflict.setEmployeeId(assignment.getEmployeeId());
        conflict.setConflictDate(conflictDate);
        conflict.setMessage(message);
        return conflict;
    }



    private ScheduleConflict checkForConflictBetweenAssignments(ScheduleAssignment a1, ScheduleAssignment a2) {
        LocalDate s1 = a1.getStartDate();
        LocalDate e1 = (a1.getEndDate() != null) ? a1.getEndDate() : s1;
        LocalDate s2 = a2.getStartDate();
        LocalDate e2 = (a2.getEndDate() != null) ? a2.getEndDate() : s2;

        if (!datesOverlap(s1, e1, s2, e2)) return null;

        Shifts sh1 = shiftsRepository.findById(a1.getShiftId()).orElse(null);
        Shifts sh2 = shiftsRepository.findById(a2.getShiftId()).orElse(null);
        if (sh1 == null || sh2 == null) return null;

        LocalDate overlapStart = Collections.max(Arrays.asList(s1, s2));
        LocalDate overlapEnd = Collections.min(Arrays.asList(e1, e2));

        // VALIDACIÓN CORREGIDA: Verificar conflictos día por día basándose en shift_details
        for (LocalDate d = overlapStart; !d.isAfter(overlapEnd); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue();

            // Verificar si AMBOS turnos tienen actividad en este día de la semana
            boolean sh1HasThisDay = sh1.getShiftDetails() != null &&
                    sh1.getShiftDetails().stream().anyMatch(dd -> dd.getDayOfWeek() != null && dd.getDayOfWeek() == dow);
            boolean sh2HasThisDay = sh2.getShiftDetails() != null &&
                    sh2.getShiftDetails().stream().anyMatch(dd -> dd.getDayOfWeek() != null && dd.getDayOfWeek() == dow);

            if (sh1HasThisDay && sh2HasThisDay) {
                ScheduleConflict c = new ScheduleConflict();
                c.setEmployeeId(a1.getEmployeeId());
                c.setConflictDate(d);
                c.setMessage("Conflicto el día " + d + " - múltiples turnos nuevos asignados para la misma fecha");
                return c;
            }
        }
        return null;
    }

    private List<HolidayWarning> detectHolidayWarnings(List<ScheduleAssignment> assignments) {
        List<HolidayWarning> warnings = new ArrayList<>();
        for (ScheduleAssignment a : assignments) {
            LocalDate start = a.getStartDate();
            LocalDate end = (a.getEndDate() != null) ? a.getEndDate() : start;

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (holidayService.isHoliday(d)) {
                    HolidayWarning w = new HolidayWarning();
                    w.setEmployeeId(a.getEmployeeId());
                    w.setHolidayDate(d);
                    w.setHolidayName(holidayService.getHolidayName(d));
                    w.setRequiresConfirmation(true);
                    warnings.add(w);
                }
            }
        }
        return warnings;
    }

    private boolean datesOverlap(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }

    // =================== MÉTODOS EXISTENTES (compatibilidad) ===================

    public List<EmployeeScheduleDTO> getAllEmployeeSchedules() {
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findAll();
        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public EmployeeScheduleDTO getEmployeeScheduleById(Long id) {
        EmployeeSchedule schedule = employeeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));
        return convertToDTO(schedule);
    }

    public List<EmployeeScheduleDTO> getSchedulesByEmployeeId(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("Employee ID debe ser un número válido.");
        }
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);
        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByDependencyId(Long dependencyId,
                                                                LocalDate startDate,
                                                                LocalDate endDate,
                                                                java.time.LocalTime startTime,
                                                                Long shiftId) {
        // Implementación simplificada (ajústala si tienes consulta real por dependencia)
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(dependencyId);
        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public void deleteEmployeeSchedule(Long id) {
        if (!employeeScheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("EmployeeSchedule not found with id: " + id);
        }
        employeeScheduleRepository.deleteById(id);
    }

    private EmployeeScheduleDTO convertToDTO(EmployeeSchedule schedule) {
        EmployeeScheduleDTO dto = new EmployeeScheduleDTO();
        dto.setId(schedule.getId());
        dto.setNumberId(Long.valueOf(schedule.getEmployeeId().toString()));
        dto.setStartDate(dateFormat.format(schedule.getStartDate()));
        dto.setEndDate(schedule.getEndDate() != null ? dateFormat.format(schedule.getEndDate()) : null);

        try {
            EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
            if (response != null && response.getEmployee() != null) {
                EmployeeResponse.Employee emp = response.getEmployee();
                dto.setFirstName(emp.getFirstName() != null ? emp.getFirstName() : "");
                dto.setSecondName(emp.getSecondName() != null ? emp.getSecondName() : "");
                dto.setSurName(emp.getSurName() != null ? emp.getSurName() : "");
                dto.setSecondSurname(emp.getSecondSurname() != null ? emp.getSecondSurname() : "");
            }
        } catch (Exception ignore) { }
        return dto;
    }

    private EmployeeResponse getEmployeeData(Long employeeId) {
        if (employeeId == null) return null;
        try {
            String url = "http://192.168.23.3:40020/api/employees/bynumberid/" + employeeId;
            ResponseEntity<EmployeeResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(null), EmployeeResponse.class
            );
            if (response.getStatusCode().is2xxSuccessful()) return response.getBody();
        } catch (Exception ignore) { }
        return null;
    }
}
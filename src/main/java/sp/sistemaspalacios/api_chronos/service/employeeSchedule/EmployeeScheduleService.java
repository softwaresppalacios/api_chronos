package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import sp.sistemaspalacios.api_chronos.dto.*;
import sp.sistemaspalacios.api_chronos.dto.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.math.BigDecimal;
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

    private final EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository;

    private final HolidayService holidayService;

    // Servicios inyectados
    private final ScheduleAssignmentGroupService groupService;
    private final HolidayExemptionService holidayExemptionService;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public EmployeeScheduleService(
            EmployeeScheduleRepository employeeScheduleRepository,
            ShiftsRepository shiftsRepository,
            RestTemplate restTemplate,
            EmployeeScheduleDayRepository employeeScheduleDayRepository,
            GeneralConfigurationService configService,
            HolidayService holidayService,
            ScheduleAssignmentGroupService groupService,
            HolidayExemptionService holidayExemptionService,  EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository
    ) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.restTemplate = restTemplate;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.configService = configService;
        this.holidayService = holidayService;
        this.groupService = groupService;
        this.employeeScheduleTimeBlockRepository = employeeScheduleTimeBlockRepository;

        this.holidayExemptionService = holidayExemptionService;
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

        // 2) Festivos
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

            System.out.println("✅ Schedule creado: ID=" + saved.getId() +
                    ", EmployeeId=" + saved.getEmployeeId() +
                    ", ShiftId=" + saved.getShift().getId());
        }

        // Forzar flush antes de agrupar
        employeeScheduleRepository.flush();
        System.out.println("🔄 Flush ejecutado - " + created.size() + " schedules confirmados en BD");

        // 4) AGRUPAR por empleado y CREAR/ACTUALIZAR grupo
        Map<Long, List<Long>> idsPorEmpleado = created.stream()
                .collect(Collectors.groupingBy(EmployeeSchedule::getEmployeeId,
                        Collectors.mapping(EmployeeSchedule::getId, Collectors.toList())));

        System.out.println("📊 Agrupación por empleado:");
        idsPorEmpleado.forEach((empId, scheduleIds) -> {
            System.out.println("  Employee " + empId + ": schedules " + scheduleIds);
        });

        for (Map.Entry<Long, List<Long>> e : idsPorEmpleado.entrySet()) {
            Long employeeId = e.getKey();
            List<Long> scheduleIds = e.getValue();

            List<EmployeeSchedule> verification = employeeScheduleRepository.findAllById(scheduleIds);
            System.out.println("🔍 Verificación pre-grupo - Employee " + employeeId +
                    ": " + verification.size() + " schedules encontrados de " + scheduleIds.size());

            ScheduleAssignmentGroupDTO group = groupService.processScheduleAssignment(employeeId, scheduleIds);
            System.out.println("✅ Grupo procesado para employee " + employeeId +
                    ": group ID " + group.getId());
        }

        // 5) Resumen final
        List<EmployeeHoursSummary> summaries =
                idsPorEmpleado.keySet().stream()
                        .map(empId -> {
                            try {
                                return calculateEmployeeHoursSummary(empId);
                            } catch (Exception ex) {
                                System.err.println("❌ Error calculando resumen para employee " + empId + ": " + ex.getMessage());
                                var empty = new EmployeeHoursSummary();
                                empty.setEmployeeId(empId);
                                empty.setTotalHours(0.0);
                                empty.setAssignedHours(0.0);
                                empty.setOvertimeHours(0.0);
                                empty.setOvertimeType("Normal");
                                empty.setFestivoHours(0.0);
                                empty.setFestivoType(null);
                                empty.setOvertimeBreakdown(new HashMap<>());
                                return empty;
                            }
                        })
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
        List<EmployeeHoursSummary> summaries =
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

    // =================== RESUMEN DE HORAS ===================

    public EmployeeHoursSummary calculateEmployeeHoursSummary(Long employeeId) {

        // ⬇️ Filtrar aquí SOLO los grupos ACTIVE
        List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId)
                .stream()
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .collect(Collectors.toList());

        var s = new EmployeeHoursSummary();
        s.setEmployeeId(employeeId);

        if (groups.isEmpty()) {
            s.setTotalHours(0.0);
            s.setAssignedHours(0.0);
            s.setOvertimeHours(0.0);
            s.setOvertimeType("Normal");
            s.setFestivoHours(0.0);
            s.setFestivoType(null);
            s.setOvertimeBreakdown(new HashMap<>());
            return s;
        }

        BigDecimal regular = BigDecimal.ZERO;
        BigDecimal extra   = BigDecimal.ZERO;
        BigDecimal festivo = BigDecimal.ZERO;

        Map<String, BigDecimal> breakdownSum = new HashMap<>();

        for (ScheduleAssignmentGroupDTO g : groups) {
            if (g.getRegularHours() != null) regular = regular.add(g.getRegularHours());
            if (g.getOvertimeHours() != null) extra   = extra.add(g.getOvertimeHours());
            if (g.getFestivoHours() != null)  festivo = festivo.add(g.getFestivoHours());

            if (g.getOvertimeBreakdown() != null) {
                for (Map.Entry<String, Object> e : g.getOvertimeBreakdown().entrySet()) {
                    if (e.getValue() instanceof Number n) {
                        breakdownSum.merge(e.getKey(), BigDecimal.valueOf(n.doubleValue()), BigDecimal::add);
                    }
                }
            }
        }

        BigDecimal total = regular.add(extra);
        BigDecimal assigned = regular.add(festivo);

        String predominantExtra = null;
        BigDecimal maxExtra = BigDecimal.ZERO;
        String predominantFestivo = null;
        BigDecimal maxFestivo = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> e : breakdownSum.entrySet()) {
            String code = e.getKey();
            BigDecimal hours = e.getValue();
            if (hours == null || hours.signum() <= 0) continue;

            if (code.startsWith("EXTRA_") || code.contains("DOMINICAL")) {
                if (hours.compareTo(maxExtra) > 0) { maxExtra = hours; predominantExtra = code; }
            } else if (code.startsWith("FESTIVO_")) {
                if (hours.compareTo(maxFestivo) > 0) { maxFestivo = hours; predominantFestivo = code; }
            }
        }

        s.setTotalHours(total.doubleValue());
        s.setAssignedHours(assigned.doubleValue());
        s.setOvertimeHours(extra.doubleValue());
        s.setFestivoHours(festivo.doubleValue());
        s.setOvertimeType(predominantExtra != null ? predominantExtra : "Normal");
        s.setFestivoType(predominantFestivo);

        Map<String, Object> bd = new HashMap<>();
        breakdownSum.forEach((k, v) -> bd.put(k, v.doubleValue()));
        s.setOvertimeBreakdown(bd);
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
                ? ((java.sql.Date) schedule.getStartDate()).toLocalDate()
                : null;
        LocalDate endDate = (schedule.getEndDate() != null)
                ? ((java.sql.Date) schedule.getEndDate()).toLocalDate()
                : startDate;

        if (startDate == null) throw new IllegalStateException("StartDate es requerido");
        if (endDate == null) endDate = startDate;

        List<ShiftDetail> details = (schedule.getShift() != null && schedule.getShift().getShiftDetails() != null)
                ? schedule.getShift().getShiftDetails()
                : Collections.emptyList();

        // Mapear decisiones por fecha
        Map<LocalDate, HolidayDecision> decisionMap = (holidayDecisions != null ? holidayDecisions : Collections.<HolidayDecision>emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(h -> h.getHolidayDate() != null)
                .collect(Collectors.toMap(HolidayDecision::getHolidayDate, h -> h, (a,b) -> a));

        // Omitir día SOLO cuando hay motivo de exención (día NO trabajado + razón)
        Set<LocalDate> exemptDates = decisionMap.entrySet().stream()
                .filter(e -> {
                    HolidayDecision v = e.getValue();
                    boolean hasReason = v.getExemptionReason() != null && !v.getExemptionReason().isBlank();
                    boolean noTrabaja = (v.isApplyHolidayCharge() == false);
                    return hasReason && noTrabaja;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {

            // Saltar si exento explícito
            if (exemptDates.contains(d)) {
                continue;
            }

            HolidayDecision decision = decisionMap.get(d);

            EmployeeScheduleDay day = new EmployeeScheduleDay();
            day.setEmployeeSchedule(schedule);
            day.setDate(java.sql.Date.valueOf(d));
            day.setDayOfWeek(d.getDayOfWeek().getValue());
            day.setCreatedAt(new Date());
            day.setTimeBlocks(new ArrayList<>());

            // para cada detalle del turno que coincida con el DOW, creamos bloque
            for (ShiftDetail sd : details) {
                if (sd.getDayOfWeek() == null || !Objects.equals(sd.getDayOfWeek(), d.getDayOfWeek().getValue())) continue;
                if (sd.getStartTime() == null || sd.getEndTime() == null) continue;

                // Valores por defecto (del turno)
                String finalStartTime = sd.getStartTime();
                String finalEndTime   = sd.getEndTime();
                String finalBreakStartTime = sd.getBreakStartTime();
                String finalBreakEndTime   = sd.getBreakEndTime();

                // Si hay decisión y segmentos desde el front, buscar el segmento que coincide por "segmentName"
                if (decision != null && decision.getShiftSegments() != null) {
                    for (Object segmentObj : decision.getShiftSegments()) {
                        if (!(segmentObj instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> seg = (Map<String, Object>) segmentObj;

                        String segName = stringOf(seg.get("segmentName"));
                        String expected = determineSegmentName(sd.getStartTime()); // "Mañana"/"Tarde"/"Noche"

                        if (equalsIgnoreCaseNoAccents(segName, expected)) {
                            String s  = stringOf(seg.get("startTime"));       // => "HH:mm:ss"
                            String e  = stringOf(seg.get("endTime"));
                            String bs = stringOf(seg.get("breakStartTime"));
                            String be = stringOf(seg.get("breakEndTime"));

                            if (!isBlank(s))  finalStartTime = s;
                            if (!isBlank(e))  finalEndTime   = e;
                            if (!isBlank(bs)) finalBreakStartTime = bs;
                            if (!isBlank(be)) finalBreakEndTime   = be;
                            break;
                        }
                    }
                }

                // Normalizar a HH:mm:ss
                String sStr = normalizeTimeForDatabase(finalStartTime);
                String eStr = normalizeTimeForDatabase(finalEndTime);

                EmployeeScheduleTimeBlock tb = new EmployeeScheduleTimeBlock();
                tb.setEmployeeScheduleDay(day);
                tb.setStartTime(Time.valueOf(sStr));
                tb.setEndTime(Time.valueOf(eStr));
                tb.setCreatedAt(new Date());

                day.getTimeBlocks().add(tb);
            }

            schedule.getDays().add(day);

            // Guardar exenciones/decisiones para trazabilidad:
            if (decision != null) {
                try {
                    if (decision.getExemptionReason() != null && !decision.getExemptionReason().isBlank()) {
                        // Exención real (no trabajar) ya la omitimos arriba
                        holidayExemptionService.saveExemption(
                                schedule.getEmployeeId(),
                                d,
                                holidayService.getHolidayName(d),
                                decision.getExemptionReason(),
                                null
                        );
                    } else if (decision.isApplyHolidayCharge() == false) {
                        // No aplicar recargo (trabajo REGULAR sin recargo) → registro como NO_APLICAR_RECARGO
                        holidayExemptionService.saveExemption(
                                schedule.getEmployeeId(),
                                d,
                                holidayService.getHolidayName(d),
                                "NO_APLICAR_RECARGO",
                                null
                        );
                    }
                } catch (Exception ignore) {
                    // No detengas el flujo por logs
                }
            }
        }
    }


    // NUEVOS MÉTODOS HELPER PARA MANEJAR SEGMENTOS

    private boolean segmentMatchesShiftDetail(Object segment, ShiftDetail sd) {
        try {
            // Usar reflexión para obtener el segmentName del objeto
            java.lang.reflect.Method getSegmentName = segment.getClass().getMethod("getSegmentName");
            String segmentName = (String) getSegmentName.invoke(segment);

            if (segmentName == null) return false;

            // Normalizar nombres
            String normalizedSegment = segmentName.toLowerCase().trim();

            // Determinar el nombre esperado basado en la hora de inicio del ShiftDetail
            String expectedName = determineSegmentName(sd.getStartTime()).toLowerCase().trim();

            return normalizedSegment.equals(expectedName);
        } catch (Exception e) {
            return false;
        }
    }

    private String getSegmentProperty(Object segment, String methodName) {
        try {
            java.lang.reflect.Method method = segment.getClass().getMethod(methodName);
            Object result = method.invoke(segment);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeTimeForDatabase(String time) {
        if (time == null) return "00:00:00";
        if (time.split(":").length == 3) return time;        // HH:mm:ss
        if (time.split(":").length == 2) return time + ":00"; // HH:mm
        return time + ":00:00";                               // HH
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

        System.out.println("🔍 DEBUGGING: Iniciando detección de conflictos para " + assignments.size() + " asignaciones");

        Map<Long, List<ScheduleAssignment>> byEmp = assignments.stream()
                .collect(Collectors.groupingBy(ScheduleAssignment::getEmployeeId));

        for (Map.Entry<Long, List<ScheduleAssignment>> entry : byEmp.entrySet()) {
            Long employeeId = entry.getKey();
            List<ScheduleAssignment> empAssignments = entry.getValue();

            System.out.println("🔍 Verificando empleado " + employeeId + " con " + empAssignments.size() + " asignaciones");

            // Mostrar las nuevas asignaciones
            for (int i = 0; i < empAssignments.size(); i++) {
                ScheduleAssignment assign = empAssignments.get(i);
                System.out.println("  Nueva asignación " + (i+1) + ": " +
                        assign.getStartDate() + " - " + assign.getEndDate() +
                        " (Shift: " + assign.getShiftId() + ")");
            }

            List<EmployeeSchedule> existing = employeeScheduleRepository.findByEmployeeId(employeeId);
            System.out.println("🔍 Empleado " + employeeId + " tiene " + existing.size() + " horarios existentes");

            // Mostrar horarios existentes
            for (int i = 0; i < existing.size(); i++) {
                EmployeeSchedule es = existing.get(i);
                System.out.println("  Existente " + (i+1) + ": " +
                        toLocalDate(es.getStartDate()) + " - " +
                        toLocalDate(es.getEndDate()) +
                        " (Shift: " + (es.getShift() != null ? es.getShift().getId() : "null") + ")");
            }

            // 1) nuevos vs existentes
            for (ScheduleAssignment newAssignment : empAssignments) {
                System.out.println("🔍 Verificando nueva asignación: " +
                        newAssignment.getStartDate() + " - " + newAssignment.getEndDate() +
                        " (Shift: " + newAssignment.getShiftId() + ")");

                for (EmployeeSchedule existingSchedule : existing) {
                    System.out.println("  🔍 Comparando con horario existente: " +
                            toLocalDate(existingSchedule.getStartDate()) + " - " +
                            toLocalDate(existingSchedule.getEndDate()) +
                            " (Shift: " + (existingSchedule.getShift() != null ? existingSchedule.getShift().getId() : "null") + ")");

                    ScheduleConflict conflict = checkForConflictWithDetailedLogging(newAssignment, existingSchedule);
                    if (conflict != null) {
                        System.out.println("❌ CONFLICTO DETECTADO: " + conflict.getMessage());
                        conflicts.add(conflict);
                    } else {
                        System.out.println("  ✅ Sin conflicto con este horario existente");
                    }
                }
            }

            // 2) nuevos entre sí
            for (int i = 0; i < empAssignments.size(); i++) {
                for (int j = i + 1; j < empAssignments.size(); j++) {
                    System.out.println("🔍 Verificando conflicto entre nuevas asignaciones " + (i+1) + " y " + (j+1));
                    ScheduleConflict conflict = checkForConflictBetweenAssignments(
                            empAssignments.get(i), empAssignments.get(j));
                    if (conflict != null) {
                        System.out.println("❌ CONFLICTO ENTRE NUEVAS ASIGNACIONES: " + conflict.getMessage());
                        conflicts.add(conflict);
                    } else {
                        System.out.println("  ✅ Sin conflicto entre nuevas asignaciones");
                    }
                }
            }
        }

        System.out.println("🔍 TOTAL CONFLICTOS ENCONTRADOS: " + conflicts.size());
        return conflicts;
    }


    private ScheduleConflict checkForConflictWithDetailedLogging(ScheduleAssignment assignment, EmployeeSchedule existing) {

        LocalDate newStart = assignment.getStartDate();
        LocalDate newEnd = (assignment.getEndDate() != null) ? assignment.getEndDate() : newStart;

        LocalDate existingStart = toLocalDate(existing.getStartDate());
        LocalDate existingEnd = (existing.getEndDate() != null) ? toLocalDate(existing.getEndDate()) : existingStart;

        System.out.println("    Verificando solapamiento:");
        System.out.println("      Nuevo: " + newStart + " - " + newEnd);
        System.out.println("      Existente: " + existingStart + " - " + existingEnd);

        if (!datesOverlap(newStart, newEnd, existingStart, existingEnd)) {
            System.out.println("      Sin solapamiento de fechas");
            return null;
        }

        System.out.println("      Fechas se solapan - verificando si es el mismo turno...");

        Shifts newShift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
        Shifts existingShift = existing.getShift();

        if (newShift == null || existingShift == null) {
            System.out.println("      No se pudieron cargar los turnos");
            return createConflict(assignment, newStart, "No se pudo verificar turnos");
        }

        System.out.println("      Turno nuevo: " + newShift.getName() + " (ID: " + newShift.getId() + ")");
        System.out.println("      Turno existente: " + existingShift.getName() + " (ID: " + existingShift.getId() + ")");

        // SIMPLIFICADO: Si es exactamente el mismo turno y fechas = permitir (reasignación)
        boolean isExactDuplicate = Objects.equals(assignment.getShiftId(), existing.getShift().getId()) &&
                newStart.equals(existingStart) &&
                newEnd.equals(existingEnd);

        if (isExactDuplicate) {
            System.out.println("      Es exactamente el mismo turno y fechas - permitiendo reasignación");
            return null;
        }

        // EN CUALQUIER OTRO CASO DE SOLAPAMIENTO = CONFLICTO
        System.out.println("      CONFLICTO: Fechas solapadas con turnos diferentes");
        return createConflict(assignment, newStart,
                "No se puede asignar porque el empleado ya tiene otro turno en fechas que se solapan " +
                        "(Existente: " + existingShift.getName() + " del " + existingStart + " al " + existingEnd + ")");
    }

    private ScheduleConflict checkForConflict(ScheduleAssignment assignment, EmployeeSchedule existing) {

        LocalDate newStart = assignment.getStartDate();
        LocalDate newEnd = (assignment.getEndDate() != null) ? assignment.getEndDate() : newStart;

        LocalDate existingStart = toLocalDate(existing.getStartDate());
        LocalDate existingEnd = (existing.getEndDate() != null) ? toLocalDate(existing.getEndDate()) : existingStart;

        if (!datesOverlap(newStart, newEnd, existingStart, existingEnd)) {
            return null;
        }

        Shifts newShift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
        Shifts existingShift = existing.getShift();

        if (newShift == null || existingShift == null) {
            return createConflict(assignment, newStart, "No se pudo verificar turnos");
        }

        LocalDate overlapStart = Collections.max(Arrays.asList(newStart, existingStart));
        LocalDate overlapEnd = Collections.min(Arrays.asList(newEnd, existingEnd));

        for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
            int dayOfWeek = date.getDayOfWeek().getValue();

            boolean newShiftHasThisDay = hasShiftActivityOnDay(newShift, dayOfWeek);
            boolean existingShiftHasThisDay = hasShiftActivityOnDay(existingShift, dayOfWeek);

            if (newShiftHasThisDay && existingShiftHasThisDay) {
                return createConflict(assignment, date,
                        "Conflicto de fechas el " + date +
                                ": El empleado ya tiene un turno asignado para esta fecha");
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

    private boolean timePeriodsOverlap(String start1, String end1, String start2, String end2) {
        try {
            if (start1 == null || end1 == null || start2 == null || end2 == null) return false;

            String s1 = start1.contains(":") && start1.split(":").length == 2 ? start1 + ":00" : start1;
            String e1 = end1.contains(":") && end1.split(":").length == 2 ? end1 + ":00" : end1;
            String s2 = start2.contains(":") && start2.split(":").length == 2 ? start2 + ":00" : start2;
            String e2 = end2.contains(":") && end2.split(":").length == 2 ? end2 + ":00" : end2;

            Time startTime1 = Time.valueOf(s1);
            Time endTime1 = Time.valueOf(e1);
            Time startTime2 = Time.valueOf(s2);
            Time endTime2 = Time.valueOf(e2);

            return startTime1.before(endTime2) && startTime2.before(endTime1);

        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeTimeFormat(String time) {
        if (time == null) return "00:00:00";
        if (time.split(":").length == 3) return time;
        if (time.split(":").length == 2) return time + ":00";
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

        for (LocalDate d = overlapStart; !d.isAfter(overlapEnd); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue();

            boolean sh1HasThisDay = sh1.getShiftDetails() != null &&
                    sh1.getShiftDetails().stream().anyMatch(dd ->
                            dd.getDayOfWeek() != null && dd.getDayOfWeek() == dow &&
                                    dd.getStartTime() != null && dd.getEndTime() != null);

            boolean sh2HasThisDay = sh2.getShiftDetails() != null &&
                    sh2.getShiftDetails().stream().anyMatch(dd ->
                            dd.getDayOfWeek() != null && dd.getDayOfWeek() == dow &&
                                    dd.getStartTime() != null && dd.getEndTime() != null);

            if (sh1HasThisDay && sh2HasThisDay) {
                boolean hasTimeConflict = sh1.getShiftDetails().stream()
                        .filter(d1 -> d1.getDayOfWeek() != null && d1.getDayOfWeek() == dow)
                        .anyMatch(d1 -> sh2.getShiftDetails().stream()
                                .filter(d2 -> d2.getDayOfWeek() != null && d2.getDayOfWeek() == dow)
                                .anyMatch(d2 -> timePeriodsOverlap(d1.getStartTime(), d1.getEndTime(),
                                        d2.getStartTime(), d2.getEndTime())));

                if (hasTimeConflict) {
                    ScheduleConflict c = new ScheduleConflict();
                    c.setEmployeeId(a1.getEmployeeId());
                    c.setConflictDate(d);
                    c.setMessage("Conflicto el día " + d + " - Los turnos se solapan en horario");
                    return c;
                }
            }
        }
        return null;
    }

    private List<HolidayWarning> detectHolidayWarnings(List<ScheduleAssignment> assignments) {
        List<HolidayWarning> warnings = new ArrayList<>();

        for (ScheduleAssignment assignment : assignments) {
            LocalDate start = assignment.getStartDate();
            LocalDate end = (assignment.getEndDate() != null) ? assignment.getEndDate() : start;

            Shifts shift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
            if (shift == null || shift.getShiftDetails() == null) continue;

            String employeeName = getEmployeeName(assignment.getEmployeeId());

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (holidayService.isHoliday(d)) {
                    HolidayWarning warning = new HolidayWarning();
                    warning.setEmployeeId(assignment.getEmployeeId());
                    warning.setEmployeeName(employeeName);
                    warning.setHolidayDate(d);
                    warning.setHolidayName(holidayService.getHolidayName(d));

                    List<ShiftSegmentDetail> segments = calculateShiftSegmentsForDay(shift, d);
                    warning.setShiftSegments(segments);

                    warning.setRequiresConfirmation(true);
                    warnings.add(warning);
                }
            }
        }
        return warnings;
    }

    // =================== MÉTODOS AUXILIARES PARA CALCULAR SEGMENTOS ===================

    // Método auxiliar para obtener nombre del empleado
    public String getEmployeeName(Long employeeId) {
        try {
            EmployeeResponse response = getEmployeeData(employeeId);
            if (response != null && response.getEmployee() != null) {
                EmployeeResponse.Employee emp = response.getEmployee();
                return String.join(" ",
                        Arrays.stream(new String[]{emp.getFirstName(), emp.getSecondName(), emp.getSurName(), emp.getSecondSurname()})
                                .filter(Objects::nonNull)
                                .filter(s -> !s.isEmpty())
                                .toArray(String[]::new)
                );
            }
        } catch (Exception ignore) {}
        return "Empleado " + employeeId;
    }

    // Método para calcular los segmentos del turno
    private List<ShiftSegmentDetail> calculateShiftSegmentsForDay(Shifts shift, LocalDate date) {
        List<ShiftSegmentDetail> segments = new ArrayList<>();
        int dayOfWeek = date.getDayOfWeek().getValue();

        List<ShiftDetail> dayDetails = shift.getShiftDetails().stream()
                .filter(detail -> detail.getDayOfWeek() != null && detail.getDayOfWeek().equals(dayOfWeek))
                .filter(detail -> detail.getStartTime() != null && detail.getEndTime() != null)
                .collect(Collectors.toList());

        for (ShiftDetail detail : dayDetails) {
            ShiftSegmentDetail segment = new ShiftSegmentDetail();

            segment.setSegmentName(determineSegmentName(detail.getStartTime()));
            segment.setStartTime(normalizeTimeForDatabase(detail.getStartTime()));
            segment.setEndTime(normalizeTimeForDatabase(detail.getEndTime()));

            if (detail.getBreakStartTime() != null) {
                segment.setBreakStartTime(normalizeTimeForDatabase(detail.getBreakStartTime()));
            }
            if (detail.getBreakEndTime() != null) {
                segment.setBreakEndTime(normalizeTimeForDatabase(detail.getBreakEndTime()));
            }
            segment.setBreakMinutes(detail.getBreakMinutes());

            double workingHours = calculateHoursBetween(segment.getStartTime(), segment.getEndTime());
            segment.setWorkingHours(workingHours);

            double breakHours = (segment.getBreakMinutes() != null) ? segment.getBreakMinutes() / 60.0 : 0.0;
            segment.setBreakHours(breakHours);
            segment.setEffectiveHours(Math.max(0.0, workingHours - breakHours));

            segments.add(segment);
        }

        return segments;
    }



    private static String stringOf(Object o){ return o==null? null : o.toString(); }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }

    private static boolean equalsIgnoreCaseNoAccents(String a, String b){
        if (a==null || b==null) return false;
        return normalize(a).equals(normalize(b));
    }
    private static String normalize(String s){
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").toLowerCase().trim();
    }





    private double calculateHoursBetween(String startTime, String endTime) {
        try {
            String[] sParts = normalizeTimeForDatabase(startTime).split(":");
            String[] eParts = normalizeTimeForDatabase(endTime).split(":");
            int s = Integer.parseInt(sParts[0]) * 60 + Integer.parseInt(sParts[1]);
            int e = Integer.parseInt(eParts[0]) * 60 + Integer.parseInt(eParts[1]);

            int minutes = (e >= s) ? (e - s) : (1440 - s + e);
            return minutes / 60.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Determinar el nombre del segmento según la hora
    private String determineSegmentName(String startTime) {
        try {
            int hour = Integer.parseInt(startTime.split(":")[0]);
            if (hour >= 6 && hour < 14) return "Mañana";
            if (hour >= 14 && hour < 20) return "Tarde";
            return "Noche";
        } catch (Exception e) {
            return "Turno";
        }
    }

    // Calcular horas entre dos horarios


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

        List<EmployeeScheduleDTO> result = schedules.stream()
                .map(this::convertToDTOWithHours)
                .collect(Collectors.toList());

        return result;
    }



    @Transactional
    public void deleteEmployeeSchedule(Long id) {
        if (!employeeScheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("EmployeeSchedule not found with id: " + id);
        }
        employeeScheduleRepository.deleteById(id);
    }


    private EmployeeScheduleDTO convertToDTOWithHours(EmployeeSchedule schedule) {
        EmployeeScheduleDTO dto = convertToDTO(schedule);

        try {
            ScheduleDetailDTO detailWithHours = groupService.createScheduleDetailWithCalculation(schedule);
            dto.setHoursInPeriod(detailWithHours.getHoursInPeriod());
        } catch (Exception e) {
            dto.setHoursInPeriod(0.0);
        }

        return dto;
    }

    public     EmployeeResponse getEmployeeData(Long employeeId) {
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


    // EmployeeScheduleService.java
    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByDependencyId(
            Long dependencyId,
            LocalDate startDate,
            LocalDate endDate,
            java.time.LocalTime startTime,
            Long shiftId
    ) {
        if (dependencyId == null) {
            return Collections.emptyList();
        }
        List<EmployeeSchedule> schedules;

        if (startDate != null && endDate != null && startTime != null && shiftId != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndFullDateRangeAndShiftId(
                    dependencyId, startDate, endDate, java.sql.Time.valueOf(startTime), shiftId);
        } else if (startDate != null && endDate != null && shiftId != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndDateRangeAndShiftId(
                    dependencyId, startDate, endDate, shiftId);
        } else if (startDate != null && endDate != null && startTime != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndDateRangeAndStartTime(
                    dependencyId, startDate, endDate, java.sql.Time.valueOf(startTime));
        } else if (startDate != null && endDate != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndDateRangeNoTime(
                    dependencyId, startDate, endDate);
        } else if (shiftId != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndShiftId(
                    dependencyId, shiftId);
        } else {
            schedules = employeeScheduleRepository.findByDependencyId(dependencyId);
        }

        return schedules.stream()
                .map(this::convertToDTOWithHours) // ya rellena hoursInPeriod
                .collect(Collectors.toList());
    }

    public List<EmployeeScheduleDTO> getSchedulesByShiftId(Long shiftId) {
        if (shiftId == null || shiftId <= 0) {
            throw new IllegalArgumentException("Shift ID debe ser un número válido.");
        }
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByShiftId(shiftId);
        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeSchedule> createEmployeeSchedules(List<EmployeeSchedule> schedules) {
        return schedules;
    }

    public EmployeeSchedule createEmployeeSchedule(EmployeeSchedule schedule) {
        return schedule;
    }

    @Transactional
    public EmployeeSchedule updateEmployeeSchedule(Long id, EmployeeSchedule schedule) {
        EmployeeSchedule existing = employeeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));

        validateSchedule(schedule);

        existing.setEmployeeId(schedule.getEmployeeId());

        if (schedule.getShift() != null) {
            existing.setShift(schedule.getShift());
        }

        existing.setStartDate(schedule.getStartDate());
        existing.setEndDate(schedule.getEndDate());
        existing.setUpdatedAt(new Date());

        return employeeScheduleRepository.save(existing);
    }


    private void updateTimeBlockDetails(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        existingTimeBlock.setStartTime(Time.valueOf(timeBlockDTO.getStartTime()));
        existingTimeBlock.setEndTime(Time.valueOf(timeBlockDTO.getEndTime()));
        existingTimeBlock.setUpdatedAt(new Date());
    }

    private void validateDayAndParentDay(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        EmployeeScheduleDay currentDay = existingTimeBlock.getEmployeeScheduleDay();

        // Check if the time block belongs to the specified day
        if (!currentDay.getId().equals(timeBlockDTO.getEmployeeScheduleDayId())) {
            throw new IllegalArgumentException("Time block does not belong to the specified day");
        }

        // Additional parent day validation (based on the hint in the original comment)
        Long parentDayId = currentDay.getParentDayId();
        if (parentDayId != null) {
            // Optional: Add specific parent day validation logic here
            // For example, ensuring the update respects parent day constraints
        }
    }

    private void validateEmployeePermissions(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        EmployeeSchedule employeeSchedule = existingTimeBlock.getEmployeeScheduleDay().getEmployeeSchedule();

        if (employeeSchedule == null) {
            throw new IllegalArgumentException("No employee schedule found for this time block");
        }


    }
    private void validateInputParameters(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO == null) {
            throw new IllegalArgumentException("Time block data cannot be null");
        }
        if (timeBlockDTO.getStartTime() == null || timeBlockDTO.getEndTime() == null) {
            throw new IllegalArgumentException("Start time and end time must be provided");
        }
        if (timeBlockDTO.getEmployeeScheduleDayId() == null) {
            throw new IllegalArgumentException("Employee schedule day ID must be specified");
        }
    }
    private void validateTimeBlockInput(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO.getId() == null || timeBlockDTO.getId() <= 0) {
            throw new IllegalArgumentException("Invalid time block ID");
        }
        if (timeBlockDTO.getEmployeeScheduleDayId() == null || timeBlockDTO.getEmployeeScheduleDayId() <= 0) {
            throw new IllegalArgumentException("Invalid employee schedule day ID");
        }
        if (timeBlockDTO.getNumberId() == null || timeBlockDTO.getNumberId().isEmpty()) {
            throw new IllegalArgumentException("Invalid employee identification number");
        }
    }

    private Map<String, Object> createTimeBlockResponse(EmployeeScheduleTimeBlock updatedBlock) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", updatedBlock.getId());
        response.put("employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId());
        response.put("startTime", updatedBlock.getStartTime() != null ? updatedBlock.getStartTime().toString() : null);
        response.put("endTime", updatedBlock.getEndTime() != null ? updatedBlock.getEndTime().toString() : null);
        response.put("numberId", updatedBlock.getEmployeeScheduleDay().getEmployeeSchedule().getEmployeeId().toString());
        response.put("updatedAt", updatedBlock.getUpdatedAt());

        return response;
    }

    private void validateSchedule(EmployeeSchedule schedule) {
        if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0) {
            throw new IllegalArgumentException("Employee ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0) {
            throw new IllegalArgumentException("Shift ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getStartDate() == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }
        if (schedule.getEndDate() != null && schedule.getStartDate().after(schedule.getEndDate())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
        }
    }
    @Transactional
    public List<EmployeeSchedule> createMultipleSchedules(List<EmployeeSchedule> schedules) {
        List<EmployeeSchedule> savedSchedules = new ArrayList<>();

        // Variable para almacenar un ID común para days_parent_id
        Long commonDaysParentId = null;

        for (EmployeeSchedule schedule : schedules) {
            // Validaciones y configuraciones básicas
            validateSchedule(schedule);
            schedule.setCreatedAt(new Date());

            // Buscar y establecer el turno
            Shifts shift = shiftsRepository.findById(schedule.getShift().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado"));
            schedule.setShift(shift);

            // Generar días de horario
            generateScheduleDays(schedule);

            // Guardar el horario
            EmployeeSchedule savedSchedule = employeeScheduleRepository.save(schedule);

            // Guardar explícitamente los días
            List<EmployeeScheduleDay> savedDays = new ArrayList<>();
            for (EmployeeScheduleDay day : savedSchedule.getDays()) {
                day.setEmployeeSchedule(savedSchedule);

                // MODIFICACIÓN IMPORTANTE: Establecer días padre
                if (commonDaysParentId == null) {
                    commonDaysParentId = savedSchedule.getId();
                }
                day.setDaysParentId(commonDaysParentId);

                EmployeeScheduleDay savedDay = employeeScheduleDayRepository.save(day);
                savedDays.add(savedDay);
            }

            savedSchedule.setDays(savedDays);

            // Establecer el mismo days_parent_id para todos los horarios
            savedSchedule.setDaysParentId(commonDaysParentId);
            employeeScheduleRepository.save(savedSchedule);

            savedSchedules.add(savedSchedule);
        }

        return savedSchedules;
    }
    @Transactional
    public EmployeeScheduleTimeBlock updateTimeBlockByDependency(TimeBlockDependencyDTO timeBlockDTO) {
        // 1. Obtener el bloque de tiempo existente
        EmployeeScheduleTimeBlock existingTimeBlock = employeeScheduleTimeBlockRepository
                .findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bloque de tiempo no encontrado con id: " + timeBlockDTO.getId()));

        // 2. Validar que el bloque pertenece al día especificado
        if (!existingTimeBlock.getEmployeeScheduleDay().getId().equals(timeBlockDTO.getEmployeeScheduleDayId())) {
            throw new IllegalArgumentException("El bloque de tiempo no pertenece al día especificado.");
        }

        // 3. Validar que pertenece a la dependencia especificada
        EmployeeScheduleDay employeeScheduleDay = existingTimeBlock.getEmployeeScheduleDay();
        EmployeeSchedule employeeSchedule = employeeScheduleDay.getEmployeeSchedule();

        // Verificaciones más detalladas
        if (employeeSchedule == null) {
            throw new IllegalArgumentException("No se encontró el horario del empleado.");
        }

        if (employeeSchedule.getShift() == null) {
            throw new IllegalArgumentException("No se encontró el turno del empleado.");
        }

        // Obtener los IDs de dependencia




        // 4. Validar horas
        if (timeBlockDTO.getStartTime() == null || timeBlockDTO.getEndTime() == null) {
            throw new IllegalArgumentException("StartTime y EndTime no pueden ser nulos.");
        }

        // 5. Actualizar campos
        existingTimeBlock.setStartTime(Time.valueOf(timeBlockDTO.getStartTime()));
        existingTimeBlock.setEndTime(Time.valueOf(timeBlockDTO.getEndTime()));
        existingTimeBlock.setUpdatedAt(new Date());

        return employeeScheduleTimeBlockRepository.save(existingTimeBlock);
    }

    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(List<Long> employeeIds) {
        // 1. Obtener horarios con días (sin timeBlocks)
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeIdInWithDays(employeeIds);

        if (!schedules.isEmpty()) {
            // 2. Obtener IDs de los horarios
            List<Long> scheduleIds = schedules.stream()
                    .map(EmployeeSchedule::getId)
                    .collect(Collectors.toList());

            // 3. Cargar timeBlocks en batch para todos los días
            List<EmployeeScheduleDay> daysWithBlocks = employeeScheduleRepository
                    .findDaysWithTimeBlocksByScheduleIds(scheduleIds);

            // 4. Asociar los timeBlocks a los días correspondientes
            Map<Long, List<EmployeeScheduleDay>> daysByScheduleId = daysWithBlocks.stream()
                    .collect(Collectors.groupingBy(
                            day -> day.getEmployeeSchedule().getId(),
                            Collectors.toList()
                    ));

            schedules.forEach(schedule -> {
                List<EmployeeScheduleDay> days = daysByScheduleId.get(schedule.getId());
                if (days != null) {
                    // Reemplazar la lista de días con los que tienen timeBlocks cargados
                    schedule.getDays().clear();
                    schedule.getDays().addAll(days);
                }
            });
        }

        return schedules.stream()
                .map(this::convertToCompleteDTO)
                .collect(Collectors.toList());
    }
    private EmployeeScheduleDTO convertToCompleteDTO(EmployeeSchedule schedule) {
        // 1. Obtener datos del empleado desde el microservicio
        EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        // 2. Construir estructura de días
        Map<String, Object> daysStructure = buildDaysStructure(schedule);

        // 3. Crear DTO con toda la información
        return new EmployeeScheduleDTO(
                schedule.getId(),
                getEmployeeField(employee, EmployeeResponse.Employee::getNumberId),
                getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSurName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondSurname, "Desconocido"),
                getEmployeeDependency(employee),
                getEmployeePosition(employee),
                formatDate(schedule.getStartDate()),
                formatDate(schedule.getEndDate()),
                buildShiftDTO(schedule.getShift()),
                schedule.getDaysParentId(),
                daysStructure
        );
    }


    public List<EmployeeScheduleDTO> getSchedulesByDateRange(Date startDate, Date endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }

        List<EmployeeSchedule> schedules;

        if (endDate == null) {
            // Si no se proporciona endDate, obtener registros donde endDate sea NULL
            schedules = employeeScheduleRepository.findByStartDateAndNullEndDate(startDate);
        } else {
            if (startDate.after(endDate)) {
                throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
            }
            schedules = employeeScheduleRepository.findByDateRange(startDate, endDate);
        }

        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildDaysStructure(EmployeeSchedule schedule) {
        Map<String, Object> daysMap = new LinkedHashMap<>();
        daysMap.put("id", schedule.getDaysParentId());

        // Ordenar días por fecha
        List<EmployeeScheduleDay> sortedDays = schedule.getDays() != null ?
                schedule.getDays().stream()
                        .sorted(Comparator.comparing(EmployeeScheduleDay::getDate))
                        .collect(Collectors.toList()) :
                new ArrayList<>();

        // Convertir días a DTO
        List<Map<String, Object>> dayItems = sortedDays.stream()
                .map(day -> {
                    Map<String, Object> dayMap = new LinkedHashMap<>();
                    dayMap.put("id", day.getId());
                    dayMap.put("date", dateFormat.format(day.getDate()));
                    dayMap.put("dayOfWeek", day.getDayOfWeek());

                    // Convertir bloques de tiempo
                    List<Map<String, String>> timeBlocks = day.getTimeBlocks() != null ?
                            day.getTimeBlocks().stream()
                                    .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                                    .map(block -> {
                                        Map<String, String> blockMap = new LinkedHashMap<>();
                                        blockMap.put("id", block.getId().toString());
                                        blockMap.put("startTime", block.getStartTime().toString());
                                        blockMap.put("endTime", block.getEndTime().toString());
                                        return blockMap;
                                    })
                                    .collect(Collectors.toList()) :
                            new ArrayList<>();

                    dayMap.put("timeBlocks", timeBlocks);
                    return dayMap;
                })
                .collect(Collectors.toList());

        daysMap.put("items", dayItems);
        return daysMap;
    }

    private ShiftsDTO buildShiftDTO(Shifts shift) {
        if (shift == null) return null;

        // Si tu entidad Shifts no tiene getTimeBreak() como Long, déjalo en null.
        Long timeBreak = null;
        // Si SÍ tienes getTimeBreak() como Long o Integer, cámbialo por:
        // Long timeBreak = shift.getTimeBreak();

        return new ShiftsDTO(
                shift.getId(),
                shift.getName(),
                shift.getDescription(),
                timeBreak,
                Collections.emptyList() // si luego quieres mapear detalles, aquí los pones
        );
    }
    private EmployeeScheduleDTO convertToDTO(EmployeeSchedule schedule) {
        // 1. Obtener datos del empleado desde el microservicio
        EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        // 2. Convertir el turno a DTO
        ShiftsDTO shiftDTO = buildShiftDTO(schedule.getShift());

        // 3. Construir estructura de días
        Map<String, Object> daysStructure = buildDaysStructure(schedule);

        // 4. Construir y retornar el DTO completo
        return new EmployeeScheduleDTO(
                schedule.getId(),
                getEmployeeField(employee, EmployeeResponse.Employee::getNumberId),
                getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSurName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondSurname, "Desconocido"),
                getEmployeeDependency(employee),
                getEmployeePosition(employee),
                formatDate(schedule.getStartDate()),
                formatDate(schedule.getEndDate()),
                shiftDTO,
                schedule.getDaysParentId(),
                daysStructure
        );
    }

    private String formatDate(Date date) {
        return date != null ? new SimpleDateFormat("yyyy-MM-dd").format(date) : null;
    }

    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter) {
        return employee != null ? getter.apply(employee) : null;
    }

    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter,
                                   T defaultValue) {
        try {
            return employee != null ? getter.apply(employee) : defaultValue;
        } catch (NullPointerException e) {
            return defaultValue;
        }
    }

    private String getEmployeeDependency(EmployeeResponse.Employee employee) {
        return getEmployeeField(employee,
                e -> e.getPosition().getDependency().getName(),
                "Sin dependencia");
    }

    private String getEmployeePosition(EmployeeResponse.Employee employee) {
        return getEmployeeField(employee,
                e -> e.getPosition().getName(),
                "Sin posición");
    }

}

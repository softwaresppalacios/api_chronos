package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import org.springframework.transaction.annotation.Transactional;
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
import sp.sistemaspalacios.api_chronos.dto.ScheduleDetailDTO;
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
            HolidayExemptionService holidayExemptionService
    ) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.restTemplate = restTemplate;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.configService = configService;
        this.holidayService = holidayService;
        this.groupService = groupService;
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
        List<sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary> summaries =
                idsPorEmpleado.keySet().stream()
                        .map(empId -> {
                            try {
                                return calculateEmployeeHoursSummary(empId);
                            } catch (Exception ex) {
                                System.err.println("❌ Error calculando resumen para employee " + empId + ": " + ex.getMessage());
                                var empty = new sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary();
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

    // =================== RESUMEN DE HORAS ===================

    public sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary
    calculateEmployeeHoursSummary(Long employeeId) {

        List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId);

        var s = new sp.sistemaspalacios.api_chronos.controller.employeeSchedule.EmployeeScheduleController.EmployeeHoursSummary();
        s.setEmployeeId(employeeId);

        if (groups == null || groups.isEmpty()) {
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
                    String code = e.getKey();
                    Object v = e.getValue();
                    if (!(v instanceof Number)) continue;
                    BigDecimal add = BigDecimal.valueOf(((Number) v).doubleValue());
                    breakdownSum.put(code, breakdownSum.getOrDefault(code, BigDecimal.ZERO).add(add));
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
                if (hours.compareTo(maxExtra) > 0) {
                    maxExtra = hours; predominantExtra = code;
                }
            } else if (code.startsWith("FESTIVO_")) {
                if (hours.compareTo(maxFestivo) > 0) {
                    maxFestivo = hours; predominantFestivo = code;
                }
            }
        }

        s.setTotalHours(total.doubleValue());
        s.setAssignedHours(assigned.doubleValue());
        s.setOvertimeHours(extra.doubleValue());
        s.setFestivoHours(festivo.doubleValue());
        s.setOvertimeType(predominantExtra != null ? predominantExtra : "Normal");
        s.setFestivoType(predominantFestivo);

        Map<String, Object> bd = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : breakdownSum.entrySet()) {
            bd.put(e.getKey(), e.getValue().doubleValue());
        }
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

        // ====== MAPA DE DECISIONES ======
        Map<LocalDate, HolidayDecision> decisionMap = (holidayDecisions != null ? holidayDecisions : Collections.<HolidayDecision>emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(h -> h.getHolidayDate() != null)
                .collect(Collectors.toMap(HolidayDecision::getHolidayDate, h -> h));

        // ⛔ OMITIR SOLO cuando hay razón explícita (exención real)
        Set<LocalDate> exemptDates = decisionMap.entrySet().stream()
                .filter(entry -> {
                    HolidayDecision v = entry.getValue();
                    boolean hasReason = v.getExemptionReason() != null && !v.getExemptionReason().isBlank();
                    return hasReason; // <<-- “No aplicar recargo” NO se omite
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        System.out.println("🎯 GENERACIÓN CON DECISIONES:");
        System.out.println("   📅 Período: " + startDate + " al " + endDate);
        System.out.println("   ⛔ Días OMITIDOS (solo exención con razón): " + exemptDates);
        System.out.println("   📋 Decisiones totales: " + decisionMap.size());

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            final LocalDate currentDay = d;

            // Omitir solo si exento con razón
            if (exemptDates.contains(currentDay)) {
                System.out.println("⛔ Día omitido (exención con razón): " + currentDay);
                continue;
            }

            boolean isHoliday = holidayService.isHoliday(currentDay);
            HolidayDecision decision = decisionMap.get(currentDay);
            boolean treatAsHoliday = isHoliday && (decision == null || decision.isApplyHolidayCharge());

            EmployeeScheduleDay day = new EmployeeScheduleDay();
            day.setEmployeeSchedule(schedule);
            day.setDate(java.sql.Date.valueOf(currentDay));
            day.setDayOfWeek(currentDay.getDayOfWeek().getValue());
            day.setCreatedAt(new Date());
            day.setTimeBlocks(new ArrayList<>());

            for (ShiftDetail sd : details) {
                if (sd.getDayOfWeek() == null || !Objects.equals(sd.getDayOfWeek(), currentDay.getDayOfWeek().getValue())) continue;
                if (sd.getStartTime() == null || sd.getEndTime() == null) continue;

                String sStr = sd.getStartTime().contains(":") && sd.getStartTime().split(":").length == 2
                        ? sd.getStartTime() + ":00" : sd.getStartTime();
                String eStr = sd.getEndTime().contains(":") && sd.getEndTime().split(":").length == 2
                        ? sd.getEndTime() + ":00" : sd.getEndTime();

                EmployeeScheduleTimeBlock tb = new EmployeeScheduleTimeBlock();
                tb.setEmployeeScheduleDay(day);
                tb.setStartTime(Time.valueOf(sStr));
                tb.setEndTime(Time.valueOf(eStr));
                tb.setCreatedAt(new Date());

                day.getTimeBlocks().add(tb);
            }

            schedule.getDays().add(day);
            System.out.println("✅ Día generado: " + currentDay + " (festivo real: " + isHoliday + ", treatAsHoliday=" + treatAsHoliday + ")");
        }

        System.out.println("📊 Total días generados: " + schedule.getDays().size());

        // ====== GUARDAR EXENCIONES/DECISIONES ======
        if (holidayDecisions != null) {
            for (HolidayDecision h : holidayDecisions) {
                if (h != null && h.getHolidayDate() != null) {
                    try {
                        if (h.getExemptionReason() != null && !h.getExemptionReason().isBlank()) {
                            // Exención explícita - día omitido
                            holidayExemptionService.saveExemption(
                                    schedule.getEmployeeId(),
                                    h.getHolidayDate(),
                                    holidayService.getHolidayName(h.getHolidayDate()),
                                    h.getExemptionReason(),
                                    null
                            );
                            System.out.println("💾 Exención guardada (razón explícita): " + h.getHolidayDate() + " - " + h.getExemptionReason());
                        } else if (!h.isApplyHolidayCharge()) {
                            // 'No aplicar' → también registrar exención (sirve para tratarlos como REGULARES)
                            holidayExemptionService.saveExemption(
                                    schedule.getEmployeeId(),
                                    h.getHolidayDate(),
                                    holidayService.getHolidayName(h.getHolidayDate()),
                                    "NO_APLICAR_RECARGO",
                                    null
                            );
                            System.out.println("💾 Exención guardada (NO_APLICAR_RECARGO): " + h.getHolidayDate());
                        }
                    } catch (Exception ex) {
                        System.err.println("⚠️ No se pudo guardar la exención/decisión: " + ex.getMessage());
                    }
                }
            }
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

            // 1) nuevos vs existentes
            for (ScheduleAssignment newAssignment : empAssignments) {
                for (EmployeeSchedule existingSchedule : existing) {
                    ScheduleConflict conflict = checkForConflict(newAssignment, existingSchedule);
                    if (conflict != null) conflicts.add(conflict);
                }
            }

            // 2) nuevos entre sí
            for (int i = 0; i < empAssignments.size(); i++) {
                for (int j = i + 1; j < empAssignments.size(); j++) {
                    ScheduleConflict conflict = checkForConflictBetweenAssignments(
                            empAssignments.get(i), empAssignments.get(j));
                    if (conflict != null) conflicts.add(conflict);
                }
            }
        }
        return conflicts;
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

        List<EmployeeScheduleDTO> result = schedules.stream()
                .map(this::convertToDTOWithHours)
                .collect(Collectors.toList());

        return result;
    }

    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByDependencyId(Long dependencyId,
                                                                LocalDate startDate,
                                                                LocalDate endDate,
                                                                java.time.LocalTime startTime,
                                                                Long shiftId) {
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

        if (schedule.getShift() != null) {
            dto.setShiftName(schedule.getShift().getName());
        }

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

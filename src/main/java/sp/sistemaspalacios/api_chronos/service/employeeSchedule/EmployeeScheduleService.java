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
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.math.BigDecimal;
import java.sql.Time;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class EmployeeScheduleService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final RestTemplate restTemplate;
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository;

    private final HolidayService holidayService;
    private final ScheduleAssignmentGroupService groupService;
    private final HolidayExemptionService holidayExemptionService;
    private final Map<Long, EmployeeResponse> employeeDataCache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public EmployeeScheduleService(
            EmployeeScheduleRepository employeeScheduleRepository,
            ShiftsRepository shiftsRepository,
            RestTemplate restTemplate,
            EmployeeScheduleDayRepository employeeScheduleDayRepository,
            HolidayService holidayService,
            ScheduleAssignmentGroupService groupService,
            HolidayExemptionService holidayExemptionService,
            EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository
    ) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.restTemplate = restTemplate;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.holidayService = holidayService;
        this.groupService = groupService;
        this.holidayExemptionService = holidayExemptionService;
        this.employeeScheduleTimeBlockRepository = employeeScheduleTimeBlockRepository;
    }

    // =================== UTILIDADES FECHA/HORA ===================

    private static LocalDate toLocalDate(java.util.Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static String normalizeTimeForDatabase(String time) {
        if (time == null) return "00:00:00";
        String t = time.trim();
        if (t.matches("^\\d{2}:\\d{2}:\\d{2}$")) return t;        // HH:mm:ss
        if (t.matches("^\\d{1}:\\d{2}$")) return "0" + t + ":00"; // H:mm -> 0H:mm:00
        if (t.matches("^\\d{2}:\\d{2}$")) return t + ":00";       // HH:mm -> HH:mm:00
        if (t.matches("^\\d{2}$"))        return t + ":00:00";    // HH -> HH:00:00
        return "00:00:00";
    }

    private static int toMinutes(String hhmmss) {
        String[] p = hhmmss.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static boolean timeOverlaps(String s1, String e1, String s2, String e2) {
        try {
            String ns1 = normalizeTimeForDatabase(s1);
            String ne1 = normalizeTimeForDatabase(e1);
            String ns2 = normalizeTimeForDatabase(s2);
            String ne2 = normalizeTimeForDatabase(e2);

            int a1 = toMinutes(ns1), a2 = toMinutes(ne1);
            int b1 = toMinutes(ns2), b2 = toMinutes(ne2);

            if (a2 <= a1) a2 += 1440; // soporta cruce nocturno
            if (b2 <= b1) b2 += 1440;

            return a1 < b2 && b1 < a2;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean equalsIgnoreCaseNoAccents(String a, String b){
        if (a == null || b == null) return false;
        String na = Normalizer.normalize(a, Normalizer.Form.NFD).replaceAll("\\p{M}","");
        String nb = Normalizer.normalize(b, Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return na.equalsIgnoreCase(nb);
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

    private boolean datesOverlap(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }

    private static String stringOf(Object o){ return o==null? null : o.toString(); }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }

    // =================== ASIGNACIONES ===================
    private void validateRequest(AssignmentRequest request) {
        if (request == null || request.getAssignments() == null || request.getAssignments().isEmpty()) {
            throw new ValidationException("Debe proporcionar al menos una asignación", Arrays.asList("Sin asignaciones"));
        }

        System.out.println("=== VALIDANDO REQUEST ===");
        System.out.println("Número de asignaciones: " + request.getAssignments().size());

        List<String> errors = new ArrayList<>();
        for (ScheduleAssignment a : request.getAssignments()) {
            System.out.println("=== ASIGNACIÓN INDIVIDUAL ===");
            System.out.println("Employee ID: " + a.getEmployeeId());
            System.out.println("Shift ID: " + a.getShiftId());
            System.out.println("Start Date: " + a.getStartDate());
            System.out.println("End Date: " + a.getEndDate());

            if (a.getEmployeeId() == null) errors.add("Employee ID requerido");
            if (a.getShiftId() == null) errors.add("Shift ID requerido");
            if (a.getStartDate() == null) errors.add("Fecha de inicio requerida");
            if (a.getEndDate() != null && a.getStartDate() != null && a.getEndDate().isBefore(a.getStartDate())) {
                errors.add("Fecha de fin debe ser posterior a fecha de inicio");
            }
        }
        if (!errors.isEmpty()) throw new ValidationException("Errores de validación", errors);
    }
    @Transactional
    public AssignmentResult processMultipleAssignments(AssignmentRequest request) {
        validateRequest(request);

        List<ScheduleConflict> conflicts = detectScheduleConflicts(request.getAssignments());
        if (!conflicts.isEmpty()) throw new ConflictException("Conflictos de horarios detectados", conflicts);

        List<HolidayWarning> holidayWarnings = detectHolidayWarnings(request.getAssignments());
        if (!holidayWarnings.isEmpty()) {
            AssignmentResult preview = new AssignmentResult();
            preview.setSuccess(false);
            preview.setMessage("Se detectaron días festivos");
            preview.setHolidayWarnings(holidayWarnings);
            preview.setRequiresConfirmation(true);
            return preview;
        }

        List<EmployeeSchedule> created = new ArrayList<>();
        for (ScheduleAssignment a : request.getAssignments()) {
            System.out.println("=== PROCESANDO EMPLEADO: " + a.getEmployeeId() + " ===");

            EmployeeSchedule s = createScheduleFromAssignment(a);
            s.setDays(new ArrayList<>());
            EmployeeSchedule saved = employeeScheduleRepository.save(s);
            generateScheduleDays(saved);
            created.add(employeeScheduleRepository.save(saved));
        }
        employeeScheduleRepository.flush();

        Map<Long, List<Long>> idsPorEmpleado = created.stream()
                .collect(Collectors.groupingBy(EmployeeSchedule::getEmployeeId,
                        Collectors.mapping(EmployeeSchedule::getId, Collectors.toList())));

        idsPorEmpleado.forEach((empId, scheduleIds) -> {
            groupService.processScheduleAssignment(empId, scheduleIds);
            try {
                holidayExemptionService.backfillGroupIds(empId, groupService.getEmployeeGroups(empId));
            } catch (Exception e) {
                System.err.println("No se pudo enlazar exenciones a grupos para empId " + empId + ": " + e.getMessage());
            }
        });



        List<EmployeeHoursSummary> summaries = idsPorEmpleado.keySet().stream()
                .map(empId -> {
                    try { return calculateEmployeeHoursSummary(empId); }
                    catch (Exception ex) {
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
                }).collect(Collectors.toList());

        AssignmentResult result = new AssignmentResult();
        result.setSuccess(true);
        result.setMessage("Turnos asignados correctamente");
        result.setUpdatedEmployees(summaries);
        result.setRequiresConfirmation(false);
        return result;
    }



    @Transactional
    public AssignmentResult processHolidayAssignment(HolidayConfirmationRequest request) {
        if (request == null || request.getConfirmedAssignments() == null || request.getConfirmedAssignments().isEmpty()) {
            throw new IllegalArgumentException("confirmedAssignments es requerido");
        }

        List<EmployeeSchedule> created = new ArrayList<>();
        for (ConfirmedAssignment ca : request.getConfirmedAssignments()) {
            shiftsRepository.findById(ca.getShiftId())
                    .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + ca.getShiftId()));

            EmployeeSchedule s = createScheduleFromConfirmedAssignment(ca);
            s.setDays(new ArrayList<>());
            EmployeeSchedule saved = employeeScheduleRepository.save(s);
            List<HolidayDecision> safe = (ca.getHolidayDecisions() != null) ? ca.getHolidayDecisions() : Collections.emptyList();
            generateScheduleDaysWithHolidayDecisions(saved, safe);
            created.add(employeeScheduleRepository.save(saved));
        }

        Map<Long, List<Long>> idsPorEmpleado = created.stream()
                .collect(Collectors.groupingBy(EmployeeSchedule::getEmployeeId,
                        Collectors.mapping(EmployeeSchedule::getId, Collectors.toList())));

        idsPorEmpleado.forEach((empId, ids) -> {
            groupService.processScheduleAssignment(empId, ids);
            try {
                holidayExemptionService.backfillGroupIds(empId, groupService.getEmployeeGroups(empId));
            } catch (Exception e) {
                System.err.println("No se pudo enlazar exenciones a grupos para empId " + empId + ": " + e.getMessage());
            }
        });

        List<EmployeeHoursSummary> summaries = idsPorEmpleado.keySet().stream()
                .map(this::calculateEmployeeHoursSummary)
                .collect(Collectors.toList());

        AssignmentResult result = new AssignmentResult();
        result.setSuccess(true);
        result.setMessage("Turnos asignados correctamente con decisiones de festivos");
        result.setUpdatedEmployees(summaries);
        result.setRequiresConfirmation(false);
        return result;
    }

    // =================== RESUMEN HORAS ===================

    public EmployeeHoursSummary calculateEmployeeHoursSummary(Long employeeId) {
        try { groupService.syncAllGroupStatuses(); } catch (Exception ignored) { }

        List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId).stream()
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

        BigDecimal regular = BigDecimal.ZERO, extra = BigDecimal.ZERO, festivo = BigDecimal.ZERO;
        Map<String, BigDecimal> breakdownSum = new HashMap<>();

        for (ScheduleAssignmentGroupDTO g : groups) {
            if (g.getRegularHours() != null) regular = regular.add(g.getRegularHours());
            if (g.getOvertimeHours() != null) extra = extra.add(g.getOvertimeHours());
            if (g.getFestivoHours() != null) festivo = festivo.add(g.getFestivoHours());

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

        String predominantExtra = null, predominantFestivo = null;
        BigDecimal maxExtra = BigDecimal.ZERO, maxFestivo = BigDecimal.ZERO;

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
            if (!conflicts.isEmpty()) errors.add("Se detectaron conflictos de horarios");

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

    private void generateScheduleDaysWithHolidayDecisions(EmployeeSchedule schedule, List<HolidayDecision> holidayDecisions) {
        if (schedule.getDays() == null) schedule.setDays(new ArrayList<>());
        else schedule.getDays().clear();

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

        Map<LocalDate, HolidayDecision> decisionMap = (holidayDecisions != null ? holidayDecisions : Collections.<HolidayDecision>emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(h -> h.getHolidayDate() != null)
                .collect(Collectors.toMap(HolidayDecision::getHolidayDate, h -> h, (a,b) -> a));

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            HolidayDecision decision = decisionMap.get(d);

            // PASO 1: GUARDAR EXENCIONES PRIMERO (independientemente de si se crea el día)
            if (decision != null) {
                try {
                    System.out.println("Procesando decisión de festivo para " + d);
                    System.out.println("  - Razón de exención: '" + decision.getExemptionReason() + "'");
                    System.out.println("  - Aplicar recargo: " + decision.isApplyHolidayCharge());

                    if (decision.getExemptionReason() != null && !decision.getExemptionReason().isBlank()) {
                        System.out.println("  - Guardando exención con razón personalizada");
                        holidayExemptionService.saveExemption(
                                schedule.getEmployeeId(), d, holidayService.getHolidayName(d),
                                decision.getExemptionReason(), null
                        );
                    } else if (!decision.isApplyHolidayCharge()) {
                        System.out.println("  - Guardando exención por no aplicar recargo");
                        holidayExemptionService.saveExemption(
                                schedule.getEmployeeId(), d, holidayService.getHolidayName(d),
                                "NO_APLICAR_RECARGO", null
                        );
                    }
                } catch (Exception e) {
                    System.err.println("Error guardando exención de festivo: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // PASO 2: DECIDIR SI CREAR EL DÍA DE TRABAJO
            boolean skipDayCreation = decision != null &&
                    decision.getExemptionReason() != null &&
                    !decision.getExemptionReason().isBlank() &&
                    !decision.isApplyHolidayCharge();

            if (skipDayCreation) {
                System.out.println("  - Saltando creación de día (empleado no trabaja)");
                continue;
            }

            // PASO 3: CREAR EL DÍA DE TRABAJO
            EmployeeScheduleDay day = new EmployeeScheduleDay();
            day.setEmployeeSchedule(schedule);
            day.setDate(java.sql.Date.valueOf(d));
            day.setDayOfWeek(d.getDayOfWeek().getValue());
            day.setCreatedAt(new Date());
            day.setTimeBlocks(new ArrayList<>());

            for (ShiftDetail sd : details) {
                if (sd.getDayOfWeek() == null || !Objects.equals(sd.getDayOfWeek(), d.getDayOfWeek().getValue())) continue;
                if (sd.getStartTime() == null || sd.getEndTime() == null) continue;

                String finalStartTime = sd.getStartTime();
                String finalEndTime   = sd.getEndTime();

                if (decision != null && decision.getShiftSegments() != null) {
                    for (Object segmentObj : decision.getShiftSegments()) {
                        if (!(segmentObj instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> seg = (Map<String, Object>) segmentObj;

                        String segName = stringOf(seg.get("segmentName"));
                        String expected = determineSegmentName(sd.getStartTime());

                        if (equalsIgnoreCaseNoAccents(segName, expected)) {
                            String s  = stringOf(seg.get("startTime"));
                            String e  = stringOf(seg.get("endTime"));
                            if (!isBlank(s))  finalStartTime = s;
                            if (!isBlank(e))  finalEndTime   = e;
                            break;
                        }
                    }
                }

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
        }
    }
    // =================== CONFLICTOS & FESTIVOS ===================

    private List<ShiftDetail> detailsForDay(Shifts shift, int dayOfWeek) {
        if (shift == null || shift.getShiftDetails() == null) return Collections.emptyList();
        return shift.getShiftDetails().stream()
                .filter(d -> Objects.equals(d.getDayOfWeek(), dayOfWeek) && d.getStartTime() != null && d.getEndTime() != null)
                .collect(Collectors.toList());
    }

    private ScheduleConflict createConflict(ScheduleAssignment assignment, LocalDate conflictDate, String message) {
        ScheduleConflict conflict = new ScheduleConflict();
        conflict.setEmployeeId(assignment.getEmployeeId());
        conflict.setConflictDate(conflictDate);
        conflict.setMessage(message);
        return conflict;
    }

    private List<ScheduleConflict> detectScheduleConflicts(List<ScheduleAssignment> assignments) {
        List<ScheduleConflict> conflicts = new ArrayList<>();

        Map<Long, List<ScheduleAssignment>> byEmp = assignments.stream()
                .collect(Collectors.groupingBy(ScheduleAssignment::getEmployeeId));

        for (Map.Entry<Long, List<ScheduleAssignment>> entry : byEmp.entrySet()) {
            Long employeeId = entry.getKey();
            List<ScheduleAssignment> empAssignments = entry.getValue();

            List<EmployeeSchedule> existing = employeeScheduleRepository.findByEmployeeId(employeeId);

            // nuevos vs existentes (con solape de horario)
            for (ScheduleAssignment na : empAssignments) {
                for (EmployeeSchedule ex : existing) {
                    ScheduleConflict c = checkForConflictWithTimeOverlap(na, ex);
                    if (c != null) conflicts.add(c);
                }
            }

            // nuevos entre sí (con solape de horario)
            for (int i = 0; i < empAssignments.size(); i++) {
                for (int j = i + 1; j < empAssignments.size(); j++) {
                    ScheduleConflict c = checkForConflictBetweenAssignments(empAssignments.get(i), empAssignments.get(j));
                    if (c != null) conflicts.add(c);
                }
            }
        }
        return conflicts;
    }

    private ScheduleConflict checkForConflictWithTimeOverlap(ScheduleAssignment assignment, EmployeeSchedule existing) {
        LocalDate newStart = assignment.getStartDate();
        LocalDate newEnd = (assignment.getEndDate() != null) ? assignment.getEndDate() : newStart;
        LocalDate existingStart = toLocalDate(existing.getStartDate());
        LocalDate existingEnd = (existing.getEndDate() != null) ? toLocalDate(existing.getEndDate()) : existingStart;

        if (!datesOverlap(newStart, newEnd, existingStart, existingEnd)) return null;

        Shifts newShift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
        Shifts existingShift = existing.getShift();
        if (newShift == null || existingShift == null) {
            return createConflict(assignment, newStart, "No se pudo verificar turnos");
        }

        LocalDate overlapStart = Collections.max(Arrays.asList(newStart, existingStart));
        LocalDate overlapEnd = Collections.min(Arrays.asList(newEnd, existingEnd));

        for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
            int dow = date.getDayOfWeek().getValue();
            List<ShiftDetail> n = detailsForDay(newShift, dow);
            List<ShiftDetail> e = detailsForDay(existingShift, dow);

            if (!n.isEmpty() && !e.isEmpty()) {
                boolean clash = n.stream().anyMatch(d1 ->
                        e.stream().anyMatch(d2 -> timeOverlaps(d1.getStartTime(), d1.getEndTime(), d2.getStartTime(), d2.getEndTime()))
                );
                if (clash) {
                    return createConflict(assignment, date,
                            "Conflicto de horarios el " + DATE_FMT.format(date) + ": solapamiento con turno " + existingShift.getName());
                }
            }
        }
        return null;
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
            List<ShiftDetail> d1 = detailsForDay(sh1, dow);
            List<ShiftDetail> d2 = detailsForDay(sh2, dow);

            if (!d1.isEmpty() && !d2.isEmpty()) {
                boolean clash = d1.stream().anyMatch(x ->
                        d2.stream().anyMatch(y -> timeOverlaps(x.getStartTime(), x.getEndTime(), y.getStartTime(), y.getEndTime()))
                );
                if (clash) {
                    ScheduleConflict c = new ScheduleConflict();
                    c.setEmployeeId(a1.getEmployeeId());
                    c.setConflictDate(d);
                    c.setMessage("Conflicto el día " + DATE_FMT.format(d) + " - turnos se solapan en horario");
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
                    warning.setShiftSegments(calculateShiftSegmentsForDay(shift, d));
                    warning.setRequiresConfirmation(true);
                    warnings.add(warning);
                }
            }
        }
        return warnings;
    }

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
        } catch (Exception ignored) { }
        return "Empleado " + employeeId;
    }

    private List<ShiftSegmentDetail> calculateShiftSegmentsForDay(Shifts shift, LocalDate date) {
        List<ShiftSegmentDetail> segments = new ArrayList<>();
        int dow = date.getDayOfWeek().getValue();

        List<ShiftDetail> dayDetails = shift.getShiftDetails().stream()
                .filter(detail -> detail.getDayOfWeek() != null && detail.getDayOfWeek().equals(dow))
                .filter(detail -> detail.getStartTime() != null && detail.getEndTime() != null)
                .collect(Collectors.toList());

        for (ShiftDetail detail : dayDetails) {
            ShiftSegmentDetail segment = new ShiftSegmentDetail();
            segment.setSegmentName(determineSegmentName(detail.getStartTime()));
            segment.setStartTime(normalizeTimeForDatabase(detail.getStartTime()));
            segment.setEndTime(normalizeTimeForDatabase(detail.getEndTime()));

            if (detail.getBreakStartTime() != null) segment.setBreakStartTime(normalizeTimeForDatabase(detail.getBreakStartTime()));
            if (detail.getBreakEndTime() != null) segment.setBreakEndTime(normalizeTimeForDatabase(detail.getBreakEndTime()));
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

    // =================== API PÚBLICA (mismo contrato) ===================

    public List<EmployeeScheduleDTO> getAllEmployeeSchedules() {
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findAll();
        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public EmployeeScheduleDTO getEmployeeScheduleById(Long id) {
        EmployeeSchedule schedule = employeeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));
        syncEmployeeGroupsStatus(schedule.getEmployeeId());
        return convertToDTO(schedule);
    }

    public List<EmployeeScheduleDTO> getSchedulesByEmployeeId(Long employeeId) {
        return getSchedulesByEmployeeId(employeeId, false);
    }

    public List<EmployeeScheduleDTO> getSchedulesByEmployeeId(Long employeeId, boolean onlyActiveGroups) {
        if (employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("Employee ID debe ser un número válido.");
        }


        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);



        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    private void syncEmployeeGroupsStatus(Long employeeId) {
        try {
            List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId);
            for (ScheduleAssignmentGroupDTO group : groups) {
                groupService.getGroupById(group.getId()); // fuerza sync interno
            }
        } catch (Exception ignored) { }
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

    public EmployeeResponse getEmployeeData(Long employeeId) {
        if (employeeId == null) return null;
        try {
            String url = "http://192.168.23.3:40020/api/employees/bynumberid/" + employeeId;
            ResponseEntity<EmployeeResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(null), EmployeeResponse.class
            );
            if (response.getStatusCode().is2xxSuccessful()) return response.getBody();
        } catch (Exception ignored) { }
        return null;
    }



    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSchedulesByDependencyId(
            Long dependencyId,
            LocalDate startDate,
            LocalDate endDate,
            java.time.LocalTime startTime,
            Long shiftId
    ) {
        if (dependencyId == null) return Collections.emptyList();

        // Consulta optimizada
        List<EmployeeSchedule> schedules;
        if (shiftId != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndShiftId(dependencyId, shiftId);
        } else {
            schedules = employeeScheduleRepository.findByDependencyId(dependencyId);
        }

        if (schedules.isEmpty()) return Collections.emptyList();

        // RESTAURAR SOLO LA AGRUPACIÓN, sin la lógica de grupos activos
        return groupSchedulesByShiftAndDependency(schedules);
    }
    // MÉTODO NUEVO para agrupar en el backend
    private List<Map<String, Object>> groupSchedulesByShiftAndDependency(List<EmployeeSchedule> schedules) {
        Map<String, Map<String, Object>> groupMap = new LinkedHashMap<>();

        for (EmployeeSchedule schedule : schedules) {
            String shiftId = schedule.getShift() != null ? schedule.getShift().getId().toString() : "null";

            // OBTENER DEPENDENCIA DE LOS DATOS DEL EMPLEADO (como antes)
            EmployeeResponse employeeResponse = getEmployeeData(schedule.getEmployeeId());
            EmployeeResponse.Employee employee = employeeResponse != null ? employeeResponse.getEmployee() : null;
            String dependency = getEmployeeDependency(employee);

            String groupKey = shiftId + "::" + dependency;

            // Crear grupo si no existe
            if (!groupMap.containsKey(groupKey)) {
                Map<String, Object> group = new LinkedHashMap<>();

                // Información del shift
                Map<String, Object> shiftInfo = new LinkedHashMap<>();
                if (schedule.getShift() != null) {
                    shiftInfo.put("id", schedule.getShift().getId());
                    shiftInfo.put("name", schedule.getShift().getName());
                    shiftInfo.put("description", schedule.getShift().getDescription());
                }

                group.put("shift", shiftInfo);
                group.put("dependency", dependency); // Usar la dependencia del empleado
                group.put("employeeCount", 0);
                group.put("employees", new ArrayList<>());
                group.put("employeeIds", new HashSet<Long>());

                groupMap.put(groupKey, group);
            }

            Map<String, Object> group = groupMap.get(groupKey);
            @SuppressWarnings("unchecked")
            Set<Long> employeeIds = (Set<Long>) group.get("employeeIds");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> employees = (List<Map<String, Object>>) group.get("employees");

            // Solo agregar si es un empleado nuevo para este grupo
            if (!employeeIds.contains(schedule.getEmployeeId())) {
                employeeIds.add(schedule.getEmployeeId());

                // Crear objeto del empleado (reutilizar la misma lógica)
                Map<String, Object> employeeData = new LinkedHashMap<>();
                employeeData.put("id", schedule.getEmployeeId());
                employeeData.put("numberId", schedule.getEmployeeId());
                employeeData.put("firstName", getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, ""));
                employeeData.put("surName", getEmployeeField(employee, EmployeeResponse.Employee::getSurName, ""));
                employeeData.put("shift", group.get("shift"));

                // Procesar días
                Map<String, Object> daysStructure = buildDaysStructure(schedule);
                employeeData.put("days", daysStructure);
                employeeData.put("startDate", formatDate(schedule.getStartDate()));
                employeeData.put("endDate", formatDate(schedule.getEndDate()));

                employees.add(employeeData);
                group.put("employeeCount", employees.size());
            }
        }

        return groupMap.values().stream()
                .peek(group -> group.remove("employeeIds"))
                .collect(Collectors.toList());
    }




    public List<EmployeeScheduleDTO> getSchedulesByShiftId(Long shiftId) {
        if (shiftId == null || shiftId <= 0) {
            throw new IllegalArgumentException("Shift ID debe ser un número válido.");
        }
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByShiftId(shiftId);
        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
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
        if (schedule.getShift() != null) existing.setShift(schedule.getShift());
        existing.setStartDate(schedule.getStartDate());
        existing.setEndDate(schedule.getEndDate());
        existing.setUpdatedAt(new Date());

        return employeeScheduleRepository.save(existing);
    }

    @Transactional
    public List<EmployeeSchedule> createMultipleSchedules(List<EmployeeSchedule> schedules) {
        List<EmployeeSchedule> savedSchedules = new ArrayList<>();
        Long commonDaysParentId = null;

        for (EmployeeSchedule schedule : schedules) {
            validateSchedule(schedule);
            schedule.setCreatedAt(new Date());

            Shifts shift = shiftsRepository.findById(schedule.getShift().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado"));
            schedule.setShift(shift);

            generateScheduleDays(schedule);

            EmployeeSchedule savedSchedule = employeeScheduleRepository.save(schedule);

            List<EmployeeScheduleDay> savedDays = new ArrayList<>();
            for (EmployeeScheduleDay day : savedSchedule.getDays()) {
                day.setEmployeeSchedule(savedSchedule);
                if (commonDaysParentId == null) commonDaysParentId = savedSchedule.getId();
                day.setDaysParentId(commonDaysParentId);
                EmployeeScheduleDay savedDay = employeeScheduleDayRepository.save(day);
                savedDays.add(savedDay);
            }

            savedSchedule.setDays(savedDays);
            savedSchedule.setDaysParentId(commonDaysParentId);
            employeeScheduleRepository.save(savedSchedule);

            savedSchedules.add(savedSchedule);
        }

        return savedSchedules;
    }

    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(List<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 1) Sincroniza estados de grupos UNA sola vez (best effort)
        try {
            groupService.syncAllGroupStatuses();
        } catch (Exception e) {
            System.err.println("syncAllGroupStatuses() error: " + e.getMessage());
        }

        // 2) Carga schedules (con días, sin timeBlocks) para todos los empleados solicitados
        List<EmployeeSchedule> schedules =
                employeeScheduleRepository.findByEmployeeIdInWithDays(employeeIds);

        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        // 3) Obtiene los grupos por empleado en un solo paso
        Map<Long, List<ScheduleAssignmentGroupDTO>> groupsByEmployee = new HashMap<>();
        for (Long employeeId : new HashSet<>(employeeIds)) {
            try {
                List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId);
                groupsByEmployee.put(employeeId, (groups != null) ? groups : Collections.emptyList());
            } catch (Exception e) {
                System.err.println("getEmployeeGroups(" + employeeId + ") error: " + e.getMessage());
                groupsByEmployee.put(employeeId, Collections.emptyList());
            }
        }

        // 4) Empleados que SÍ tienen grupos (cualquier estado)
        Set<Long> employeesWithGroups = groupsByEmployee.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // 5) Schedules pertenecientes a grupos ACTIVE
        Set<Long> activeScheduleIds = groupsByEmployee.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .flatMap(g -> {
                    List<Long> ids = g.getEmployeeScheduleIds();
                    return (ids != null) ? ids.stream() : Stream.<Long>empty();
                })
                .collect(Collectors.toSet());

        // 6) Regla de filtrado:
        //    - Si el empleado NO tiene grupos -> NO filtrar (dejar sus schedules).
        //    - Si el empleado SÍ tiene grupos -> dejar solo schedules en grupos ACTIVE.
        schedules = schedules.stream()
                .filter(s -> !employeesWithGroups.contains(s.getEmployeeId())
                        || activeScheduleIds.contains(s.getId()))
                .collect(Collectors.toList());

        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        // 7) Cargar timeBlocks en batch y asociarlos a sus días
        List<Long> scheduleIds = schedules.stream()
                .map(EmployeeSchedule::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!scheduleIds.isEmpty()) {
            List<EmployeeScheduleDay> daysWithBlocks =
                    employeeScheduleRepository.findDaysWithTimeBlocksByScheduleIds(scheduleIds);

            Map<Long, List<EmployeeScheduleDay>> daysByScheduleId = daysWithBlocks.stream()
                    .collect(Collectors.groupingBy(
                            day -> day.getEmployeeSchedule().getId(),
                            Collectors.toList()
                    ));

            for (EmployeeSchedule schedule : schedules) {
                List<EmployeeScheduleDay> days = daysByScheduleId.get(schedule.getId());
                if (days != null) {
                    // Reemplaza los días para incluir bloque horarios cargados
                    schedule.getDays().clear();
                    schedule.getDays().addAll(days);
                }
            }
        }

        // 8) Mapear a DTO completo
        return schedules.stream()
                .map(this::convertToCompleteDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeScheduleDTO> getSchedulesByDateRange(Date startDate, Date endDate) {
        if (startDate == null) throw new IllegalArgumentException("La fecha de inicio es obligatoria.");

        List<EmployeeSchedule> schedules;
        if (endDate == null) {
            schedules = employeeScheduleRepository.findByStartDateAndNullEndDate(startDate);
        } else {
            if (startDate.after(endDate)) throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
            schedules = employeeScheduleRepository.findByDateRange(startDate, endDate);
        }

        return schedules.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public void recalculateGroupTotalsForEmployee(Long employeeId) {
        try {
            List<ScheduleAssignmentGroupDTO> activeGroups = groupService.getEmployeeGroups(employeeId).stream()
                    .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                    .collect(Collectors.toList());

            for (ScheduleAssignmentGroupDTO group : activeGroups) {
                try { groupService.recalculateGroup(group.getId()); }
                catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    @Transactional
    public void cleanupEmptyDaysForEmployee(Long employeeId) {
        try {
            List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);
            int daysDeleted = 0;

            for (EmployeeSchedule schedule : schedules) {
                if (schedule.getDays() == null) continue;

                List<EmployeeScheduleDay> emptyDays = schedule.getDays().stream()
                        .filter(day -> day.getTimeBlocks() == null || day.getTimeBlocks().isEmpty())
                        .collect(Collectors.toList());

                for (EmployeeScheduleDay emptyDay : emptyDays) {
                    try { employeeScheduleDayRepository.deleteById(emptyDay.getId()); daysDeleted++; }
                    catch (Exception ignored) { }
                }
            }

            if (daysDeleted > 0) {
                try { recalculateGroupTotalsForEmployee(employeeId); }
                catch (Exception ignored) { }
            }

        } catch (Exception ignored) { }
    }

    // =================== DTO MAPPING ===================

    private Map<String, Object> buildDaysStructure(EmployeeSchedule schedule) {
        Map<String, Object> daysMap = new LinkedHashMap<>();
        daysMap.put("id", schedule.getDaysParentId());

        List<EmployeeScheduleDay> sortedDays = schedule.getDays() != null
                ? schedule.getDays().stream().sorted(Comparator.comparing(EmployeeScheduleDay::getDate)).collect(Collectors.toList())
                : new ArrayList<>();

        List<Map<String, Object>> dayItems = sortedDays.stream().map(day -> {
            Map<String, Object> dayMap = new LinkedHashMap<>();
            dayMap.put("id", day.getId());
            dayMap.put("date", toLocalDate(day.getDate()).format(DATE_FMT));
            dayMap.put("dayOfWeek", day.getDayOfWeek());

            List<Map<String, String>> timeBlocks = day.getTimeBlocks() != null
                    ? day.getTimeBlocks().stream()
                    .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                    .map(block -> {
                        Map<String, String> blockMap = new LinkedHashMap<>();
                        blockMap.put("id", block.getId().toString());
                        blockMap.put("startTime", block.getStartTime().toString());
                        blockMap.put("endTime", block.getEndTime().toString());
                        return blockMap;
                    }).collect(Collectors.toList())
                    : new ArrayList<>();

            dayMap.put("timeBlocks", timeBlocks);
            return dayMap;
        }).collect(Collectors.toList());

        daysMap.put("items", dayItems);
        return daysMap;
    }

    private ShiftsDTO buildShiftDTO(Shifts shift) {
        if (shift == null) return null;
        Long timeBreak = null; // si luego expones getTimeBreak(), úsalo aquí
        return new ShiftsDTO(
                shift.getId(),
                shift.getName(),
                shift.getDescription(),
                timeBreak,
                Collections.emptyList()
        );
    }

    private EmployeeScheduleDTO convertToDTO(EmployeeSchedule schedule) {
        EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        ShiftsDTO shiftDTO = buildShiftDTO(schedule.getShift());

        // NO cargar días para búsqueda inicial
        Map<String, Object> daysStructure = new LinkedHashMap<>();
        daysStructure.put("id", schedule.getDaysParentId());
        daysStructure.put("items", new ArrayList<>());

        return new EmployeeScheduleDTO(
                schedule.getId(),
                getEmployeeField(employee, EmployeeResponse.Employee::getNumberId),
                getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondName, ""),
                getEmployeeField(employee, EmployeeResponse.Employee::getSurName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondSurname, ""),
                getEmployeeDependency(employee),
                getEmployeePosition(employee),
                formatDate(schedule.getStartDate()),  // <- getStartDate()
                formatDate(schedule.getEndDate()),    // <- getEndDate()
                shiftDTO,
                schedule.getDaysParentId(),
                daysStructure
        );
    }
    private EmployeeScheduleDTO convertToCompleteDTO(EmployeeSchedule schedule) {
        EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        Map<String, Object> daysStructure = buildDaysStructure(schedule);

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

    private String formatDate(Date date) {
        return date != null ? toLocalDate(date).format(DATE_FMT) : null;
    }

    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter) {
        return employee != null ? getter.apply(employee) : null;
    }

    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter,
                                   T defaultValue) {
        try { return employee != null ? getter.apply(employee) : defaultValue; }
        catch (NullPointerException e) { return defaultValue; }
    }

    private String getEmployeeDependency(EmployeeResponse.Employee employee) {
        return getEmployeeField(employee, e -> e.getPosition().getDependency().getName(), "Sin dependencia");
    }

    private String getEmployeePosition(EmployeeResponse.Employee employee) {
        return getEmployeeField(employee, e -> e.getPosition().getName(), "Sin posición");
    }

    // =================== TIMEBLOCK: UPDATE ===================

    public EmployeeScheduleTimeBlock updateTimeBlock(TimeBlockDTO timeBlockDTO) {
        validateInputParameters(timeBlockDTO);

        EmployeeScheduleTimeBlock existingTimeBlock = employeeScheduleTimeBlockRepository
                .findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Time block not found with id: " + timeBlockDTO.getId()));

        validateDayAndParentDay(existingTimeBlock, timeBlockDTO);
        validateEmployeePermissions(existingTimeBlock, timeBlockDTO);

        existingTimeBlock.setStartTime(Time.valueOf(normalizeTimeForDatabase(timeBlockDTO.getStartTime())));
        existingTimeBlock.setEndTime(Time.valueOf(normalizeTimeForDatabase(timeBlockDTO.getEndTime())));
        existingTimeBlock.setUpdatedAt(new Date());

        EmployeeScheduleTimeBlock result = employeeScheduleTimeBlockRepository.save(existingTimeBlock);

        Long employeeId = existingTimeBlock.getEmployeeScheduleDay().getEmployeeSchedule().getEmployeeId();
        recalculateGroupTotalsForEmployee(employeeId);

        return result;
    }


    private void validateInputParameters(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO == null) throw new IllegalArgumentException("Time block data cannot be null");
        if (timeBlockDTO.getStartTime() == null || timeBlockDTO.getEndTime() == null)
            throw new IllegalArgumentException("Start time and end time must be provided");
        if (timeBlockDTO.getEmployeeScheduleDayId() == null)
            throw new IllegalArgumentException("Employee schedule day ID must be specified");
    }

    private void validateDayAndParentDay(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        EmployeeScheduleDay currentDay = existingTimeBlock.getEmployeeScheduleDay();
        if (!currentDay.getId().equals(timeBlockDTO.getEmployeeScheduleDayId())) {
            throw new IllegalArgumentException("Time block does not belong to the specified day");
        }
    }

    private void validateEmployeePermissions(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        if (existingTimeBlock.getEmployeeScheduleDay().getEmployeeSchedule() == null) {
            throw new IllegalArgumentException("No employee schedule found for this time block");
        }

    }

    private void validateSchedule(EmployeeSchedule schedule) {
        if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0)
            throw new IllegalArgumentException("Employee ID es obligatorio y debe ser un número válido.");
        if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0)
            throw new IllegalArgumentException("Shift ID es obligatorio y debe ser un número válido.");
        if (schedule.getStartDate() == null)
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        if (schedule.getEndDate() != null && schedule.getStartDate().after(schedule.getEndDate()))
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
    }
}

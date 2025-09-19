package sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.ScheduleCalculationService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayExemptionService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayProcessingService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.time.ScheduleDayGeneratorService;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleAssignmentService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final ShiftsRepository shiftsRepository;
    private final ScheduleValidationService scheduleValidationService;
    private final HolidayProcessingService holidayProcessingService;
    private final ScheduleDayGeneratorService scheduleDayGeneratorService;
    private final ScheduleAssignmentGroupService groupService;
    private final HolidayExemptionService holidayExemptionService;
    private final ScheduleCalculationService scheduleCalculationService;

    public ScheduleAssignmentService(
            EmployeeScheduleRepository employeeScheduleRepository,
            EmployeeScheduleDayRepository employeeScheduleDayRepository,
            ShiftsRepository shiftsRepository,
            ScheduleValidationService scheduleValidationService,
            HolidayProcessingService holidayProcessingService,
            ScheduleDayGeneratorService scheduleDayGeneratorService,
            ScheduleAssignmentGroupService groupService,
            HolidayExemptionService holidayExemptionService,
            ScheduleCalculationService scheduleCalculationService
    ) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.shiftsRepository = shiftsRepository;
        this.scheduleValidationService = scheduleValidationService;
        this.holidayProcessingService = holidayProcessingService;
        this.scheduleDayGeneratorService = scheduleDayGeneratorService;
        this.groupService = groupService;
        this.holidayExemptionService = holidayExemptionService;
        this.scheduleCalculationService = scheduleCalculationService;
    }

    @Transactional
    public AssignmentResult processMultipleAssignments(AssignmentRequest request) {
        try {
            System.out.println("=== PROCESS MULTIPLE ASSIGNMENTS START ===");

            scheduleValidationService.validateAssignmentRequest(request);
            System.out.println("✅ Validation passed");

            // COMENTAR TEMPORALMENTE ESTA VALIDACIÓN DE CONFLICTOS
            // PERMITE ASIGNAR EL MISMO TURNO A DIFERENTES EMPLEADOS
        /*
        List<ScheduleConflict> conflicts = scheduleValidationService.detectScheduleConflicts(request.getAssignments());
        if (!conflicts.isEmpty()) throw new ConflictException("Conflictos de horarios detectados", conflicts);
        System.out.println("✅ No conflicts detected");
        */
            System.out.println("✅ Conflict validation skipped temporarily - allowing same shift for different employees");

            List<HolidayWarning> holidayWarnings = holidayProcessingService.detectHolidayWarnings(request.getAssignments());
            if (!holidayWarnings.isEmpty()) {
                System.out.println("⚠️ Holiday warnings detected, returning confirmation request");
                AssignmentResult preview = new AssignmentResult();
                preview.setSuccess(false);
                preview.setMessage("Se detectaron días festivos");
                preview.setHolidayWarnings(holidayWarnings);
                preview.setRequiresConfirmation(true);
                return preview;
            }
            System.out.println("✅ No holiday warnings");

            List<EmployeeSchedule> created = new ArrayList<>();
            for (int i = 0; i < request.getAssignments().size(); i++) {
                ScheduleAssignment a = request.getAssignments().get(i);
                System.out.println("📝 Processing assignment " + (i+1) + ": " + a);

                try {
                    EmployeeSchedule s = createScheduleFromAssignment(a);
                    System.out.println("✅ Schedule created: " + s.getId());

                    s.setDays(new ArrayList<>());
                    EmployeeSchedule saved = employeeScheduleRepository.save(s);
                    System.out.println("✅ Schedule saved with ID: " + saved.getId());

                    System.out.println("🔄 Generating schedule days...");
                    scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, Collections.emptyList());
                    System.out.println("✅ Schedule days generated");

                    EmployeeSchedule finalSaved = employeeScheduleRepository.save(saved);
                    System.out.println("✅ Final save completed for schedule: " + finalSaved.getId());

                    created.add(finalSaved);

                } catch (Exception e) {
                    System.err.println("❌ Error processing assignment " + (i+1) + ": " + e.getClass().getName());
                    System.err.println("❌ Error message: " + e.getMessage());
                    e.printStackTrace();
                    throw e; // Re-lanzar para que se maneje en el nivel superior
                }
            }

            employeeScheduleRepository.flush();
            System.out.println("✅ Repository flushed");

            System.out.println("🔄 Processing created schedules...");
            AssignmentResult result = processCreatedSchedules(created);
            System.out.println("✅ Processing completed");

            System.out.println("=== PROCESS MULTIPLE ASSIGNMENTS END ===");
            return result;

        } catch (Exception e) {
            System.err.println("❌ FATAL ERROR in processMultipleAssignments: " + e.getClass().getName());
            System.err.println("❌ FATAL ERROR message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    @Transactional
    public AssignmentResult processHolidayAssignment(HolidayConfirmationRequest request) {
        try {
            System.out.println("=== PROCESS HOLIDAY ASSIGNMENT START ===");
            System.out.println("Request: " + request);

            if (request == null || request.getConfirmedAssignments() == null || request.getConfirmedAssignments().isEmpty()) {
                throw new IllegalArgumentException("confirmedAssignments es requerido");
            }

            List<EmployeeSchedule> created = new ArrayList<>();
            for (int i = 0; i < request.getConfirmedAssignments().size(); i++) {
                ConfirmedAssignment ca = request.getConfirmedAssignments().get(i);
                System.out.println("Processing confirmed assignment " + (i+1) + ": " + ca);

                try {
                    shiftsRepository.findById(ca.getShiftId())
                            .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + ca.getShiftId()));

                    EmployeeSchedule s = createScheduleFromConfirmedAssignment(ca);
                    s.setDays(new ArrayList<>());
                    EmployeeSchedule saved = employeeScheduleRepository.save(s);

                    List<HolidayDecision> decisions = (ca.getHolidayDecisions() != null) ? ca.getHolidayDecisions() : Collections.emptyList();
                    System.out.println("Holiday decisions for assignment " + (i+1) + ": " + decisions.size() + " decisions");

                    scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, decisions);
                    created.add(employeeScheduleRepository.save(saved));

                } catch (Exception e) {
                    System.err.println("Error processing confirmed assignment " + (i+1) + ": " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            }

            System.out.println("=== PROCESS HOLIDAY ASSIGNMENT END ===");
            return processCreatedSchedules(created);

        } catch (Exception e) {
            System.err.println("FATAL ERROR in processHolidayAssignment: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public ValidationResult validateAssignmentOnly(AssignmentRequest request) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        try {
            scheduleValidationService.validateAssignmentRequest(request);

            List<ScheduleConflict> conflicts = scheduleValidationService.detectScheduleConflicts(request.getAssignments());
            if (!conflicts.isEmpty()) errors.add("Se detectaron conflictos de horarios");

            List<HolidayWarning> holidayWarnings = holidayProcessingService.detectHolidayWarnings(request.getAssignments());
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

    @Transactional
    public List<EmployeeSchedule> createMultipleSchedules(List<EmployeeSchedule> schedules) {
        List<EmployeeSchedule> savedSchedules = new ArrayList<>();
        Long commonDaysParentId = null;

        for (EmployeeSchedule schedule : schedules) {
            validateSchedule(schedule);
            schedule.setCreatedAt(new Date());

            Shifts shift = shiftsRepository.findById(schedule.getShift().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));
            schedule.setShift(shift);

            scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(schedule, Collections.emptyList());

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

    // =================== MÉTODOS PRIVADOS ===================

    private AssignmentResult processCreatedSchedules(List<EmployeeSchedule> created) {
        try {
            System.out.println("=== PROCESS CREATED SCHEDULES START ===");
            System.out.println("Created schedules count: " + created.size());

            Map<Long, List<Long>> idsPorEmpleado = created.stream()
                    .collect(Collectors.groupingBy(EmployeeSchedule::getEmployeeId,
                            Collectors.mapping(EmployeeSchedule::getId, Collectors.toList())));

            System.out.println("Employee groups: " + idsPorEmpleado);

            idsPorEmpleado.forEach((empId, scheduleIds) -> {
                try {
                    System.out.println("Processing group service for employee: " + empId + " with schedules: " + scheduleIds);
                    groupService.processScheduleAssignment(empId, scheduleIds);
                    System.out.println("Group service processed successfully for employee: " + empId);

                    try {
                        System.out.println("Backfilling group IDs for employee: " + empId);
                        holidayExemptionService.backfillGroupIds(empId, groupService.getEmployeeGroups(empId));
                        System.out.println("Backfill completed for employee: " + empId);
                    } catch (Exception e) {
                        System.err.println("No se pudo enlazar exenciones a grupos para empId " + empId + ": " + e.getMessage());
                        e.printStackTrace(); // Agregar stack trace para este error también
                    }
                } catch (Exception e) {
                    System.err.println("Error processing group for employee " + empId + ": " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Error processing group for employee " + empId, e);
                }
            });

            System.out.println("Calculating employee summaries...");
            List<Object> summaries = Collections.singletonList(idsPorEmpleado.keySet().stream()
                    .map(empId -> {
                        try {
                            System.out.println("Calculating summary for employee: " + empId);
                            EmployeeHoursSummaryDTO summary = scheduleCalculationService.calculateEmployeeHoursSummary(empId);
                            System.out.println("Summary calculated for employee: " + empId);
                            return summary;
                        } catch (Exception ex) {
                            System.err.println("Error calculating summary for employee " + empId + ": " + ex.getMessage());
                            ex.printStackTrace();
                            return createEmptySummary(empId);
                        }
                    }).collect(Collectors.toList()));

            System.out.println("Creating result object...");
            AssignmentResult result = new AssignmentResult();
            result.setSuccess(true);
            result.setMessage("Turnos asignados correctamente a múltiples empleados");
            result.setUpdatedEmployees(summaries);
            result.setRequiresConfirmation(false);

            System.out.println("=== PROCESS CREATED SCHEDULES END ===");
            return result;

        } catch (Exception e) {
            System.err.println("FATAL ERROR in processCreatedSchedules: " + e.getClass().getName());
            System.err.println("FATAL ERROR message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }


    private EmployeeSchedule createScheduleFromAssignment(ScheduleDto.ScheduleAssignment assignment) {
        // ===== 1) Validaciones tempranas (con mensajes explícitos) =====
        if (assignment == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es requerido");
        }
        if (assignment.getEmployeeId() == null || assignment.getEmployeeId() <= 0) {
            throw new IllegalArgumentException("Employee ID requerido y debe ser > 0");
        }
        if (assignment.getShiftId() == null || assignment.getShiftId() <= 0) {
            throw new IllegalArgumentException("Shift ID requerido y debe ser > 0");
        }
        if (assignment.getStartDate() == null) {
            throw new IllegalArgumentException("Fecha de inicio requerida");
        }
        if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(assignment.getStartDate())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        // ===== 2) Carga de entidades con mensajes claros =====
        Shifts shift = getShiftOrThrow(assignment.getShiftId());

        // ===== 3) Construcción del schedule =====
        EmployeeSchedule schedule = new EmployeeSchedule();
        schedule.setEmployeeId(assignment.getEmployeeId());
        schedule.setShift(shift);
        schedule.setStartDate(java.sql.Date.valueOf(assignment.getStartDate()));
        schedule.setEndDate(
                assignment.getEndDate() != null ? java.sql.Date.valueOf(assignment.getEndDate()) : null
        );

        // Auditoría (si no usas @PrePersist)
        if (schedule.getCreatedAt() == null) {
            // “día actual” a las 00:00 locales
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.Instant todayAt00 = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant();
            schedule.setCreatedAt(java.util.Date.from(todayAt00));
        }
        schedule.setUpdatedAt(new java.util.Date());

        // ===== 4) Validaciones de dominio (tu método existente) =====
        validateSchedule(schedule);

        return schedule;
    }

    private Shifts getShiftOrThrow(Long shiftId) {
        return shiftsRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + shiftId));
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

    private EmployeeHoursSummaryDTO createEmptySummary(Long empId) {
        var empty = new EmployeeHoursSummaryDTO();
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

    private void validateSchedule(EmployeeSchedule schedule) {
        List<String> errors = new ArrayList<>();

        if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0) {
            errors.add("Employee ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0) {
            errors.add("Shift ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getStartDate() == null) {
            errors.add("La fecha de inicio es obligatoria.");
        }
        if (schedule.getEndDate() != null && schedule.getStartDate() != null && schedule.getStartDate().after(schedule.getEndDate())) {
            errors.add("La fecha de inicio debe ser anterior a la fecha de fin.");
        }

        if (!errors.isEmpty()) {
            throw new ScheduleDto.ValidationException("Errores de validación", errors);
        }
    }

}
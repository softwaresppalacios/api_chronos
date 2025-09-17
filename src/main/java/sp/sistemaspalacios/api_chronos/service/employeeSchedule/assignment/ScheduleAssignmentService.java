package sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
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
        scheduleValidationService.validateAssignmentRequest(request);

        List<ScheduleConflict> conflicts = scheduleValidationService.detectScheduleConflicts(request.getAssignments());
        if (!conflicts.isEmpty()) throw new ConflictException("Conflictos de horarios detectados", conflicts);

        List<HolidayWarning> holidayWarnings = holidayProcessingService.detectHolidayWarnings(request.getAssignments());
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
            EmployeeSchedule s = createScheduleFromAssignment(a);
            s.setDays(new ArrayList<>());
            EmployeeSchedule saved = employeeScheduleRepository.save(s);
            scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, Collections.emptyList());
            created.add(employeeScheduleRepository.save(saved));
        }
        employeeScheduleRepository.flush();

        return processCreatedSchedules(created);
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
            List<HolidayDecision> decisions = (ca.getHolidayDecisions() != null) ? ca.getHolidayDecisions() : Collections.emptyList();
            scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, decisions);
            created.add(employeeScheduleRepository.save(saved));
        }

        return processCreatedSchedules(created);
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

        List<Object> summaries = Collections.singletonList(idsPorEmpleado.keySet().stream()
                .map(empId -> {
                    try {
                        return scheduleCalculationService.calculateEmployeeHoursSummary(empId);
                    } catch (Exception ex) {
                        return createEmptySummary(empId);
                    }
                }).collect(Collectors.toList()).reversed());

        AssignmentResult result = new AssignmentResult();
        result.setSuccess(true);
        result.setMessage("Turnos asignados correctamente");
        result.setUpdatedEmployees(summaries);
        result.setRequiresConfirmation(false);
        return result;
    }

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
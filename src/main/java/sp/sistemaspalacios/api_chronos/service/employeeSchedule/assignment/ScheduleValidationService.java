package sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;
import sp.sistemaspalacios.api_chronos.service.common.TimeService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleValidationService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final HolidayService holidayService;
    private final TimeService timeService;

    public ScheduleValidationService(
            EmployeeScheduleRepository employeeScheduleRepository,
            ShiftsRepository shiftsRepository,
            HolidayService holidayService,
            TimeService timeService) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.holidayService = holidayService;
        this.timeService = timeService;
    }

    public void validateAssignmentRequest(AssignmentRequest request) {
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
        if (!errors.isEmpty()) {
            throw new ValidationException("Errores de validación", errors);
        }
    }

    public List<ScheduleConflict> detectScheduleConflicts(List<ScheduleAssignment> assignments) {
        List<ScheduleConflict> conflicts = new ArrayList<>();

        Map<Long, List<ScheduleAssignment>> byEmp = assignments.stream()
                .collect(Collectors.groupingBy(ScheduleAssignment::getEmployeeId));

        for (Map.Entry<Long, List<ScheduleAssignment>> entry : byEmp.entrySet()) {
            Long employeeId = entry.getKey();
            List<ScheduleAssignment> empAssignments = entry.getValue();

            List<EmployeeSchedule> existing = employeeScheduleRepository.findByEmployeeId(employeeId);

            for (ScheduleAssignment na : empAssignments) {
                for (EmployeeSchedule ex : existing) {
                    ScheduleConflict c = checkForConflictWithTimeOverlap(na, ex);
                    if (c != null) conflicts.add(c);
                }
            }

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
        LocalDate existingStart = existing.getStartDate();
        LocalDate existingEnd = (existing.getEndDate() != null) ? existing.getEndDate() : existingStart;

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
                        e.stream().anyMatch(d2 -> timeService.timeOverlaps(d1.getStartTime(), d1.getEndTime(), d2.getStartTime(), d2.getEndTime()))
                );
                if (clash) {
                    return createConflict(assignment, date, "Conflicto de horarios - solapamiento con turno " + existingShift.getName());
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

        for (LocalDate d = Collections.max(Arrays.asList(s1, s2)); !d.isAfter(Collections.min(Arrays.asList(e1, e2))); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue();
            List<ShiftDetail> d1 = detailsForDay(sh1, dow);
            List<ShiftDetail> d2 = detailsForDay(sh2, dow);

            if (!d1.isEmpty() && !d2.isEmpty()) {
                boolean clash = d1.stream().anyMatch(x ->
                        d2.stream().anyMatch(y -> timeService.timeOverlaps(x.getStartTime(), x.getEndTime(), y.getStartTime(), y.getEndTime()))
                );
                if (clash) {
                    ScheduleConflict c = new ScheduleConflict();
                    c.setEmployeeId(a1.getEmployeeId());
                    c.setConflictDate(d);
                    c.setMessage("Conflicto - turnos se solapan en horario");
                    return c;
                }
            }
        }
        return null;
    }

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

    private boolean datesOverlap(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }

    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(String message, List<String> errors) {
            super(message);
            this.errors = errors;
        }
        public List<String> getErrors() {
            return errors;
        }

    }
}
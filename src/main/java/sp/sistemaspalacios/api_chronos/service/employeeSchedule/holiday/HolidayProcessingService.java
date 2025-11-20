package sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;
import sp.sistemaspalacios.api_chronos.service.common.TimeService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeDataService;

import java.time.LocalDate;
import java.util.*;

@Service
public class HolidayProcessingService {

    private final ShiftsRepository shiftsRepository;
    private final HolidayService holidayService;
    private final EmployeeDataService employeeDataService;
    private final TimeService timeService;

    public HolidayProcessingService(ShiftsRepository shiftsRepository,
                                    HolidayService holidayService,
                                    EmployeeDataService employeeDataService,
                                    TimeService timeService) {
        this.shiftsRepository = shiftsRepository;
        this.holidayService = holidayService;
        this.employeeDataService = employeeDataService;
        this.timeService = timeService;
    }


    private List<ShiftSegmentDetail> calculateShiftSegmentsForDay(Shifts shift, LocalDate date) {
        List<ShiftSegmentDetail> segments = new ArrayList<>();
        int dow = date.getDayOfWeek().getValue();

        List<ShiftDetail> dayDetails = shift.getShiftDetails().stream()
                .filter(detail -> detail.getDayOfWeek() != null && detail.getDayOfWeek().equals(dow))
                .filter(detail -> detail.getStartTime() != null && detail.getEndTime() != null)
                .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);

        for (ShiftDetail detail : dayDetails) {
            ShiftSegmentDetail segment = new ShiftSegmentDetail();
            segment.setSegmentName(determineSegmentName(detail.getStartTime()));
            segment.setStartTime(timeService.normalizeTimeForDatabase(detail.getStartTime()));
            segment.setEndTime(timeService.normalizeTimeForDatabase(detail.getEndTime()));

            if (detail.getBreakStartTime() != null) segment.setBreakStartTime(timeService.normalizeTimeForDatabase(detail.getBreakStartTime()));
            if (detail.getBreakEndTime() != null) segment.setBreakEndTime(timeService.normalizeTimeForDatabase(detail.getBreakEndTime()));
            segment.setBreakMinutes(detail.getBreakMinutes());

            double workingHours = timeService.calculateHoursBetween(segment.getStartTime(), segment.getEndTime());
            segment.setWorkingHours(workingHours);

            double breakHours = (segment.getBreakMinutes() != null) ? segment.getBreakMinutes() / 60.0 : 0.0;
            segment.setBreakHours(breakHours);
            segment.setEffectiveHours(Math.max(0.0, workingHours - breakHours));

            segments.add(segment);
        }
        return segments;
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









































    public List<HolidayWarning> detectHolidayWarnings(List<ScheduleAssignment> assignments) {
        List<HolidayWarning> warnings = new ArrayList<>();

        for (ScheduleAssignment assignment : assignments) {
            LocalDate start = assignment.getStartDate();
            LocalDate end = (assignment.getEndDate() != null) ? assignment.getEndDate() : start;

            Shifts shift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
            if (shift == null || shift.getShiftDetails() == null) continue;

            String employeeName = employeeDataService.getEmployeeName(assignment.getEmployeeId());

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (holidayService.isHoliday(d)) {

                    // ✅ NUEVA VALIDACIÓN: Solo crear warning si el turno trabaja este día
                    if (shiftWorksOnDay(shift, d)) {
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
        }
        return warnings;
    }

    // AGREGAR ESTE MÉTODO NUEVO
    private boolean shiftWorksOnDay(Shifts shift, LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday

        return shift.getShiftDetails().stream()
                .anyMatch(detail ->
                        Objects.equals(detail.getDayOfWeek(), dayOfWeek) &&
                                detail.getStartTime() != null &&
                                detail.getEndTime() != null &&
                                !detail.getStartTime().trim().isEmpty() &&
                                !detail.getEndTime().trim().isEmpty()
                );
    }
















}
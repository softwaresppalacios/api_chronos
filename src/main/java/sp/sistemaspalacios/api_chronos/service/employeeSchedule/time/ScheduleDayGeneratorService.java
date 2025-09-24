package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;
import sp.sistemaspalacios.api_chronos.service.common.TimeService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayExemptionService;

import java.sql.Time;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;               // <-- IMPORT NECESARIO
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleDayGeneratorService {

    private final HolidayService holidayService;
    private final HolidayExemptionService holidayExemptionService;
    private final TimeService timeService;

    public ScheduleDayGeneratorService(HolidayService holidayService,
                                       HolidayExemptionService holidayExemptionService,
                                       TimeService timeService) {
        this.holidayService = holidayService;
        this.holidayExemptionService = holidayExemptionService;
        this.timeService = timeService;
    }

    public void generateScheduleDaysWithHolidayDecisions(EmployeeSchedule schedule, List<ScheduleDto.HolidayDecision> holidayDecisions) {
        if (schedule.getDays() == null) schedule.setDays(new ArrayList<>());
        else schedule.getDays().clear();
        LocalDate startDate = schedule.getStartDate();
        LocalDate endDate   = (schedule.getEndDate() != null) ? schedule.getEndDate() : startDate;

        if (startDate == null) throw new IllegalStateException("StartDate es requerido");
        if (endDate == null) endDate = startDate;

        List<ShiftDetail> details = (schedule.getShift() != null && schedule.getShift().getShiftDetails() != null)
                ? schedule.getShift().getShiftDetails()
                : Collections.emptyList();

        Map<LocalDate, ScheduleDto.HolidayDecision> decisionMap =
                (holidayDecisions != null ? holidayDecisions : Collections.<ScheduleDto.HolidayDecision>emptyList())
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(h -> h.getHolidayDate() != null)
                        .collect(Collectors.toMap(ScheduleDto.HolidayDecision::getHolidayDate, h -> h, (a, b) -> a));

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            ScheduleDto.HolidayDecision decision = decisionMap.get(d);

            // PASO 1: exenciones
            if (decision != null) {
                try {
                    if (decision.getExemptionReason() != null && !decision.getExemptionReason().isBlank()) {
                        holidayExemptionService.saveExemption(
                                schedule.getEmployeeId(), d, holidayService.getHolidayName(d),
                                decision.getExemptionReason(), null
                        );
                    } else if (!decision.isApplyHolidayCharge()) {
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

            // PASO 2: ¿se crea el día?
            boolean skipDayCreation = decision != null &&
                    decision.getExemptionReason() != null &&
                    !decision.getExemptionReason().isBlank() &&
                    !decision.isApplyHolidayCharge();

            if (skipDayCreation) continue;

            // PASO 3: crear día
            EmployeeScheduleDay day = new EmployeeScheduleDay();
            day.setEmployeeSchedule(schedule);
            day.setDate(java.sql.Date.valueOf(d));            // ← LocalDate -> java.sql.Date
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
                            String s = stringOf(seg.get("startTime"));
                            String e = stringOf(seg.get("endTime"));
                            if (!isBlank(s)) finalStartTime = s;
                            if (!isBlank(e)) finalEndTime = e;
                            break;
                        }
                    }
                }

                String sStr = normalizeTimeForDatabase(finalStartTime);
                String eStr = normalizeTimeForDatabase(finalEndTime);

                Time startTime = safeTimeValueOf(sStr, "08:00:00");
                Time endTime   = safeTimeValueOf(eStr, "17:00:00");

                EmployeeScheduleTimeBlock tb = new EmployeeScheduleTimeBlock();
                tb.setEmployeeScheduleDay(day);
                tb.setStartTime(startTime);
                tb.setEndTime(endTime);
                tb.setCreatedAt(new Date());

                day.getTimeBlocks().add(tb);
            }

            schedule.getDays().add(day);
        }
    }

    // ==== Helpers ====

    private static LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Time safeTimeValueOf(String timeString, String defaultTime) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return Time.valueOf(defaultTime);
        }
        try {
            String normalizedTime = normalizeTimeString(timeString.trim());
            return Time.valueOf(normalizedTime);
        } catch (IllegalArgumentException e) {
            System.err.println("WARNING: Invalid time format '" + timeString + "', using default " + defaultTime);
            return Time.valueOf(defaultTime);
        }
    }

    private String normalizeTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return "00:00:00";
        timeStr = timeStr.trim();
        if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}")) return timeStr;
        if (timeStr.matches("\\d{2}:\\d{2}")) return timeStr + ":00";
        if (timeStr.matches("\\d{1}:\\d{2}")) return "0" + timeStr + ":00";
        return "00:00:00";
    }

    private static boolean equalsIgnoreCaseNoAccents(String a, String b){
        if (a == null || b == null) return false;
        String na = Normalizer.normalize(a, Normalizer.Form.NFD).replaceAll("\\p{M}","");
        String nb = Normalizer.normalize(b, Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return na.equalsIgnoreCase(nb);
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

    private static String stringOf(Object o){ return o==null? null : o.toString(); }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }
    private String normalizeTimeForDatabase(String time) {
        return timeService.normalizeTimeForDatabase(time);
    }
}

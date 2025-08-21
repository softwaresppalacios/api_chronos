package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleDetailDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.ScheduleAssignmentGroup;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.ScheduleAssignmentGroupRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAssignmentGroupService {

    private final ScheduleAssignmentGroupRepository groupRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final GeneralConfigurationService configService;
    private final HolidayService holidayService;
    private final OvertimeTypeService overtimeTypeService;

    @Value("${chronos.group.default-status:ACTIVE}")
    private String defaultGroupStatus;

    @Value("${chronos.shift.default-name:Sin nombre}")
    private String defaultShiftName;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    // ===== MÉTODOS PRINCIPALES =====

    @Transactional
    public ScheduleAssignmentGroupDTO processScheduleAssignment(Long employeeId, List<Long> scheduleIds) {
        validateInputs(employeeId, scheduleIds);

        List<EmployeeSchedule> newSchedules = getValidatedSchedules(scheduleIds);
        ScheduleAssignmentGroup group = findOrCreateGroup(employeeId, newSchedules, scheduleIds);

        List<EmployeeSchedule> allSchedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        HoursCalculation hoursCalc = calculateHours(allSchedules);

        updateGroupTotals(group, hoursCalc);
        group = groupRepository.save(group);

        return convertToDTO(group, newSchedules, hoursCalc);
    }

    public List<ScheduleAssignmentGroupDTO> getEmployeeGroups(Long employeeId) {
        List<ScheduleAssignmentGroup> groups = groupRepository.findByEmployeeId(employeeId);
        return groups.stream()
                .map(this::convertGroupToDTO)
                .collect(Collectors.toList());
    }

    public ScheduleAssignmentGroupDTO getLatestGroupForEmployee(Long employeeId) {
        return groupRepository.findByEmployeeId(employeeId).stream()
                .max(Comparator.comparing(ScheduleAssignmentGroup::getId))
                .map(this::convertGroupToDTO)
                .orElse(null);
    }

    public ScheduleAssignmentGroupDTO getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .map(this::convertGroupToDTO)
                .orElse(null);
    }

    @Transactional
    public ScheduleAssignmentGroupDTO recalculateGroup(Long groupId) {
        ScheduleAssignmentGroup group = getGroupOrThrow(groupId);
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());

        HoursCalculation hoursCalc = calculateHours(schedules);
        updateGroupTotals(group, hoursCalc);

        group = groupRepository.save(group);
        return convertToDTO(group, schedules, hoursCalc);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("Grupo no encontrado con ID: " + groupId);
        }
        groupRepository.deleteById(groupId);
    }

    // ===== MÉTODOS PRIVADOS =====

    private void validateInputs(Long employeeId, List<Long> scheduleIds) {
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID no puede ser nulo");
        }
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new IllegalArgumentException("Lista de schedule IDs no puede estar vacía");
        }
    }

    private List<EmployeeSchedule> getValidatedSchedules(List<Long> scheduleIds) {
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(scheduleIds);
        if (schedules.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron schedules con los IDs proporcionados");
        }
        return schedules;
    }

    private ScheduleAssignmentGroup getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado con ID: " + groupId));
    }

    private ScheduleAssignmentGroup findOrCreateGroup(Long employeeId, List<EmployeeSchedule> newSchedules, List<Long> scheduleIds) {
        DatePeriod period = DatePeriod.fromSchedules(newSchedules);

        Optional<ScheduleAssignmentGroup> existingGroup = findOverlappingGroup(employeeId, period);

        if (existingGroup.isPresent()) {
            return updateExistingGroup(existingGroup.get(), scheduleIds, period);
        } else {
            return createNewGroup(employeeId, scheduleIds, period);
        }
    }

    private Optional<ScheduleAssignmentGroup> findOverlappingGroup(Long employeeId, DatePeriod period) {
        return groupRepository.findByEmployeeId(employeeId).stream()
                .filter(group -> period.overlapsWith(group.getPeriodStart(), group.getPeriodEnd()))
                .findFirst();
    }

    private ScheduleAssignmentGroup updateExistingGroup(ScheduleAssignmentGroup group, List<Long> scheduleIds, DatePeriod period) {
        scheduleIds.forEach(id -> {
            if (!group.getEmployeeScheduleIds().contains(id)) {
                group.getEmployeeScheduleIds().add(id);
            }
        });

        if (period.getStartDate().before(group.getPeriodStart())) {
            group.setPeriodStart(period.getStartDate());
        }
        if (period.getEndDate().after(group.getPeriodEnd())) {
            group.setPeriodEnd(period.getEndDate());
        }

        return group;
    }

    private ScheduleAssignmentGroup createNewGroup(Long employeeId, List<Long> scheduleIds, DatePeriod period) {
        ScheduleAssignmentGroup group = new ScheduleAssignmentGroup();
        group.setEmployeeId(employeeId);
        group.setPeriodStart(period.getStartDate());
        group.setPeriodEnd(period.getEndDate());
        group.setEmployeeScheduleIds(new ArrayList<>(scheduleIds));
        group.setStatus(defaultGroupStatus);
        return group;
    }

    private HoursCalculation calculateHours(List<EmployeeSchedule> schedules) {
        Map<String, BigDecimal> hoursByType = initializeHoursMap();
        Set<LocalDate> holidayDates = getHolidayDates();

        BigDecimal weeklyLimit = getWeeklyHoursLimit();
        BigDecimal totalShiftHours = calculateTotalShiftHours(schedules);

        log.info("🔍 CÁLCULO - Límite: {}h, Total: {}h", weeklyLimit, totalShiftHours);

        if (totalShiftHours.compareTo(weeklyLimit) <= 0) {
            processScheduleDetails(schedules, hoursByType, holidayDates);
        } else {
            processExcessHours(hoursByType, weeklyLimit, totalShiftHours);
        }

        return new HoursCalculation(hoursByType, totalShiftHours);
    }

    private Map<String, BigDecimal> initializeHoursMap() {
        Map<String, BigDecimal> map = new HashMap<>();
        overtimeTypeService.getAllActiveTypes().forEach(type ->
                map.put(type.getCode(), BigDecimal.ZERO));
        return map;
    }

    private Set<LocalDate> getHolidayDates() {
        return holidayService.getAllHolidays().stream()
                .map(h -> h.getHolidayDate())
                .collect(Collectors.toSet());
    }

    private void processScheduleDetails(List<EmployeeSchedule> schedules, Map<String, BigDecimal> hoursMap, Set<LocalDate> holidayDates) {
        for (EmployeeSchedule schedule : schedules) {
            if (schedule.getShift() == null || schedule.getShift().getShiftDetails() == null) continue;

            DatePeriod schedulePeriod = DatePeriod.fromSchedule(schedule);

            for (LocalDate date = schedulePeriod.getStartLocalDate(); !date.isAfter(schedulePeriod.getEndLocalDate()); date = date.plusDays(1)) {
                processDateDetails(schedule.getShift(), date, hoursMap, holidayDates);
            }
        }
    }

    private void processDateDetails(Shifts shift, LocalDate date, Map<String, BigDecimal> hoursMap, Set<LocalDate> holidayDates) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        boolean isSunday = (dayOfWeek == 7);
        boolean isHoliday = holidayDates.contains(date);

        for (ShiftDetail detail : shift.getShiftDetails()) {
            if (!Objects.equals(detail.getDayOfWeek(), dayOfWeek) ||
                    detail.getStartTime() == null || detail.getEndTime() == null) continue;

            boolean isNightShift = TimeUtils.isNightTimeShift(detail.getStartTime(), detail.getEndTime());
            BigDecimal hours = TimeUtils.calculateDetailHours(detail);
            String overtimeCode = determineOvertimeCode(isHoliday, isSunday, isNightShift);

            addHoursToMap(hoursMap, overtimeCode, hours);

            log.debug("🕐 {}-{} {} = {} +{}h",
                    detail.getStartTime(), detail.getEndTime(),
                    TimeUtils.getDayName(dayOfWeek), overtimeCode, hours);
        }
    }

    private String determineOvertimeCode(boolean isHoliday, boolean isSunday, boolean isNightShift) {
        Set<String> availableCodes = overtimeTypeService.getAllActiveTypes().stream()
                .map(OvertimeTypeDTO::getCode)
                .collect(Collectors.toSet());

        if (isHoliday) {
            return (isNightShift && availableCodes.contains("FESTIVO_NOCTURNA"))
                    ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";
        }
        if (isSunday) {
            return (isNightShift && availableCodes.contains("DOMINICAL_NOCTURNA"))
                    ? "DOMINICAL_NOCTURNA" : "DOMINICAL_DIURNA";
        }
        return (isNightShift && availableCodes.contains("REGULAR_NOCTURNA"))
                ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA";
    }

    private void addHoursToMap(Map<String, BigDecimal> hoursMap, String overtimeCode, BigDecimal hours) {
        if (overtimeCode != null && hoursMap.containsKey(overtimeCode)) {
            hoursMap.put(overtimeCode, hoursMap.get(overtimeCode).add(hours));
        } else if (hoursMap.containsKey("REGULAR_DIURNA")) {
            hoursMap.put("REGULAR_DIURNA", hoursMap.get("REGULAR_DIURNA").add(hours));
        }
    }

    private BigDecimal calculateTotalShiftHours(List<EmployeeSchedule> schedules) {
        return schedules.stream()
                .filter(s -> s.getShift() != null)
                .map(s -> TimeUtils.calculateTotalShiftHours(s.getShift()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getWeeklyHoursLimit() {
        try {
            String value = configService.getByType("WEEKLY_HOURS").getValue();
            return TimeUtils.parseHourOrDecimal(value);
        } catch (Exception e) {
            return new BigDecimal("44.0"); // fallback
        }
    }

    private void processExcessHours(Map<String, BigDecimal> hoursMap, BigDecimal weeklyLimit, BigDecimal totalHours) {
        BigDecimal extraPart = totalHours.subtract(weeklyLimit);

        if (hoursMap.containsKey("REGULAR_DIURNA")) {
            hoursMap.put("REGULAR_DIURNA", weeklyLimit);
        }
        if (hoursMap.containsKey("EXTRA_DIURNA")) {
            hoursMap.put("EXTRA_DIURNA", extraPart);
        }
    }

    private void updateGroupTotals(ScheduleAssignmentGroup group, HoursCalculation calc) {
        HoursSummary summary = HoursSummary.fromCalculation(calc, overtimeTypeService.getAllActiveTypes());

        group.setTotalHours(summary.getTotalHours());
        group.setRegularHours(summary.getRegularHours());
        group.setOvertimeHours(summary.getOvertimeHours());
        group.setFestivoHours(summary.getFestivoHours());
        group.setFestivoType(summary.getFestivoType());
        group.setOvertimeType(summary.getOvertimeType());

        log.info("✅ GRUPO CONFIGURADO: Total={}h, Regulares={}h, Overtime={}h",
                group.getTotalHours(), group.getRegularHours(), group.getOvertimeHours());
    }

    private ScheduleAssignmentGroupDTO convertGroupToDTO(ScheduleAssignmentGroup group) {
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        HoursCalculation calc = calculateHours(schedules);
        return convertToDTO(group, schedules, calc);
    }

    private ScheduleAssignmentGroupDTO convertToDTO(ScheduleAssignmentGroup group, List<EmployeeSchedule> schedules, HoursCalculation calc) {
        ScheduleAssignmentGroupDTO dto = new ScheduleAssignmentGroupDTO();

        dto.setId(group.getId());
        dto.setEmployeeId(group.getEmployeeId());
        dto.setPeriodStart(dateFormat.format(group.getPeriodStart()));
        dto.setPeriodEnd(dateFormat.format(group.getPeriodEnd()));
        dto.setTotalHours(group.getTotalHours());
        dto.setRegularHours(group.getRegularHours());
        dto.setAssignedHours(group.getRegularHours());
        dto.setOvertimeHours(group.getOvertimeHours());
        dto.setOvertimeType(group.getOvertimeType());
        dto.setFestivoHours(group.getFestivoHours());
        dto.setFestivoType(group.getFestivoType());
        dto.setStatus(group.getStatus());
        dto.setEmployeeScheduleIds(group.getEmployeeScheduleIds());

        // Desglose detallado
        Map<String, Object> breakdown = createBreakdown(calc.getHoursByType());
        dto.setOvertimeBreakdown(breakdown);

        // Detalles de schedules
        List<ScheduleDetailDTO> details = schedules.stream()
                .map(this::createScheduleDetail)
                .collect(Collectors.toList());
        dto.setScheduleDetails(details);

        return dto;
    }

    private Map<String, Object> createBreakdown(Map<String, BigDecimal> hoursByType) {
        Map<String, Object> breakdown = new HashMap<>();
        hoursByType.forEach((key, value) -> breakdown.put(key, value.doubleValue()));

        // Total festivo
        BigDecimal festivoTotal = hoursByType.getOrDefault("FESTIVO_DIURNA", BigDecimal.ZERO)
                .add(hoursByType.getOrDefault("FESTIVO_NOCTURNA", BigDecimal.ZERO));
        breakdown.put("total_festivo", festivoTotal.doubleValue());

        return breakdown;
    }

    private ScheduleDetailDTO createScheduleDetail(EmployeeSchedule schedule) {
        ScheduleDetailDTO detail = new ScheduleDetailDTO();
        detail.setScheduleId(schedule.getId());
        detail.setShiftName(schedule.getShift() != null ? schedule.getShift().getName() : defaultShiftName);
        detail.setStartDate(dateFormat.format(schedule.getStartDate()));
        detail.setEndDate(dateFormat.format(schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate()));

        BigDecimal hours = schedule.getShift() != null ? TimeUtils.calculateTotalShiftHours(schedule.getShift()) : BigDecimal.ZERO;
        detail.setHoursInPeriod(hours.doubleValue());

        return detail;
    }

    // ===== CLASES DE APOYO =====

    private static class DatePeriod {
        private final Date startDate;
        private final Date endDate;
        private final LocalDate startLocalDate;
        private final LocalDate endLocalDate;

        private DatePeriod(Date startDate, Date endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.startLocalDate = toLocalDate(startDate);
            this.endLocalDate = toLocalDate(endDate);
        }

        static DatePeriod fromSchedules(List<EmployeeSchedule> schedules) {
            Date start = schedules.stream()
                    .map(EmployeeSchedule::getStartDate)
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(new Date());

            Date end = schedules.stream()
                    .map(es -> es.getEndDate() != null ? es.getEndDate() : es.getStartDate())
                    .filter(Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(new Date());

            return new DatePeriod(start, end);
        }

        static DatePeriod fromSchedule(EmployeeSchedule schedule) {
            Date start = schedule.getStartDate();
            Date end = schedule.getEndDate() != null ? schedule.getEndDate() : start;
            return new DatePeriod(start, end);
        }

        boolean overlapsWith(Date otherStart, Date otherEnd) {
            return !this.startDate.after(otherEnd) && !otherStart.after(this.endDate);
        }

        Date getStartDate() { return startDate; }
        Date getEndDate() { return endDate; }
        LocalDate getStartLocalDate() { return startLocalDate; }
        LocalDate getEndLocalDate() { return endLocalDate; }

        private static LocalDate toLocalDate(Date date) {
            if (date == null) return LocalDate.now();
            if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    private static class HoursCalculation {
        private final Map<String, BigDecimal> hoursByType;
        private final BigDecimal totalHours;

        HoursCalculation(Map<String, BigDecimal> hoursByType, BigDecimal totalHours) {
            this.hoursByType = hoursByType;
            this.totalHours = totalHours;
        }

        Map<String, BigDecimal> getHoursByType() { return hoursByType; }
        BigDecimal getTotalHours() { return totalHours; }
    }

    private static class HoursSummary {
        private final BigDecimal totalHours;
        private final BigDecimal regularHours;
        private final BigDecimal overtimeHours;
        private final BigDecimal festivoHours;
        private final String festivoType;
        private final String overtimeType;

        private HoursSummary(BigDecimal totalHours, BigDecimal regularHours, BigDecimal overtimeHours,
                             BigDecimal festivoHours, String festivoType, String overtimeType) {
            this.totalHours = totalHours.setScale(2, RoundingMode.HALF_UP);
            this.regularHours = regularHours.setScale(2, RoundingMode.HALF_UP);
            this.overtimeHours = overtimeHours.setScale(2, RoundingMode.HALF_UP);
            this.festivoHours = festivoHours.setScale(2, RoundingMode.HALF_UP);
            this.festivoType = festivoType;
            this.overtimeType = overtimeType;
        }

        static HoursSummary fromCalculation(HoursCalculation calc, List<OvertimeTypeDTO> types) {
            Map<String, BigDecimal> hoursByType = calc.getHoursByType();

            // Calcular regulares (incluye festivos)
            BigDecimal regularDiurnas = hoursByType.getOrDefault("REGULAR_DIURNA", BigDecimal.ZERO);
            BigDecimal regularNocturnas = hoursByType.getOrDefault("REGULAR_NOCTURNA", BigDecimal.ZERO);
            BigDecimal festivoTotal = hoursByType.getOrDefault("FESTIVO_DIURNA", BigDecimal.ZERO)
                    .add(hoursByType.getOrDefault("FESTIVO_NOCTURNA", BigDecimal.ZERO));

            BigDecimal regularesCompletas = regularDiurnas.add(regularNocturnas).add(festivoTotal);

            // Calcular extras (dominicales + extras)
            BigDecimal dominicalesTotal = hoursByType.getOrDefault("DOMINICAL_DIURNA", BigDecimal.ZERO)
                    .add(hoursByType.getOrDefault("DOMINICAL_NOCTURNA", BigDecimal.ZERO));
            BigDecimal extrasTotal = hoursByType.getOrDefault("EXTRA_DIURNA", BigDecimal.ZERO)
                    .add(hoursByType.getOrDefault("EXTRA_NOCTURNA", BigDecimal.ZERO));
            BigDecimal extrasCompletas = dominicalesTotal.add(extrasTotal);

            BigDecimal totalReal = regularesCompletas.add(extrasCompletas);

            String festivoType = determinePredominantType(hoursByType, types, true);
            String overtimeType = determinePredominantType(hoursByType, types, false);

            return new HoursSummary(totalReal, regularesCompletas, extrasCompletas,
                    festivoTotal, festivoType, overtimeType);
        }

        private static String determinePredominantType(Map<String, BigDecimal> hoursByType, List<OvertimeTypeDTO> types, boolean festivo) {
            Map<String, String> codeToName = types.stream()
                    .collect(Collectors.toMap(OvertimeTypeDTO::getCode, OvertimeTypeDTO::getDisplayName));

            Map<String, BigDecimal> filteredHours = hoursByType.entrySet().stream()
                    .filter(entry -> festivo ? entry.getKey().startsWith("FESTIVO_") : !entry.getKey().startsWith("FESTIVO_"))
                    .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (filteredHours.isEmpty()) {
                return festivo ? null : "Normal";
            }

            String predominantCode = filteredHours.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(festivo ? null : "REGULAR_DIURNA");

            return predominantCode != null ? codeToName.getOrDefault(predominantCode, predominantCode) :
                    (festivo ? null : "Normal");
        }

        BigDecimal getTotalHours() { return totalHours; }
        BigDecimal getRegularHours() { return regularHours; }
        BigDecimal getOvertimeHours() { return overtimeHours; }
        BigDecimal getFestivoHours() { return festivoHours; }
        String getFestivoType() { return festivoType; }
        String getOvertimeType() { return overtimeType; }
    }

    private static class TimeUtils {

        static boolean isNightTimeShift(String startTime, String endTime) {
            try {
                String sStr = normalizeTimeFormat(startTime);
                String eStr = normalizeTimeFormat(endTime);

                int startMinutes = toMinutes(sStr);
                int endMinutes = toMinutes(eStr);

                // Horario nocturno: 21:00 a 06:00
                int nightStart = 21 * 60;
                int nightEnd = 6 * 60;

                if (startMinutes > endMinutes) {
                    // Cruza medianoche
                    return startMinutes >= nightStart || endMinutes <= nightEnd;
                } else {
                    // Mismo día
                    boolean isEarlyMorning = startMinutes < nightEnd && endMinutes <= nightEnd;
                    boolean isLateEvening = startMinutes >= nightStart;

                    if (isEarlyMorning || isLateEvening) return true;

                    // Calcular minutos nocturnos
                    int nightMinutes = 0;
                    if (startMinutes < nightEnd && endMinutes > nightStart) {
                        nightMinutes = Math.min(endMinutes, nightEnd) - Math.max(startMinutes, 0);
                        nightMinutes += Math.min(endMinutes, 1440) - Math.max(startMinutes, nightStart);
                    }

                    return nightMinutes >= 180; // 3 horas mínimo
                }
            } catch (Exception e) {
                return false;
            }
        }

        static BigDecimal calculateDetailHours(ShiftDetail detail) {
            int minutes = calculateDetailMinutes(detail);
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        static BigDecimal calculateTotalShiftHours(Shifts shift) {
            if (shift == null || shift.getShiftDetails() == null) return BigDecimal.ZERO;

            int totalMinutes = shift.getShiftDetails().stream()
                    .mapToInt(TimeUtils::calculateDetailMinutes)
                    .sum();

            return BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        static int calculateDetailMinutes(ShiftDetail detail) {
            if (detail == null || detail.getStartTime() == null || detail.getEndTime() == null) return 0;

            int start = toMinutes(detail.getStartTime());
            int end = toMinutes(detail.getEndTime());
            return Math.max(0, (end >= start) ? (end - start) : (1440 - start + end));
        }

        static BigDecimal parseHourOrDecimal(String value) {
            if (value == null) return BigDecimal.ZERO;
            try {
                if (value.contains(":")) {
                    String[] parts = value.split(":");
                    BigDecimal hours = new BigDecimal(parts[0]);
                    BigDecimal minutes = new BigDecimal(parts[1]).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
                    return hours.add(minutes);
                }
                return new BigDecimal(value);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }

        static String getDayName(int dayOfWeek) {
            String[] days = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
            return dayOfWeek >= 1 && dayOfWeek <= 7 ? days[dayOfWeek] : "Día " + dayOfWeek;
        }

        private static int toMinutes(String timeStr) {
            try {
                String[] parts = timeStr.split(":");
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } catch (Exception e) {
                return 0;
            }
        }

        private static String normalizeTimeFormat(String time) {
            if (time == null) return "00:00:00";
            String[] parts = time.split(":");
            return parts.length == 2 ? time + ":00" : time;
        }
    }
}
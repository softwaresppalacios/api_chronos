package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HourClassificationService {

    private final OvertimeTypeService overtimeTypeService;
    private final HolidayExemptionService holidayExemptionService;
    private final HolidayService holidayService;
    private final GeneralConfigurationService configService;

    /**
     * Método principal: clasifica todas las horas de una lista de schedules
     */
    public Map<String, BigDecimal> classifyScheduleHours(List<EmployeeSchedule> schedules) {
        if (schedules.isEmpty()) return new HashMap<>();



        // Configuración
        int nightStartMinutes = getNightStartMinutes();
        BigDecimal weeklyLimit = getWeeklyLimit();
        Set<LocalDate> holidays = getHolidayDates();
        Map<String, OvertimeTypeDTO> availableTypes = getAvailableTypes();

        // Procesar directamente sin clases intermedias
        return processSchedulesDirectly(schedules, nightStartMinutes, weeklyLimit, holidays, availableTypes);
    }

    /**
     * Procesa schedules directamente sin crear objetos intermedios
     */
    private Map<String, BigDecimal> processSchedulesDirectly(List<EmployeeSchedule> schedules,
                                                             int nightStartMinutes,
                                                             BigDecimal weeklyLimit,
                                                             Set<LocalDate> holidays,
                                                             Map<String, OvertimeTypeDTO> availableTypes) {

        Map<String, BigDecimal> result = new HashMap<>();
        Map<String, BigDecimal> weeklyAccumulator = new HashMap<>();
        Map<String, Set<Long>> schedulesPerEmployeeDay = new HashMap<>();



        WeekFields weekFields = WeekFields.ISO;

        // Procesar cada schedule
        for (EmployeeSchedule schedule : schedules) {
            Long employeeId = schedule.getEmployeeId();
            Long scheduleId = schedule.getId();

            List<LocalDate> dates = getDatesToProcess(schedule);

            for (LocalDate date : dates) {
                int dayOfWeek = date.getDayOfWeek().getValue();
                boolean isHoliday = holidays.contains(date);
                boolean isSunday = (dayOfWeek == 7);

                // Verificar exenciones
                boolean hasExemption = checkHolidayExemption(employeeId, date);
                String exemptionReason = hasExemption ?
                        holidayExemptionService.getExemptionReason(employeeId, date) : null;

                if (hasExemption && exemptionReason != null &&
                        (exemptionReason.contains("NO_TRABAJAR") || exemptionReason.contains("DIA_LIBRE"))) {
                    continue;
                }

                // Detectar solapamiento por schedule
                String employeeDayKey = employeeId + "-" + date.toString();
                Set<Long> existingSchedules = schedulesPerEmployeeDay.getOrDefault(employeeDayKey, new HashSet<>());
                boolean isOverlapExtra = !existingSchedules.isEmpty() && !existingSchedules.contains(scheduleId);

                if (isOverlapExtra) {

                }

                // Obtener rangos de tiempo para esta fecha
                List<int[]> timeRanges = getTimeRangesForDateSimple(schedule, date);

                for (int[] range : timeRanges) {
                    int startMinutes = range[0];
                    int endMinutes = range[1];

                    // Dividir en día/noche directamente
                    int[] split = splitDayNightSimple(startMinutes, endMinutes, nightStartMinutes);
                    int dayMinutes = split[0];
                    int nightMinutes = split[1];

                    // Procesar horas diurnas
                    if (dayMinutes > 0) {
                        processHoursSegment(employeeId, scheduleId, date, dayMinutes, false,
                                isHoliday, isSunday, hasExemption, exemptionReason, isOverlapExtra,
                                weeklyLimit, weeklyAccumulator, availableTypes, result, weekFields);
                    }

                    // Procesar horas nocturnas
                    if (nightMinutes > 0) {
                        processHoursSegment(employeeId, scheduleId, date, nightMinutes, true,
                                isHoliday, isSunday, hasExemption, exemptionReason, isOverlapExtra,
                                weeklyLimit, weeklyAccumulator, availableTypes, result, weekFields);
                    }
                }

                // Registrar este schedule para este empleado-día
                existingSchedules.add(scheduleId);
                schedulesPerEmployeeDay.put(employeeDayKey, existingSchedules);
            }
        }


        result.forEach((type, hours) -> {
            if (hours.compareTo(BigDecimal.ZERO) > 0) {

            }
        });

        return result;
    }

    /**
     * Procesa un segmento de horas específico
     */
    private void processHoursSegment(Long employeeId, Long scheduleId, LocalDate date, int minutes, boolean isNight,
                                     boolean isHoliday, boolean isSunday, boolean hasExemption, String exemptionReason,
                                     boolean isOverlapExtra, BigDecimal weeklyLimit, Map<String, BigDecimal> weeklyAccumulator,
                                     Map<String, OvertimeTypeDTO> availableTypes, Map<String, BigDecimal> result,
                                     WeekFields weekFields) {

        String weekKey = getWeekKey(date, weekFields);
        String employeeWeekKey = employeeId + "-" + weekKey;
        BigDecimal weeklyHours = weeklyAccumulator.getOrDefault(employeeWeekKey, BigDecimal.ZERO);
        BigDecimal segmentHours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        boolean exceedsWeekly = weeklyHours.compareTo(weeklyLimit) >= 0;
        boolean willExceedWeekly = weeklyHours.add(segmentHours).compareTo(weeklyLimit) > 0;
        boolean isExtra = isOverlapExtra || exceedsWeekly;


        if (!isOverlapExtra && !exceedsWeekly && willExceedWeekly) {
            // Dividir el segmento: parte regular + parte extra
            BigDecimal remainingRegular = weeklyLimit.subtract(weeklyHours);
            BigDecimal extraPortion = segmentHours.subtract(remainingRegular);


            if (remainingRegular.compareTo(BigDecimal.ZERO) > 0) {
                String regularType = determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption,
                        exemptionReason, false, availableTypes);
                result.merge(regularType, remainingRegular, BigDecimal::add);

                if (regularType.startsWith("REGULAR_") || regularType.startsWith("FESTIVO_")) {
                    weeklyAccumulator.put(employeeWeekKey, weeklyLimit);
                }
            }

            if (extraPortion.compareTo(BigDecimal.ZERO) > 0) {
                String extraType = determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption,
                        exemptionReason, true, availableTypes);
                result.merge(extraType, extraPortion, BigDecimal::add);
            }

        } else {
            // Procesar normalmente
            String typeCode = determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption,
                    exemptionReason, isExtra, availableTypes);

            if (typeCode != null) {
                result.merge(typeCode, segmentHours, BigDecimal::add);

                if (typeCode.startsWith("REGULAR_") || typeCode.startsWith("FESTIVO_")) {
                    BigDecimal newWeeklyTotal = weeklyHours.add(segmentHours);
                    weeklyAccumulator.put(employeeWeekKey, newWeeklyTotal);
                }
            }
        }
    }

    /**
     * Determina el tipo de hora de forma simplificada
     */
    private String determineHourTypeSimple(boolean isNight, boolean isHoliday, boolean isSunday,
                                           boolean hasExemption, String exemptionReason, boolean isExtra,
                                           Map<String, OvertimeTypeDTO> availableTypes) {

        // Si es festivo con exención "NO_APLICAR_RECARGO", tratar como día normal
        if (isHoliday && hasExemption && "NO_APLICAR_RECARGO".equals(exemptionReason)) {
            String baseType = isExtra ? "EXTRA" : "REGULAR";
            return findBestMatch(baseType, isNight, availableTypes);
        }

        // Lógica de prioridades
        if (isHoliday && isSunday) {
            String baseType = isExtra ? "EXTRA_FESTIVO_DOMINICAL" : "FESTIVO_DOMINICAL";
            return findBestMatch(baseType, isNight, availableTypes);

        } else if (isHoliday) {
            String baseType = isExtra ? "EXTRA_FESTIVO" : "FESTIVO";
            return findBestMatch(baseType, isNight, availableTypes);

        } else if (isSunday) {
            String baseType = isExtra ? "EXTRA_DOMINICAL" : "DOMINICAL";
            return findBestMatch(baseType, isNight, availableTypes);

        } else {
            String baseType = isExtra ? "EXTRA" : "REGULAR";
            return findBestMatch(baseType, isNight, availableTypes);
        }
    }

    /**
     * Obtener rangos de tiempo simplificado - retorna array de [startMinutes, endMinutes]
     */
    private List<int[]> getTimeRangesForDateSimple(EmployeeSchedule schedule, LocalDate date) {
        List<int[]> ranges = new ArrayList<>();
        int dayOfWeek = date.getDayOfWeek().getValue();

        // Prioridad 1: TimeBlocks reales del día
        if (schedule.getDays() != null) {
            Optional<EmployeeScheduleDay> dayOpt = schedule.getDays().stream()
                    .filter(d -> date.equals(convertToLocalDate(d.getDate())))
                    .findFirst();

            if (dayOpt.isPresent() && dayOpt.get().getTimeBlocks() != null) {
                for (EmployeeScheduleTimeBlock block : dayOpt.get().getTimeBlocks()) {
                    ranges.add(new int[]{
                            toMinutes(block.getStartTime().toString()),
                            toMinutes(block.getEndTime().toString())
                    });
                }
                return ranges;
            }
        }

        // Prioridad 2: ShiftDetails
        if (schedule.getShift() != null && schedule.getShift().getShiftDetails() != null) {
            for (ShiftDetail detail : schedule.getShift().getShiftDetails()) {
                if (Objects.equals(detail.getDayOfWeek(), dayOfWeek) &&
                        detail.getStartTime() != null && detail.getEndTime() != null) {
                    ranges.add(new int[]{
                            toMinutes(detail.getStartTime()),
                            toMinutes(detail.getEndTime())
                    });
                }
            }
        }

        return ranges;
    }

    /**
     * Dividir día/noche simplificado - retorna [dayMinutes, nightMinutes]
     */
    private int[] splitDayNightSimple(int startMinutes, int endMinutes, int nightStartMinutes) {
        int totalMinutes = (endMinutes > startMinutes) ?
                (endMinutes - startMinutes) :
                (1440 - startMinutes + endMinutes);

        // Rango nocturno: 19:00-23:59 y 00:00-06:00
        int nightEndMinutes = 6 * 60; // 06:00

        int nightMinutes = 0;
        int current = startMinutes;
        int remaining = totalMinutes;

        while (remaining > 0) {
            int currentPos = current % 1440;
            int step = Math.min(remaining, 1440 - currentPos);

            // Verificar solapamiento con rangos nocturnos
            int overlap1 = calculateOverlap(currentPos, currentPos + step, nightStartMinutes, 1440);
            int overlap2 = calculateOverlap(currentPos, currentPos + step, 0, nightEndMinutes);

            nightMinutes += overlap1 + overlap2;
            current += step;
            remaining -= step;
        }

        return new int[]{totalMinutes - nightMinutes, nightMinutes};
    }

    private int calculateOverlap(int start1, int end1, int start2, int end2) {
        return Math.max(0, Math.min(end1, end2) - Math.max(start1, start2));
    }

    /**
     * Método auxiliar para obtener la clave de semana ISO
     */
    private String getWeekKey(LocalDate date, WeekFields weekFields) {
        int year = date.get(weekFields.weekBasedYear());
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    /**
     * Encuentra la mejor coincidencia en tipos disponibles
     */
    private String findBestMatch(String baseType, boolean isNight,
                                 Map<String, OvertimeTypeDTO> availableTypes) {
        String suffix = isNight ? "_NOCTURNA" : "_DIURNA";
        String preferredCode = baseType + suffix;

        // Intentar código exacto
        if (availableTypes.containsKey(preferredCode)) {
            return preferredCode;
        }

        // Fallbacks según tipo base
        if (baseType.contains("EXTRA")) {
            return isNight ? "EXTRA_NOCTURNA" : "EXTRA_DIURNA";
        } else if (baseType.contains("FESTIVO")) {
            return isNight ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";
        } else if (baseType.contains("DOMINICAL")) {
            return isNight ? "DOMINICAL_NOCTURNA" : "DOMINICAL_DIURNA";
        } else {
            return isNight ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA";
        }
    }

    // ===== MÉTODOS DE UTILIDAD =====

    private List<LocalDate> getDatesToProcess(EmployeeSchedule schedule) {
        if (schedule.getDays() != null && !schedule.getDays().isEmpty()) {
            return schedule.getDays().stream()
                    .map(day -> convertToLocalDate(day.getDate()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            LocalDate start = convertToLocalDate(schedule.getStartDate());
            LocalDate end = schedule.getEndDate() != null ?
                    convertToLocalDate(schedule.getEndDate()) : start;

            List<LocalDate> dates = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                dates.add(date);
            }
            return dates;
        }
    }

    // ===== CONFIGURACIÓN =====

    private int getNightStartMinutes() {
        try {
            String value = configService.getByType("NIGHT_START").getValue();
            String[] parts = value.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 19 * 60; // 19:00 por defecto
        }
    }

    private BigDecimal getWeeklyLimit() {
        try {
            String value = configService.getByType("WEEKLY_HOURS").getValue();
            if (value.contains(":")) {
                String[] parts = value.split(":");
                return new BigDecimal(parts[0]).add(
                        new BigDecimal(parts[1]).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                );
            }
            return new BigDecimal(value);
        } catch (Exception e) {
            return new BigDecimal("56"); // 56h por defecto
        }
    }

    private Set<LocalDate> getHolidayDates() {
        try {
            return holidayService.getAllHolidays().stream()
                    .filter(Objects::nonNull)
                    .map(h -> h.getHolidayDate())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private Map<String, OvertimeTypeDTO> getAvailableTypes() {
        return overtimeTypeService.getAllActiveTypes().stream()
                .collect(Collectors.toMap(OvertimeTypeDTO::getCode, t -> t));
    }

    private boolean checkHolidayExemption(Long employeeId, LocalDate date) {
        try {
            return holidayExemptionService.hasExemption(employeeId, date);
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDate convertToLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private int toMinutes(String time) {
        try {
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }
}
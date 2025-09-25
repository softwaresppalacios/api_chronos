package sp.sistemaspalacios.api_chronos.service.employeeSchedule.overtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.overtime.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayExemptionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
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


    public Map<String, BigDecimal> classifyDayHours(EmployeeSchedule schedule, LocalDate date) {
        if (schedule == null || date == null) return new HashMap<>();

        // Crear una lista temporal con solo este schedule pero filtrando para un día específico
        List<EmployeeSchedule> singleScheduleList = Arrays.asList(schedule);

        // Usar la lógica completa pero solo procesar el día solicitado
        Map<String, BigDecimal> fullResult = classifyScheduleHours(singleScheduleList);

        // Para debugging: obtener el total de horas de este día específico
        List<int[]> dayRanges = getTimeRangesForDateSimple(schedule, date);

        if (dayRanges.isEmpty()) {
            return new HashMap<>(); // Sin horas para este día
        }

        // Calcular las horas totales de este día
        BigDecimal dayTotalHours = BigDecimal.ZERO;
        for (int[] range : dayRanges) {
            int totalMinutes = (range[1] > range[0]) ?
                    (range[1] - range[0]) :
                    (1440 - range[0] + range[1]);
            dayTotalHours = dayTotalHours.add(
                    BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
            );
        }

        if (dayTotalHours.compareTo(BigDecimal.ZERO) == 0) {
            return new HashMap<>();
        }

        // Si el resultado completo tiene tipos especiales, asignar proporcionalmente a este día
        Map<String, BigDecimal> dayResult = new HashMap<>();

        // Buscar tipos especiales en el resultado completo
        for (Map.Entry<String, BigDecimal> entry : fullResult.entrySet()) {
            String type = entry.getKey();
            BigDecimal hours = entry.getValue();

            if (hours.compareTo(BigDecimal.ZERO) > 0 && isSpecialHourType(type)) {
                // Si encontramos horas especiales en el total, verificar si este día contribuye
                int dayOfWeek = date.getDayOfWeek().getValue();
                boolean isHoliday = getHolidayDates().contains(date);
                boolean isSunday = (dayOfWeek == 7);

                // Determinar si este día específico debería tener este tipo de horas especiales
                if (shouldDayHaveSpecialType(type, isHoliday, isSunday, schedule.getEmployeeId(), date)) {
                    dayResult.put(type, dayTotalHours);
                    break; // Solo un tipo por día
                }
            }
        }

        return dayResult;
    }

    // Método helper para determinar si un día debería tener un tipo específico
    private boolean shouldDayHaveSpecialType(String type, boolean isHoliday, boolean isSunday,
                                             Long employeeId, LocalDate date) {

        // Verificar exenciones
        boolean hasExemption = checkHolidayExemption(employeeId, date);
        if (hasExemption) {
            return type.equals("EXEMPT");
        }
        if (type.startsWith("FESTIVO_") && isHoliday) return true;
        if (type.startsWith("DOMINICAL_") && isSunday) return true;
        if (type.startsWith("EXTRA_")) {
            // Las horas extras pueden aparecer cualquier día dependiendo del límite semanal
            return true;
        }

        return false;
    }

    private boolean isSpecialHourType(String type) {
        return type.startsWith("EXTRA_") ||
                type.startsWith("FESTIVO_") ||
                type.startsWith("DOMINICAL_") ||
                type.equals("EXEMPT");
    }
    // Método helper que falta

    public boolean hasSpecialHours(Map<String, BigDecimal> dayClassification) {
        return dayClassification.entrySet().stream()
                .anyMatch(entry -> {
                    String type = entry.getKey();
                    BigDecimal hours = entry.getValue();
                    return hours.compareTo(BigDecimal.ZERO) > 0 &&
                            (type.startsWith("EXTRA_") ||
                                    type.startsWith("FESTIVO_") ||
                                    type.startsWith("DOMINICAL_") ||
                                    type.equals("EXEMPT")); // AGREGAR ESTA LÍNEA
                });
    }


    /**
     * Obtener rangos de tiempo simplificado - retorna array de [startMinutes, endMinutes]
     */
    private List<int[]> getTimeRangesForDateSimple(EmployeeSchedule schedule, LocalDate date) {
        List<int[]> ranges = new ArrayList<>();
        int dayOfWeek = date.getDayOfWeek().getValue();

        // 🔍 LOG
        System.out.println("getTimeRanges: Date=" + date + " ScheduleId=" + schedule.getId());

        if (schedule.getDays() != null) {
            Optional<EmployeeScheduleDay> dayOpt = schedule.getDays().stream()
                    .filter(d -> {
                        LocalDate dayDate = convertToLocalDateSafe(d.getDate());
                        return date.equals(dayDate);
                    })
                    .findFirst();

            if (dayOpt.isPresent() && dayOpt.get().getTimeBlocks() != null && !dayOpt.get().getTimeBlocks().isEmpty()) {
                for (EmployeeScheduleTimeBlock block : dayOpt.get().getTimeBlocks()) {
                    int[] range = new int[]{
                            toMinutes(block.getStartTime().toString()),
                            toMinutes(block.getEndTime().toString())
                    };
                    ranges.add(range);

                    // 🔍 LOG - Ver qué rangos se agregan
                    System.out.println("  → Adding range: " + range[0] + "-" + range[1]);
                }
                System.out.println("  → Found " + ranges.size() + " ranges from DAYS");
                return ranges;
            }
        }

        if (schedule.getShift() != null && schedule.getShift().getShiftDetails() != null) {
            if (schedule.getShift().getShiftDetails().isEmpty()) {
                return ranges;
            }
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

        System.out.println("  → Total ranges: " + ranges.size());
        return ranges;
    }
    private LocalDate convertToLocalDateSafe(Date date) {
        if (date == null) return null;
        try {
            if (date instanceof java.sql.Date) {
                return ((java.sql.Date) date).toLocalDate();
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception e) {
            System.err.println("Error convirtiendo fecha: " + date + " - " + e.getMessage());
            return null;
        }
    }
    /**
     * Dividir día/noche simplificado - retorna [dayMinutes, nightMinutes]
     */
    private int[] splitDayNightSimple(int startMinutes, int endMinutes, int nightStartMinutes) {
        int totalMinutes = (endMinutes > startMinutes) ?
                (endMinutes - startMinutes) :
                (1440 - startMinutes + endMinutes);
        // CONFIGURACIÓN NOCTURNA: 19:00 (1140 min) hasta 06:00 (360 min)
        int nightStart = 19 * 60;  // 19:00 = 1140 minutos
        int nightEnd = 6 * 60;     // 06:00 = 360 minutos

        int nightMinutes = 0;
        int dayMinutes = 0;

        // Verificar si el rango completo está en horario diurno (06:00 - 19:00)
        if (startMinutes >= nightEnd && endMinutes <= nightStart) {
            // Completamente diurno
            dayMinutes = totalMinutes;
            nightMinutes = 0;

        } else if ((startMinutes >= nightStart && endMinutes >= nightStart) ||
                (startMinutes <= nightEnd && endMinutes <= nightEnd)) {
            // Completamente nocturno (19:00-23:59 o 00:00-06:00)
            dayMinutes = 0;
            nightMinutes = totalMinutes;
        } else {
            // Calcular la parte diurna
            if (startMinutes < nightStart && endMinutes > nightEnd) {
                if (endMinutes <= nightStart) {
                    // Caso: inicia antes de 19:00 y termina antes de 19:00
                    dayMinutes = totalMinutes;
                } else {
                    // Caso: cruza el inicio de la noche
                    dayMinutes = nightStart - Math.max(startMinutes, nightEnd);
                    nightMinutes = totalMinutes - dayMinutes;
                }
            } else {
                // Para otros casos complejos, usar cálculo conservador
                dayMinutes = totalMinutes;
            }
        }
        return new int[]{dayMinutes, nightMinutes};
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
            LocalDate start = schedule.getStartDate();
            LocalDate end = (schedule.getEndDate() != null) ? schedule.getEndDate() : start;
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
    public Map<String, BigDecimal> classifyScheduleHours(List<EmployeeSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return new HashMap<>();
        }

        try {
            int nightStartMinutes = getNightStartMinutesSafe();
            BigDecimal weeklyLimit = getWeeklyLimitSafe();
            Set<LocalDate> holidays = getHolidayDatesSafe();
            Map<String, OvertimeTypeDTO> availableTypes = getAvailableTypesSafe();
            return processSchedulesDirectly(schedules, nightStartMinutes, weeklyLimit, holidays, availableTypes);

        } catch (Exception e) {
            System.err.println("ERROR en classifyScheduleHours: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }


    // MÉTODOS SEGUROS para configuración
    private int getNightStartMinutesSafe() {
        try {
            return getNightStartMinutes();
        } catch (Exception e) {
            System.err.println("Error obteniendo inicio noche, usando 19:00 por defecto: " + e.getMessage());
            return 19 * 60; // 19:00 por defecto
        }
    }

    private BigDecimal getWeeklyLimitSafe() {
        try {
            return getWeeklyLimit();
        } catch (Exception e) {
            System.err.println("Error obteniendo límite semanal, usando 56h por defecto: " + e.getMessage());
            return new BigDecimal("56");
        }
    }

    private Set<LocalDate> getHolidayDatesSafe() {
        try {
            return getHolidayDates();
        } catch (Exception e) {
            System.err.println("Error obteniendo festivos, usando lista vacía: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    private Map<String, OvertimeTypeDTO> getAvailableTypesSafe() {
        try {
            return getAvailableTypes();
        } catch (Exception e) {
            System.err.println("Error obteniendo tipos disponibles, usando mapa vacío: " + e.getMessage());
            return Collections.emptyMap();
        }
    }


    private Map<String, BigDecimal> processSchedulesDirectly(List<EmployeeSchedule> schedules,
                                                             int nightStartMinutes,
                                                             BigDecimal weeklyLimit,
                                                             Set<LocalDate> holidays,
                                                             Map<String, OvertimeTypeDTO> availableTypes) {

        Map<String, BigDecimal> result = new HashMap<>();
        Map<String, Set<Long>> schedulesPerEmployeeDay = new HashMap<>();

        for (EmployeeSchedule schedule : schedules) {
            Long employeeId = schedule.getEmployeeId();
            Long scheduleId = schedule.getId();
            List<LocalDate> dates = getDatesToProcess(schedule);

            for (LocalDate date : dates) {
                List<int[]> timeRanges = getTimeRangesForDateSimple(schedule, date);
                if (!timeRanges.isEmpty()) {
                    String employeeDayKey = employeeId + "-" + date.toString();
                    Set<Long> existingSchedules = schedulesPerEmployeeDay.getOrDefault(employeeDayKey, new HashSet<>());
                    existingSchedules.add(scheduleId);
                    schedulesPerEmployeeDay.put(employeeDayKey, existingSchedules);
                }
            }
        }

        for (EmployeeSchedule schedule : schedules) {
            Long employeeId = schedule.getEmployeeId();
            Long scheduleId = schedule.getId();
            List<LocalDate> dates = getDatesToProcess(schedule);

            for (LocalDate date : dates) {
                int dayOfWeek = date.getDayOfWeek().getValue();
                boolean isHoliday = holidays.contains(date);
                boolean isSunday = (dayOfWeek == 7);

                boolean hasExemption = checkHolidayExemption(employeeId, date);
                String exemptionReason = hasExemption ?
                        holidayExemptionService.getExemptionReason(employeeId, date) : null;

                if (hasExemption && exemptionReason != null &&
                        (exemptionReason.contains("NO_TRABAJAR") || exemptionReason.contains("DIA_LIBRE"))) {
                    continue;
                }

                String employeeDayKey = employeeId + "-" + date.toString();
                Set<Long> schedulesInDate = schedulesPerEmployeeDay.getOrDefault(employeeDayKey, new HashSet<>());
                List<Long> sortedSchedules = schedulesInDate.stream().sorted().collect(Collectors.toList());
                boolean isOverlapExtra = sortedSchedules.size() > 1 && !sortedSchedules.get(0).equals(scheduleId);

                List<int[]> timeRanges = getTimeRangesForDateSimple(schedule, date);
                if (timeRanges.isEmpty()) continue;

                for (int[] range : timeRanges) {
                    System.out.println("Processing: Employee=" + employeeId +
                            " Date=" + date +
                            " ScheduleId=" + scheduleId +
                            " Range=" + range[0] + "-" + range[1] +
                            " IsHoliday=" + isHoliday);
                    int startMinutes = range[0];
                    int endMinutes = range[1];
                    int[] split = splitDayNightSimple(startMinutes, endMinutes, nightStartMinutes);
                    int dayMinutes = split[0];
                    int nightMinutes = split[1];

                    // ✅ FIX: Procesar horas normalmente (diurnas y nocturnas)
                    if (dayMinutes > 0) {
                        processHoursSegment(employeeId, scheduleId, date, dayMinutes, false,
                                isHoliday, isSunday, hasExemption, exemptionReason, isOverlapExtra,
                                availableTypes, result);
                    }

                    if (nightMinutes > 0) {
                        processHoursSegment(employeeId, scheduleId, date, nightMinutes, true,
                                isHoliday, isSunday, hasExemption, exemptionReason, isOverlapExtra,
                                availableTypes, result);
                    }
                }
            }
        }

        return result;
    }
    private void processHoursSegment(Long employeeId, Long scheduleId, LocalDate date, int minutes, boolean isNight,
                                     boolean isHoliday, boolean isSunday, boolean hasExemption, String exemptionReason,
                                     boolean isOverlapExtra, Map<String, OvertimeTypeDTO> availableTypes,
                                     Map<String, BigDecimal> result) {

        BigDecimal segmentHours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        String typeCode = determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption,
                exemptionReason, isOverlapExtra, availableTypes);

        if (typeCode != null) {
            result.merge(typeCode, segmentHours, BigDecimal::add);

        } else {
        }
    }

    // REEMPLAZAR COMPLETO el método determineHourTypeSimple
    private String determineHourTypeSimple(boolean isNight, boolean isHoliday, boolean isSunday,
                                           boolean hasExemption, String exemptionReason, boolean isExtra,
                                           Map<String, OvertimeTypeDTO> availableTypes) {
        if (isHoliday && hasExemption && "NO_APLICAR_RECARGO".equals(exemptionReason)) {
            String baseType = isExtra ? "EXTRA" : "REGULAR";
            String result = findBestMatch(baseType, isNight, availableTypes);
            return result;
        }
        String result;
        if (isExtra) {
            if (isHoliday && isSunday) {
                String baseType = "EXTRA_FESTIVO_DOMINICAL";
                result = findBestMatch(baseType, isNight, availableTypes);
            } else if (isHoliday) {
                String baseType = "EXTRA_FESTIVO";
                result = findBestMatch(baseType, isNight, availableTypes);
            } else if (isSunday) {
                String baseType = "EXTRA_DOMINICAL";
                result = findBestMatch(baseType, isNight, availableTypes);
            } else {
                // ✅ HORAS EXTRAS NORMALES (las nocturnas del segundo turno)
                String baseType = "EXTRA";
                result = findBestMatch(baseType, isNight, availableTypes);
            }
        } else {
            if (isHoliday && isSunday) {
                String baseType = "FESTIVO_DOMINICAL";
                result = findBestMatch(baseType, isNight, availableTypes);
            } else if (isHoliday) {
                String baseType = "FESTIVO";
                result = findBestMatch(baseType, isNight, availableTypes);
            } else if (isSunday) {
                String baseType = "DOMINICAL";
                result = findBestMatch(baseType, isNight, availableTypes);
            } else {
                String baseType = "REGULAR";
                result = findBestMatch(baseType, isNight, availableTypes);
            }
        }
        return result;
    }

}
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

        // Lógica de tipos especiales
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


    private void processHoursSegment(Long employeeId, Long scheduleId, LocalDate date, int minutes, boolean isNight,
                                     boolean isHoliday, boolean isSunday, boolean hasExemption, String exemptionReason,
                                     boolean isOverlapExtra, BigDecimal weeklyLimit, Map<String, BigDecimal> weeklyAccumulator,
                                     Map<String, OvertimeTypeDTO> availableTypes, Map<String, BigDecimal> result,
                                     WeekFields weekFields) {

        String weekKey = getWeekKey(date, weekFields);
        String employeeWeekKey = employeeId + "-" + weekKey;
        BigDecimal weeklyHours = weeklyAccumulator.getOrDefault(employeeWeekKey, BigDecimal.ZERO);
        BigDecimal segmentHours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // SOLO usar múltiples turnos para determinar "extra", NO límite semanal
        boolean isExtra = isOverlapExtra;

        // DEBUG TEMPORAL
        System.out.println("=== PROCESS HOURS DEBUG ===");
        System.out.println("Employee: " + employeeId + ", Date: " + date + ", Minutes: " + minutes);
        System.out.println("IsOverlapExtra: " + isOverlapExtra + ", IsExtra: " + isExtra);

        // Procesar TODAS las horas normalmente sin división por límite semanal
        String typeCode = determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption,
                exemptionReason, isExtra, availableTypes);

        if (typeCode != null) {
            result.merge(typeCode, segmentHours, BigDecimal::add);
            System.out.println("Added: " + typeCode + " = " + segmentHours + "h");

            // Seguir acumulando para tracking, pero sin usar para división
            BigDecimal newWeeklyTotal = weeklyHours.add(segmentHours);
            weeklyAccumulator.put(employeeWeekKey, newWeeklyTotal);
            System.out.println("Updated weekly total: " + newWeeklyTotal);
        }
        System.out.println("=== END DEBUG ===");
    }




    /**
     * Obtener rangos de tiempo simplificado - retorna array de [startMinutes, endMinutes]
     */
    private List<int[]> getTimeRangesForDateSimple(EmployeeSchedule schedule, LocalDate date) {
        List<int[]> ranges = new ArrayList<>();
        int dayOfWeek = date.getDayOfWeek().getValue();

        System.out.println("DEBUG - Buscando rangos para " + date + " (dayOfWeek: " + dayOfWeek + ")");

        // Prioridad 1: TimeBlocks reales del día
        if (schedule.getDays() != null) {
            System.out.println("DEBUG - Schedule tiene " + schedule.getDays().size() + " días");
            Optional<EmployeeScheduleDay> dayOpt = schedule.getDays().stream()
                    .filter(d -> {
                        LocalDate dayDate = convertToLocalDateSafe(d.getDate());
                        boolean matches = date.equals(dayDate);
                        System.out.println("Comparando " + date + " con " + dayDate + " = " + matches);
                        return matches;
                    })
                    .findFirst();

            if (dayOpt.isPresent() && dayOpt.get().getTimeBlocks() != null && !dayOpt.get().getTimeBlocks().isEmpty()) {
                System.out.println("DEBUG - Encontró día específico con " + dayOpt.get().getTimeBlocks().size() + " bloques");
                for (EmployeeScheduleTimeBlock block : dayOpt.get().getTimeBlocks()) {
                    ranges.add(new int[]{
                            toMinutes(block.getStartTime().toString()),
                            toMinutes(block.getEndTime().toString())
                    });
                }
                return ranges;
            } else {
                System.out.println("DEBUG - Día específico encontrado pero SIN bloques, intentando fallback...");
            }
        }

        // Prioridad 2: ShiftDetails - ESTE FALLBACK ES CRÍTICO
        if (schedule.getShift() != null && schedule.getShift().getShiftDetails() != null) {
            if (schedule.getShift().getShiftDetails().isEmpty()) {
                System.out.println("DEBUG - No hay ShiftDetails, retornando vacío");
                return ranges;
            }
            for (ShiftDetail detail : schedule.getShift().getShiftDetails()) {
                System.out.println("DEBUG - Detail: dayOfWeek=" + detail.getDayOfWeek() + " vs " + dayOfWeek + ", times=" + detail.getStartTime() + "-" + detail.getEndTime());
                if (Objects.equals(detail.getDayOfWeek(), dayOfWeek) &&
                        detail.getStartTime() != null && detail.getEndTime() != null) {
                    System.out.println("DEBUG - MATCH! Agregando rango desde ShiftDetail");
                    ranges.add(new int[]{
                            toMinutes(detail.getStartTime()),
                            toMinutes(detail.getEndTime())
                    });
                }
            }
        } else {
            System.out.println("DEBUG - No hay ShiftDetails disponibles");
        }

        System.out.println("DEBUG - Total rangos encontrados: " + ranges.size());
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

        System.out.println("DEBUG DIVISIÓN: Evaluando " +
                (startMinutes/60) + ":" + String.format("%02d", startMinutes%60) + " - " +
                (endMinutes/60) + ":" + String.format("%02d", endMinutes%60));

        // Verificar si el rango completo está en horario diurno (06:00 - 19:00)
        if (startMinutes >= nightEnd && endMinutes <= nightStart) {
            // Completamente diurno
            dayMinutes = totalMinutes;
            nightMinutes = 0;

            System.out.println("RESULTADO: Completamente DIURNO = " + dayMinutes + " min");

        } else if ((startMinutes >= nightStart && endMinutes >= nightStart) ||
                (startMinutes <= nightEnd && endMinutes <= nightEnd)) {
            // Completamente nocturno (19:00-23:59 o 00:00-06:00)
            dayMinutes = 0;
            nightMinutes = totalMinutes;

            System.out.println("RESULTADO: Completamente NOCTURNO = " + nightMinutes + " min");

        } else {
            // Rango mixto - dividir
            System.out.println("RESULTADO: Rango MIXTO - dividiendo...");

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

        System.out.println("FINAL: " + dayMinutes + " min diurnos, " + nightMinutes + " min nocturnos");
        return new int[]{dayMinutes, nightMinutes};
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





    // REEMPLAZAR el método classifyScheduleHours en HourClassificationService.java
    public Map<String, BigDecimal> classifyScheduleHours(List<EmployeeSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            System.out.println("No hay schedules para clasificar");
            return new HashMap<>();
        }

        try {
            System.out.println("=== INICIANDO CLASIFICACIÓN DE HORAS ===");
            System.out.println("Schedules a procesar: " + schedules.size());

            // Configuración con valores por defecto seguros
            int nightStartMinutes = getNightStartMinutesSafe();
            BigDecimal weeklyLimit = getWeeklyLimitSafe();
            Set<LocalDate> holidays = getHolidayDatesSafe();
            Map<String, OvertimeTypeDTO> availableTypes = getAvailableTypesSafe();

            System.out.println("Configuración cargada:");
            System.out.println("- Inicio noche: " + nightStartMinutes + " minutos");
            System.out.println("- Límite semanal: " + weeklyLimit + "h");
            System.out.println("- Festivos: " + holidays.size());
            System.out.println("- Tipos disponibles: " + availableTypes.size());

            // CAMBIO CRÍTICO: Usar processSchedulesDirectly() en lugar de processSchedulesSafely()
            return processSchedulesDirectly(schedules, nightStartMinutes, weeklyLimit, holidays, availableTypes);

        } catch (Exception e) {
            System.err.println("ERROR en classifyScheduleHours: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    // MÉTODO SEGURO para procesar schedules
    private Map<String, BigDecimal> processSchedulesSafely(List<EmployeeSchedule> schedules,
                                                           int nightStartMinutes,
                                                           BigDecimal weeklyLimit,
                                                           Set<LocalDate> holidays,
                                                           Map<String, OvertimeTypeDTO> availableTypes) {
        Map<String, BigDecimal> result = new HashMap<>();

        try {
            System.out.println("Procesando " + schedules.size() + " schedules");

            for (int i = 0; i < schedules.size(); i++) {
                EmployeeSchedule schedule = schedules.get(i);
                System.out.println("Procesando schedule " + (i+1) + "/" + schedules.size() + " - ID: " +
                        (schedule != null ? schedule.getId() : "null"));

                if (schedule == null) {
                    System.out.println("Schedule es null, saltando...");
                    continue;
                }

                try {
                    processingleScheduleSafely(schedule, holidays, availableTypes, nightStartMinutes, result);
                    System.out.println("Schedule " + schedule.getId() + " procesado exitosamente");

                } catch (Exception e) {
                    System.err.println("Error procesando schedule " + schedule.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                    // Continuar con el siguiente schedule
                }
            }

            System.out.println("=== CLASIFICACIÓN COMPLETADA ===");
            System.out.println("Resultados finales:");
            result.forEach((type, hours) -> {
                if (hours.compareTo(BigDecimal.ZERO) > 0) {
                    System.out.println("  " + type + ": " + hours + "h");
                }
            });

        } catch (Exception e) {
            System.err.println("ERROR en processSchedulesSafely: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    // MÉTODO SEGURO para procesar un schedule individual
    private void processingleScheduleSafely(EmployeeSchedule schedule, Set<LocalDate> holidays,
                                            Map<String, OvertimeTypeDTO> availableTypes,
                                            int nightStartMinutes,
                                            Map<String, BigDecimal> result) {
        try {
            if (schedule.getEmployeeId() == null) {
                System.out.println("Schedule sin employeeId, saltando...");
                return;
            }

            List<LocalDate> dates = getDatesToProcessSafely(schedule);
            System.out.println("Fechas a procesar para schedule " + schedule.getId() + ": " + dates.size());

            if (dates.isEmpty()) {
                System.out.println("No hay fechas para procesar en schedule " + schedule.getId());
                return;
            }

            for (LocalDate date : dates) {
                try {
                    processDateSafely(schedule, date, holidays, availableTypes, nightStartMinutes, result);
                } catch (Exception e) {
                    System.err.println("Error procesando fecha " + date + " en schedule " + schedule.getId() + ": " + e.getMessage());
                    // Continuar con la siguiente fecha
                }
            }

        } catch (Exception e) {
            System.err.println("Error en processingleScheduleSafely: " + e.getMessage());
            throw e;
        }
    }

    // MÉTODO SEGURO para procesar una fecha
    private void processDateSafely(EmployeeSchedule schedule, LocalDate date, Set<LocalDate> holidays,
                                   Map<String, OvertimeTypeDTO> availableTypes, int nightStartMinutes,
                                   Map<String, BigDecimal> result) {
        try {
            Long employeeId = schedule.getEmployeeId();
            int dayOfWeek = date.getDayOfWeek().getValue();
            boolean isHoliday = holidays.contains(date);
            boolean isSunday = (dayOfWeek == 7);

            // Verificar exenciones de forma segura
            boolean hasExemption = checkHolidayExemptionSafely(employeeId, date);
            String exemptionReason = null;
            if (hasExemption) {
                try {
                    exemptionReason = holidayExemptionService.getExemptionReason(employeeId, date);
                } catch (Exception e) {
                    System.err.println("Error obteniendo razón de exención: " + e.getMessage());
                }
            }

            // Si tiene exención para no trabajar, saltar
            if (hasExemption && exemptionReason != null &&
                    (exemptionReason.contains("NO_TRABAJAR") || exemptionReason.contains("DIA_LIBRE"))) {
                return;
            }

            // Obtener rangos de tiempo de forma segura
            List<int[]> timeRanges = getTimeRangesForDateSafely(schedule, date);
            if (timeRanges.isEmpty()) {
                return; // No hay trabajo este día
            }

            // Procesar cada rango de tiempo
            for (int[] range : timeRanges) {
                int startMinutes = range[0];
                int endMinutes = range[1];

                // Dividir en día/noche
                int[] split = splitDayNightSafely(startMinutes, endMinutes, nightStartMinutes);
                int dayMinutes = split[0];
                int nightMinutes = split[1];

                // Procesar horas diurnas
                if (dayMinutes > 0) {
                    String typeCode = determineHourTypeSafely(false, isHoliday, isSunday, hasExemption,
                            exemptionReason, false, availableTypes);
                    if (typeCode != null) {
                        BigDecimal hours = BigDecimal.valueOf(dayMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                        result.merge(typeCode, hours, BigDecimal::add);
                    }
                }

                // Procesar horas nocturnas
                if (nightMinutes > 0) {
                    String typeCode = determineHourTypeSafely(true, isHoliday, isSunday, hasExemption,
                            exemptionReason, false, availableTypes);
                    if (typeCode != null) {
                        BigDecimal hours = BigDecimal.valueOf(nightMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                        result.merge(typeCode, hours, BigDecimal::add);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error en processDateSafely para fecha " + date + ": " + e.getMessage());
            throw e;
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

    private boolean checkHolidayExemptionSafely(Long employeeId, LocalDate date) {
        try {
            return checkHolidayExemption(employeeId, date);
        } catch (Exception e) {
            System.err.println("Error verificando exención para " + employeeId + " en " + date + ": " + e.getMessage());
            return false;
        }
    }

    // MÉTODO SEGURO para obtener fechas
    private List<LocalDate> getDatesToProcessSafely(EmployeeSchedule schedule) {
        try {
            return getDatesToProcess(schedule);
        } catch (Exception e) {
            System.err.println("Error obteniendo fechas para schedule " + schedule.getId() + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // MÉTODO SEGURO para obtener rangos de tiempo
    private List<int[]> getTimeRangesForDateSafely(EmployeeSchedule schedule, LocalDate date) {
        try {
            return getTimeRangesForDateSimple(schedule, date);
        } catch (Exception e) {
            System.err.println("Error obteniendo rangos para " + date + " en schedule " + schedule.getId() + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // MÉTODO SEGURO para dividir día/noche
    private int[] splitDayNightSafely(int startMinutes, int endMinutes, int nightStartMinutes) {
        try {
            return splitDayNightSimple(startMinutes, endMinutes, nightStartMinutes);
        } catch (Exception e) {
            System.err.println("Error dividiendo día/noche: " + e.getMessage());
            int totalMinutes = Math.max(0, endMinutes - startMinutes);
            return new int[]{totalMinutes, 0}; // Todo como diurno por defecto
        }
    }

    // MÉTODO SEGURO para determinar tipo de hora
    private String determineHourTypeSafely(boolean isNight, boolean isHoliday, boolean isSunday,
                                           boolean hasExemption, String exemptionReason, boolean isExtra,
                                           Map<String, OvertimeTypeDTO> availableTypes) {
        try {
            return determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption, exemptionReason, isExtra, availableTypes);
        } catch (Exception e) {
            System.err.println("Error determinando tipo de hora: " + e.getMessage());
            return isNight ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA"; // Tipo por defecto
        }
    }

    private Map<String, BigDecimal> processSchedulesDirectly(List<EmployeeSchedule> schedules,
                                                             int nightStartMinutes,
                                                             BigDecimal weeklyLimit,
                                                             Set<LocalDate> holidays,
                                                             Map<String, OvertimeTypeDTO> availableTypes) {

        Map<String, BigDecimal> result = new HashMap<>();
        Map<String, Set<Long>> schedulesPerEmployeeDay = new HashMap<>();

        System.out.println("=== PROCESANDO SCHEDULES DIRECTAMENTE ===");
        System.out.println("Total schedules: " + schedules.size());

        // PASO 1: PRIMERO REGISTRAR TODOS LOS SCHEDULES POR FECHA
        for (EmployeeSchedule schedule : schedules) {
            Long employeeId = schedule.getEmployeeId();
            Long scheduleId = schedule.getId();

            System.out.println("Registrando Schedule ID: " + scheduleId + " para empleado: " + employeeId);

            List<LocalDate> dates = getDatesToProcess(schedule);

            for (LocalDate date : dates) {
                // CORRECCIÓN CRÍTICA: Verificar tanto timeBlocks reales como ShiftDetails
                List<int[]> timeRanges = getTimeRangesForDateSimple(schedule, date);

                // DEBUG: Agregar logs detallados
                System.out.println("DEBUG: Schedule " + scheduleId + " para fecha " + date + " - Rangos encontrados: " + timeRanges.size());

                if (!timeRanges.isEmpty()) {
                    String employeeDayKey = employeeId + "-" + date.toString();
                    Set<Long> existingSchedules = schedulesPerEmployeeDay.getOrDefault(employeeDayKey, new HashSet<>());
                    existingSchedules.add(scheduleId);
                    schedulesPerEmployeeDay.put(employeeDayKey, existingSchedules);

                    System.out.println("✅ Schedule " + scheduleId + " registrado para " + date);
                } else {
                    System.out.println("❌ Schedule " + scheduleId + " SIN rangos para " + date + " - No se registra");
                }
            }
        }

        System.out.println("Registro de schedules por día completado: " + schedulesPerEmployeeDay);

        // PASO 2: PROCESAR CADA SCHEDULE CON DETECCIÓN CORRECTA
        for (EmployeeSchedule schedule : schedules) {
            Long employeeId = schedule.getEmployeeId();
            Long scheduleId = schedule.getId();

            System.out.println("Procesando Schedule ID: " + scheduleId + " para empleado: " + employeeId);

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

                // DETECCIÓN CORRECTA: Si hay más de un schedule en esta fecha, los adicionales son extras
                String employeeDayKey = employeeId + "-" + date.toString();
                Set<Long> schedulesInDate = schedulesPerEmployeeDay.getOrDefault(employeeDayKey, new HashSet<>());

                // El primer schedule (por ID más bajo) es regular, los demás son extras
                List<Long> sortedSchedules = schedulesInDate.stream().sorted().collect(Collectors.toList());
                boolean isOverlapExtra = sortedSchedules.size() > 1 && !sortedSchedules.get(0).equals(scheduleId);

                System.out.println("=== VERIFICANDO SOLAPAMIENTO ===");
                System.out.println("Employee: " + employeeId + ", Date: " + date + ", Schedule: " + scheduleId);
                System.out.println("Schedules en esta fecha: " + schedulesInDate);
                System.out.println("Schedules ordenados: " + sortedSchedules);
                System.out.println("¿Es extra por solapamiento? " + isOverlapExtra);

                // Obtener rangos de tiempo para esta fecha
                List<int[]> timeRanges = getTimeRangesForDateSimple(schedule, date);

                if (timeRanges.isEmpty()) {
                    System.out.println("Sin rangos de tiempo para Schedule " + scheduleId + " en " + date);
                    continue;
                }

                for (int[] range : timeRanges) {
                    int startMinutes = range[0];
                    int endMinutes = range[1];

                    // Dividir en día/noche directamente
                    int[] split = splitDayNightSimple(startMinutes, endMinutes, nightStartMinutes);
                    int dayMinutes = split[0];
                    int nightMinutes = split[1];

                    System.out.println("Minutos diurnos: " + dayMinutes + ", nocturnos: " + nightMinutes);

                    // Procesar horas diurnas
                    if (dayMinutes > 0) {
                        processHoursSegment(employeeId, scheduleId, date, dayMinutes, false,
                                isHoliday, isSunday, hasExemption, exemptionReason, isOverlapExtra,
                                availableTypes, result);
                    }

                    // Procesar horas nocturnas
                    if (nightMinutes > 0) {
                        processHoursSegment(employeeId, scheduleId, date, nightMinutes, true,
                                isHoliday, isSunday, hasExemption, exemptionReason, isOverlapExtra,
                                availableTypes, result);
                    }
                }

                System.out.println("=== END DEBUG ===");
            }
        }

        return result;
    }
    // REEMPLAZAR COMPLETO el método processHoursSegment
    private void processHoursSegment(Long employeeId, Long scheduleId, LocalDate date, int minutes, boolean isNight,
                                     boolean isHoliday, boolean isSunday, boolean hasExemption, String exemptionReason,
                                     boolean isOverlapExtra, Map<String, OvertimeTypeDTO> availableTypes,
                                     Map<String, BigDecimal> result) {

        BigDecimal segmentHours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        System.out.println("=== PROCESANDO SEGMENTO ===");
        System.out.println("Employee: " + employeeId + ", Date: " + date + ", Minutes: " + minutes);
        System.out.println("IsNight: " + isNight + ", IsOverlapExtra: " + isOverlapExtra);
        System.out.println("Segmento horas: " + segmentHours);

        // Determinar tipo de hora
        String typeCode = determineHourTypeSimple(isNight, isHoliday, isSunday, hasExemption,
                exemptionReason, isOverlapExtra, availableTypes);

        if (typeCode != null) {
            result.merge(typeCode, segmentHours, BigDecimal::add);
            System.out.println("✅ Agregado: " + typeCode + " = " + segmentHours + "h");
            System.out.println("Total acumulado " + typeCode + ": " + result.get(typeCode) + "h");
        } else {
            System.out.println("❌ No se pudo determinar tipo de hora");
        }
        System.out.println("=== END PROCESANDO SEGMENTO ===");
    }

    // REEMPLAZAR COMPLETO el método determineHourTypeSimple
    private String determineHourTypeSimple(boolean isNight, boolean isHoliday, boolean isSunday,
                                           boolean hasExemption, String exemptionReason, boolean isExtra,
                                           Map<String, OvertimeTypeDTO> availableTypes) {

        System.out.println("=== DETERMINANDO TIPO DE HORA ===");
        System.out.println("isNight: " + isNight + ", isExtra: " + isExtra + ", isHoliday: " + isHoliday + ", isSunday: " + isSunday);

        // Si es festivo con exención "NO_APLICAR_RECARGO", tratar como día normal
        if (isHoliday && hasExemption && "NO_APLICAR_RECARGO".equals(exemptionReason)) {
            String baseType = isExtra ? "EXTRA" : "REGULAR";
            String result = findBestMatch(baseType, isNight, availableTypes);
            System.out.println("Festivo con exención NO_APLICAR_RECARGO -> " + result);
            return result;
        }

        String result;

        // ✅ PRIORIDAD A LAS HORAS EXTRAS POR SOLAPAMIENTO
        if (isExtra) {
            System.out.println("🔄 Es EXTRA por solapamiento de turnos");

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
            System.out.println("📝 Horas regulares (primer turno)");

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

        System.out.println("Tipo determinado: " + result);
        System.out.println("=== END DETERMINANDO TIPO ===");
        return result;
    }

}
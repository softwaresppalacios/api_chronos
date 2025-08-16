package sp.sistemaspalacios.api_chronos.validator.shift;

import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

public class ShiftDetailValidator {

    public static void validateShiftDetail(
            ShiftDetail shiftDetail,
            GeneralConfigurationService generalConfigurationService, // ✅ Solo este servicio
            ShiftDetailRepository shiftDetailRepository
    ) {
        // 1. Validar turno
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) {
            throw new IllegalArgumentException("El turno (Shift) es obligatorio.");
        }

        // 2. Día de la semana
        if (shiftDetail.getDayOfWeek() == null || shiftDetail.getDayOfWeek() < 1 || shiftDetail.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("El día de la semana debe ser entre 1 (Lunes) y 7 (Domingo).");
        }

        // 3. Formato hora militar start/end
        if (!isValidMilitaryTime(shiftDetail.getStartTime())) {
            throw new IllegalArgumentException("La hora de inicio debe estar en formato HH:mm (hora militar).");
        }
        if (!isValidMilitaryTime(shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin debe estar en formato HH:mm (hora militar).");
        }

        // 4. Horas no nulas
        if (shiftDetail.getStartTime() == null || shiftDetail.getEndTime() == null) {
            throw new IllegalArgumentException("Las horas de inicio y fin no pueden ser nulas.");
        }

        // 5. Validación mejorada para turnos que cruzan medianoche
        LocalTime startTime = LocalTime.parse(shiftDetail.getStartTime());
        LocalTime endTime = LocalTime.parse(shiftDetail.getEndTime());

        // Si el turno cruza medianoche (hora fin < hora inicio), es válido
        boolean crossesMidnight = endTime.isBefore(startTime);

        if (!crossesMidnight && (endTime.equals(startTime) || endTime.isBefore(startTime))) {
            throw new IllegalArgumentException("La hora de fin no puede ser menor o igual a la hora de inicio.");
        }

        // 6. Cálculo correcto de duración para validaciones
        long durationHours = calculateShiftDuration(startTime, endTime);

        // 7. Validar contra DAILY_HOURS de configuración
        validateDailyHours(durationHours, generalConfigurationService);

        // 8. Mínimo diario (2 horas)
        if (durationHours < 2) {
            throw new IllegalArgumentException("El turno diario no puede ser menor a 2 horas.");
        }

        // 9. Validación de horas semanales
        validateWeeklyHours(shiftDetail, shiftDetailRepository, generalConfigurationService);

        // 10. Validaciones de Break
        validateBreakTimes(shiftDetail, generalConfigurationService, shiftDetailRepository);
    }

    // ✅ NUEVO: Validar horas diarias usando DAILY_HOURS
    private static void validateDailyHours(long durationHours, GeneralConfigurationService configService) {
        try {
            String dailyHoursValue = configService.getByType("DAILY_HOURS").getValue();
            double maxDailyHours = Double.parseDouble(dailyHoursValue);

            if (durationHours > maxDailyHours) {
                throw new IllegalArgumentException(
                        String.format("La duración del turno (%.1f horas) excede el límite diario configurado de %.1f horas.",
                                (double) durationHours, maxDailyHours));
            }
        } catch (IllegalArgumentException e) {
            // Si no hay configuración DAILY_HOURS, usar fallback de 9 horas
            if (durationHours > 9) {
                throw new IllegalArgumentException("La diferencia entre las horas de inicio y fin no puede ser mayor a 9 horas.");
            }
        }
    }

    // Calcular duración considerando turnos que cruzan medianoche
    private static long calculateShiftDuration(LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            // Si cruza medianoche, calculamos hasta medianoche + desde medianoche
            return ChronoUnit.HOURS.between(start, LocalTime.MAX) +
                    ChronoUnit.HOURS.between(LocalTime.MIN, end) + 1;
        } else {
            return ChronoUnit.HOURS.between(start, end);
        }
    }

    // Validar formato HH:mm
    public static boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        String timeRegex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])$";
        return Pattern.matches(timeRegex, time);
    }

    // MÉTODOS DEPRECADOS - Mantener por compatibilidad pero no usar internamente
    @Deprecated
    public static boolean isStartTimeAfterEndTime(String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        // Si cruza medianoche, no es un error
        if (endTime.isBefore(startTime)) return false;
        return startTime.isAfter(endTime);
    }

    @Deprecated
    public static boolean isEndTimeBeforeStartTime(String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        // Si cruza medianoche, no es un error
        if (endTime.isBefore(startTime)) return false;
        return endTime.isBefore(startTime) || endTime.equals(startTime);
    }

    // Métodos actualizados para cálculos correctos
    public static boolean isTimeDifferenceTooLong(String start, String end, int maxHours) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        long duration = calculateShiftDuration(startTime, endTime);
        return duration > maxHours;
    }

    public static boolean isTimeDifferenceTooShort(String start, String end, int minHours) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        long duration = calculateShiftDuration(startTime, endTime);
        return duration < minHours;
    }

    // ✅ ACTUALIZADO: Horas semanales usando GeneralConfiguration
    public static void validateWeeklyHours(
            ShiftDetail shiftDetail,
            ShiftDetailRepository shiftDetailRepository,
            GeneralConfigurationService generalConfigurationService
    ) {
        int currentShiftHours = calculateShiftHours(shiftDetail.getStartTime(), shiftDetail.getEndTime());
        int exactWeeklyHours = getExactWeeklyHoursFromConfig(generalConfigurationService);
        int totalScheduled = getTotalScheduledHoursForShift(shiftDetail, shiftDetailRepository);
        int totalWithNew = totalScheduled + currentShiftHours;

        if (totalWithNew > exactWeeklyHours) {
            throw new IllegalArgumentException(
                    String.format("El total de horas semanales (%d) excedería las %d horas exactas requeridas. Actualmente programadas: %d horas. Este turno: %d horas. Debe ser exactamente %d horas, no más.",
                            totalWithNew, exactWeeklyHours, totalScheduled, currentShiftHours, exactWeeklyHours));
        }
    }

    // Calcular horas (actualizado para manejar cruces de medianoche)
    public static int calculateShiftHours(String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        return (int) calculateShiftDuration(startTime, endTime);
    }

    // ✅ ACTUALIZADO: Obtener horas semanales desde GeneralConfiguration
    public static int getExactWeeklyHoursFromConfig(GeneralConfigurationService configService) {
        try {
            String hoursStr = configService.getByType("WEEKLY_HOURS").getValue();

            if (hoursStr == null) {
                throw new IllegalStateException("No se encontró configuración de horas semanales.");
            }

            // Parsear diferentes formatos
            if (hoursStr.contains(":")) {
                // Formato "HH:mm" como "44:00"
                String[] parts = hoursStr.split(":");
                int exactHours = Integer.parseInt(parts[0]);
                if (exactHours <= 0) {
                    throw new IllegalStateException("Las horas semanales deben ser mayor a 0");
                }
                return exactHours;
            } else {
                // Formato decimal como "44" o "44.0"
                double hoursDecimal = Double.parseDouble(hoursStr);
                int exactHours = (int) Math.floor(hoursDecimal);
                if (exactHours <= 0) {
                    throw new IllegalStateException("Las horas semanales deben ser mayor a 0");
                }
                return exactHours;
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("No se encontró configuración de horas semanales. Debe configurar WEEKLY_HOURS.");
        }
    }

    public static int getTotalScheduledHoursForShift(ShiftDetail shiftDetail, ShiftDetailRepository shiftDetailRepository) {
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) return 0;
        List<ShiftDetail> allShiftDetails = shiftDetailRepository.findByShiftId(shiftDetail.getShift().getId());
        int totalHours = 0;
        for (ShiftDetail detail : allShiftDetails) {
            if (shiftDetail.getId() != null && shiftDetail.getId().equals(detail.getId())) continue;
            if (detail.getStartTime() != null && detail.getEndTime() != null) {
                totalHours += calculateShiftHours(detail.getStartTime(), detail.getEndTime());
            }
        }
        return totalHours;
    }

    // --- BREAK VALIDATIONS ---
    public static void validateBreakTimes(
            ShiftDetail shiftDetail,
            GeneralConfigurationService generalConfigurationService, // ✅ Cambiado
            ShiftDetailRepository shiftDetailRepository
    ) {
        // No breaks? salta
        if (shiftDetail.getBreakStartTime() == null && shiftDetail.getBreakEndTime() == null) return;

        // Si se proporciona uno, ambos deben estar presentes
        if (shiftDetail.getBreakStartTime() == null || shiftDetail.getBreakEndTime() == null) {
            throw new IllegalArgumentException("Si se define un break, tanto la hora de inicio como la de fin son obligatorias.");
        }

        // Formato
        if (!isValidMilitaryTime(shiftDetail.getBreakStartTime())) {
            throw new IllegalArgumentException("La hora de inicio del break debe estar en formato HH:mm (hora militar).");
        }
        if (!isValidMilitaryTime(shiftDetail.getBreakEndTime())) {
            throw new IllegalArgumentException("La hora de fin del break debe estar en formato HH:mm (hora militar).");
        }

        // Validación mejorada para breaks
        LocalTime breakStart = LocalTime.parse(shiftDetail.getBreakStartTime());
        LocalTime breakEnd = LocalTime.parse(shiftDetail.getBreakEndTime());

        // Los breaks no deben cruzar medianoche
        if (breakEnd.isBefore(breakStart)) {
            throw new IllegalArgumentException("El break no puede cruzar la medianoche.");
        }

        if (breakEnd.equals(breakStart)) {
            throw new IllegalArgumentException("La hora de fin del break debe ser posterior a la hora de inicio.");
        }

        // Duración positiva
        int breakMinutes = calculateBreakMinutes(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime());
        if (breakMinutes <= 0) {
            throw new IllegalArgumentException("La duración del break debe ser mayor a 0 minutos.");
        }

        // ✅ ACTUALIZADO: Obtener límite de break desde GeneralConfiguration
        Integer maxBreakMinutes = getCurrentBreakMinutes(generalConfigurationService);
        validateTotalShiftBreaks(shiftDetail, breakMinutes, maxBreakMinutes, shiftDetailRepository);

        // El break debe estar dentro del horario laboral
        validateBreakWithinWorkingHours(shiftDetail);
    }

    // ✅ NUEVO: Obtener minutos de break desde GeneralConfiguration
    private static Integer getCurrentBreakMinutes(GeneralConfigurationService configService) {
        try {
            String breakValue = configService.getByType("BREAK").getValue();

            if (breakValue.matches("^\\d+$")) {
                // Caso "30"
                return Integer.parseInt(breakValue);
            } else if (breakValue.toLowerCase().contains("minute")) {
                // Caso "30 minutes"
                return Integer.parseInt(breakValue.replaceAll("[^0-9]", ""));
            } else if (breakValue.matches("^\\d+:\\d+$")) {
                // Caso "00:30"
                String[] parts = breakValue.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60 + minutes;
            } else {
                // Fallback por defecto
                return 60; // 60 minutos por defecto
            }
        } catch (IllegalArgumentException e) {
            // Si no hay configuración BREAK, usar fallback
            return 60; // 60 minutos por defecto
        }
    }

    // Duración del break
    public static int calculateBreakMinutes(String breakStart, String breakEnd) {
        LocalTime start = LocalTime.parse(breakStart);
        LocalTime end = LocalTime.parse(breakEnd);
        return (int) ChronoUnit.MINUTES.between(start, end);
    }

    // Total breaks
    public static void validateTotalShiftBreaks(
            ShiftDetail current,
            int currentBreakMinutes,
            int maxTotalBreakMinutes,
            ShiftDetailRepository shiftDetailRepository
    ) {
        if (current.getShift() == null || current.getShift().getId() == null) {
            if (currentBreakMinutes > maxTotalBreakMinutes) {
                throw new IllegalArgumentException(
                        String.format("El break de %d minutos excede el máximo permitido de %d minutos.",
                                currentBreakMinutes, maxTotalBreakMinutes));
            }
            return;
        }

        List<ShiftDetail> details = shiftDetailRepository.findByShiftId(current.getShift().getId());
        int totalExistingBreakMinutesForDay = 0;
        Integer dayOfWeek = current.getDayOfWeek();

        for (ShiftDetail detail : details) {
            if (!detail.getDayOfWeek().equals(dayOfWeek)) continue;
            if (current.getId() != null && current.getId().equals(detail.getId())) continue;
            if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                totalExistingBreakMinutesForDay += calculateBreakMinutes(detail.getBreakStartTime(), detail.getBreakEndTime());
            }
        }

        int totalWithNewBreakForDay = totalExistingBreakMinutesForDay + currentBreakMinutes;
        if (totalWithNewBreakForDay > maxTotalBreakMinutes) {
            throw new IllegalArgumentException(
                    String.format("Break rechazado. Total de breaks existentes en este día: %d min. Break actual: %d min. Total: %d min. Máximo permitido: %d min.",
                            totalExistingBreakMinutesForDay, currentBreakMinutes, totalWithNewBreakForDay, maxTotalBreakMinutes));
        }
    }

    // Break debe estar dentro del horario de trabajo (actualizado para turnos nocturnos)
    public static void validateBreakWithinWorkingHours(ShiftDetail shiftDetail) {
        LocalTime workStart = LocalTime.parse(shiftDetail.getStartTime());
        LocalTime workEnd = LocalTime.parse(shiftDetail.getEndTime());
        LocalTime breakStart = LocalTime.parse(shiftDetail.getBreakStartTime());
        LocalTime breakEnd = LocalTime.parse(shiftDetail.getBreakEndTime());

        boolean shiftCrossesMidnight = workEnd.isBefore(workStart);

        if (shiftCrossesMidnight) {
            // Para turnos que cruzan medianoche, el break debe estar en la parte PM del turno
            // o en la parte AM, pero no cruzar medianoche
            boolean breakInPMPortion = breakStart.isAfter(workStart) || breakStart.equals(workStart);
            boolean breakInAMPortion = breakEnd.isBefore(workEnd) || breakEnd.equals(workEnd);

            if (!breakInPMPortion && !breakInAMPortion) {
                throw new IllegalArgumentException("El break debe estar completamente dentro del horario de trabajo.");
            }
        } else {
            // Turno normal: el break debe estar entre inicio y fin
            if (breakStart.isBefore(workStart) || breakStart.equals(workStart)) {
                throw new IllegalArgumentException("El break no puede comenzar al mismo tiempo o antes que el inicio del turno.");
            }
            if (breakEnd.isAfter(workEnd) || breakEnd.equals(workEnd)) {
                throw new IllegalArgumentException("El break no puede terminar al mismo tiempo o después que el fin del turno.");
            }
        }
    }
}
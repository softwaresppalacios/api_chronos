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
            GeneralConfigurationService generalConfigurationService,
            ShiftDetailRepository shiftDetailRepository
    ) {
        // ==========================================
        // PASO 1: VALIDACIONES BÁSICAS DE ENTRADA
        // ==========================================

        validateShiftReference(shiftDetail);
        validateDayOfWeek(shiftDetail);

        // ==========================================
        // PASO 2: VALIDACIONES DE FORMATO (NUNCA CONFIAR EN FRONTEND)
        // ==========================================

        validateReceivedTimeFormats(shiftDetail);

        // ==========================================
        // PASO 3: VALIDACIONES DE NEGOCIO (RECALCULAR TODO)
        // ==========================================

        validateTimeLogic(shiftDetail, generalConfigurationService);
        validateWeeklyHours(shiftDetail, shiftDetailRepository, generalConfigurationService);
        validateBreakTimes(shiftDetail, generalConfigurationService, shiftDetailRepository);
    }

    // ==========================================
    // VALIDACIONES BÁSICAS DE ENTRADA
    // ==========================================

    private static void validateShiftReference(ShiftDetail shiftDetail) {
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) {
            throw new IllegalArgumentException("El turno (Shift) es obligatorio.");
        }
    }

    private static void validateDayOfWeek(ShiftDetail shiftDetail) {
        if (shiftDetail.getDayOfWeek() == null ||
                shiftDetail.getDayOfWeek() < 1 ||
                shiftDetail.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("El día de la semana debe ser entre 1 (Lunes) y 7 (Domingo).");
        }
    }

    // ==========================================
    // VALIDACIONES DE FORMATO - NUNCA CONFIAR EN FRONTEND
    // ==========================================

    private static void validateReceivedTimeFormats(ShiftDetail shiftDetail) {
        // Validar que las horas básicas existan
        if (shiftDetail.getStartTime() == null || shiftDetail.getStartTime().trim().isEmpty()) {
            throw new IllegalArgumentException("La hora de inicio es obligatoria.");
        }

        if (shiftDetail.getEndTime() == null || shiftDetail.getEndTime().trim().isEmpty()) {
            throw new IllegalArgumentException("La hora de fin es obligatoria.");
        }

        // Validar formato militar estricto
        if (!isValidMilitaryTime(shiftDetail.getStartTime())) {
            throw new IllegalArgumentException(
                    String.format("La hora de inicio '%s' no tiene formato HH:mm válido.", shiftDetail.getStartTime())
            );
        }

        if (!isValidMilitaryTime(shiftDetail.getEndTime())) {
            throw new IllegalArgumentException(
                    String.format("La hora de fin '%s' no tiene formato HH:mm válido.", shiftDetail.getEndTime())
            );
        }

        // Validar breaks solo si están presentes
        validateBreakFormatsIfPresent(shiftDetail);

        // Validar que las horas sean parseables
        validateTimesParseable(shiftDetail);
    }

    private static void validateBreakFormatsIfPresent(ShiftDetail shiftDetail) {
        String breakStart = shiftDetail.getBreakStartTime();
        String breakEnd = shiftDetail.getBreakEndTime();

        // Si hay break start pero no break end, es error
        if (breakStart != null && !breakStart.trim().isEmpty() &&
                (breakEnd == null || breakEnd.trim().isEmpty())) {
            throw new IllegalArgumentException("Si se proporciona hora de inicio de break, la hora de fin es obligatoria.");
        }

        // Si hay break end pero no break start, es error
        if (breakEnd != null && !breakEnd.trim().isEmpty() &&
                (breakStart == null || breakStart.trim().isEmpty())) {
            throw new IllegalArgumentException("Si se proporciona hora de fin de break, la hora de inicio es obligatoria.");
        }

        // Si ambos están presentes, validar formato
        if (breakStart != null && !breakStart.trim().isEmpty()) {
            if (!isValidMilitaryTime(breakStart)) {
                throw new IllegalArgumentException(
                        String.format("La hora de inicio de break '%s' no tiene formato HH:mm válido.", breakStart)
                );
            }
        }

        if (breakEnd != null && !breakEnd.trim().isEmpty()) {
            if (!isValidMilitaryTime(breakEnd)) {
                throw new IllegalArgumentException(
                        String.format("La hora de fin de break '%s' no tiene formato HH:mm válido.", breakEnd)
                );
            }
        }
    }

    private static void validateTimesParseable(ShiftDetail shiftDetail) {
        try {
            LocalTime.parse(shiftDetail.getStartTime());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("La hora de inicio '%s' no se puede interpretar como hora válida.", shiftDetail.getStartTime())
            );
        }

        try {
            LocalTime.parse(shiftDetail.getEndTime());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("La hora de fin '%s' no se puede interpretar como hora válida.", shiftDetail.getEndTime())
            );
        }

        // Validar breaks si están presentes
        if (shiftDetail.getBreakStartTime() != null && !shiftDetail.getBreakStartTime().trim().isEmpty()) {
            try {
                LocalTime.parse(shiftDetail.getBreakStartTime());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        String.format("La hora de inicio de break '%s' no se puede interpretar como hora válida.",
                                shiftDetail.getBreakStartTime())
                );
            }
        }

        if (shiftDetail.getBreakEndTime() != null && !shiftDetail.getBreakEndTime().trim().isEmpty()) {
            try {
                LocalTime.parse(shiftDetail.getBreakEndTime());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        String.format("La hora de fin de break '%s' no se puede interpretar como hora válida.",
                                shiftDetail.getBreakEndTime())
                );
            }
        }
    }

    // ==========================================
    // VALIDACIONES DE LÓGICA DE TIEMPO - RECALCULAR TODO
    // ==========================================

    private static void validateTimeLogic(ShiftDetail shiftDetail, GeneralConfigurationService configService) {
        LocalTime startTime = LocalTime.parse(shiftDetail.getStartTime());
        LocalTime endTime = LocalTime.parse(shiftDetail.getEndTime());

        // Calcular duración real (no confiar en frontend)
        long actualDurationHours = calculateShiftDuration(startTime, endTime);

        // Validar duración mínima
        if (actualDurationHours < 2) {
            throw new IllegalArgumentException("El turno no puede durar menos de 2 horas.");
        }

        // Validar contra límites diarios
        validateAgainstDailyLimits(actualDurationHours, configService);

        // Validar coherencia de horario (cruce de medianoche debe ser intencional)
        validateTimeCoherence(startTime, endTime, actualDurationHours);
    }

    private static void validateAgainstDailyLimits(long durationHours, GeneralConfigurationService configService) {
        try {
            String dailyHoursValue = configService.getByType("DAILY_HOURS").getValue();
            double maxDailyHours = parseHoursValue(dailyHoursValue);

            if (durationHours > maxDailyHours) {
                throw new IllegalArgumentException(
                        String.format("La duración del turno (%.1f horas) excede el límite diario configurado de %.1f horas.",
                                (double) durationHours, maxDailyHours));
            }
        } catch (IllegalArgumentException e) {
            // Si no hay configuración DAILY_HOURS, usar límite por defecto
            if (durationHours > 12) {
                throw new IllegalArgumentException("La duración del turno no puede ser mayor a 12 horas.");
            }
        }
    }

    private static void validateTimeCoherence(LocalTime start, LocalTime end, long durationHours) {
        boolean crossesMidnight = end.isBefore(start);

        // Si cruza medianoche, la duración debe ser razonable (no más de 12 horas)
        if (crossesMidnight && durationHours > 12) {
            throw new IllegalArgumentException(
                    String.format("Un turno nocturno de %.1f horas es excesivo. Máximo permitido: 12 horas.",
                            (double) durationHours)
            );
        }

        // Si no cruza medianoche, el fin debe ser posterior al inicio
        if (!crossesMidnight && (end.equals(start) || end.isBefore(start))) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio.");
        }
    }

    // ==========================================
    // VALIDACIONES DE BREAKS - RECALCULAR TODO
    // ==========================================

    public static void validateBreakTimes(
            ShiftDetail shiftDetail,
            GeneralConfigurationService generalConfigurationService,
            ShiftDetailRepository shiftDetailRepository
    ) {
        if (!hasBreakConfigured(shiftDetail)) {
            return; // Sin breaks, no hay nada que validar
        }

        LocalTime breakStart = LocalTime.parse(shiftDetail.getBreakStartTime());
        LocalTime breakEnd = LocalTime.parse(shiftDetail.getBreakEndTime());

        // RECALCULAR duración real del break (no confiar en frontend)
        int actualBreakMinutes = (int) ChronoUnit.MINUTES.between(breakStart, breakEnd);

        if (actualBreakMinutes <= 0) {
            throw new IllegalArgumentException("La duración del break debe ser mayor a 0 minutos.");
        }

        // Validar que el break esté dentro del horario laboral
        validateBreakWithinWorkingHours(shiftDetail, breakStart, breakEnd);

        // Validar contra límites de break configurados
        validateBreakAgainstLimits(shiftDetail, actualBreakMinutes, generalConfigurationService, shiftDetailRepository);
    }

    private static boolean hasBreakConfigured(ShiftDetail shiftDetail) {
        return shiftDetail.getBreakStartTime() != null &&
                !shiftDetail.getBreakStartTime().trim().isEmpty() &&
                shiftDetail.getBreakEndTime() != null &&
                !shiftDetail.getBreakEndTime().trim().isEmpty();
    }

    private static void validateBreakWithinWorkingHours(ShiftDetail shiftDetail, LocalTime breakStart, LocalTime breakEnd) {
        LocalTime workStart = LocalTime.parse(shiftDetail.getStartTime());
        LocalTime workEnd = LocalTime.parse(shiftDetail.getEndTime());

        boolean shiftCrossesMidnight = workEnd.isBefore(workStart);
        boolean breakCrossesMidnight = breakEnd.isBefore(breakStart);

        // Los breaks no pueden cruzar medianoche
        if (breakCrossesMidnight) {
            throw new IllegalArgumentException("El break no puede cruzar la medianoche.");
        }

        if (shiftCrossesMidnight) {
            // Para turnos nocturnos, el break debe estar en una de las dos partes del turno
            boolean breakInFirstPart = breakStart.isAfter(workStart) || breakStart.equals(workStart);
            boolean breakInSecondPart = breakEnd.isBefore(workEnd) || breakEnd.equals(workEnd);

            if (!breakInFirstPart && !breakInSecondPart) {
                throw new IllegalArgumentException("El break debe estar completamente dentro del horario de trabajo.");
            }
        } else {
            // Turno normal: break debe estar estrictamente dentro del horario
            if (breakStart.isBefore(workStart) || breakStart.equals(workStart)) {
                throw new IllegalArgumentException("El break no puede comenzar al mismo tiempo o antes que el turno.");
            }
            if (breakEnd.isAfter(workEnd) || breakEnd.equals(workEnd)) {
                throw new IllegalArgumentException("El break no puede terminar al mismo tiempo o después que el turno.");
            }
        }
    }

    private static void validateBreakAgainstLimits(
            ShiftDetail shiftDetail,
            int actualBreakMinutes,
            GeneralConfigurationService configService,
            ShiftDetailRepository repository
    ) {
        Integer maxBreakMinutes = getCurrentBreakMinutes(configService);

        // Calcular total de breaks para este día (RECALCULAR, no confiar en frontend)
        int totalBreakMinutesForDay = calculateTotalBreakMinutesForDay(
                shiftDetail, actualBreakMinutes, repository
        );

        if (totalBreakMinutesForDay > maxBreakMinutes) {
            throw new IllegalArgumentException(
                    String.format("Total de breaks en este día (%d min) excede el límite permitido de %d min.",
                            totalBreakMinutesForDay, maxBreakMinutes));
        }
    }

    private static int calculateTotalBreakMinutesForDay(
            ShiftDetail currentShiftDetail,
            int currentBreakMinutes,
            ShiftDetailRepository repository
    ) {
        if (currentShiftDetail.getShift() == null || currentShiftDetail.getShift().getId() == null) {
            return currentBreakMinutes;
        }

        List<ShiftDetail> existingDetails = repository.findByShiftIdAndDayOfWeek(
                currentShiftDetail.getShift().getId(),
                currentShiftDetail.getDayOfWeek()
        );

        int totalExisting = 0;
        for (ShiftDetail detail : existingDetails) {
            // Saltar el detalle actual si es una actualización
            if (currentShiftDetail.getId() != null &&
                    currentShiftDetail.getId().equals(detail.getId())) {
                continue;
            }

            if (hasBreakConfigured(detail)) {
                // RECALCULAR duración real de cada break existente
                LocalTime breakStart = LocalTime.parse(detail.getBreakStartTime());
                LocalTime breakEnd = LocalTime.parse(detail.getBreakEndTime());
                int breakMinutes = (int) ChronoUnit.MINUTES.between(breakStart, breakEnd);
                totalExisting += breakMinutes;
            }
        }

        return totalExisting + currentBreakMinutes;
    }

    // ==========================================
    // VALIDACIONES DE HORAS SEMANALES - RECALCULAR TODO
    // ==========================================

    public static void validateWeeklyHours(
            ShiftDetail shiftDetail,
            ShiftDetailRepository shiftDetailRepository,
            GeneralConfigurationService generalConfigurationService
    ) {
        // RECALCULAR horas reales del turno actual (no confiar en frontend)
        LocalTime start = LocalTime.parse(shiftDetail.getStartTime());
        LocalTime end = LocalTime.parse(shiftDetail.getEndTime());
        int currentShiftHours = (int) calculateShiftDuration(start, end);

        // RECALCULAR total de horas ya programadas
        int totalScheduledHours = recalculateTotalScheduledHours(shiftDetail, shiftDetailRepository);
        int totalWithNew = totalScheduledHours + currentShiftHours;

        // Obtener límite real de configuración
        int exactWeeklyHours = getExactWeeklyHoursFromConfig(generalConfigurationService);

        if (totalWithNew > exactWeeklyHours) {
            throw new IllegalArgumentException(
                    String.format("El total de horas semanales (%d) excedería las %d horas exactas requeridas. " +
                                    "Actualmente programadas: %d horas. Este turno: %d horas.",
                            totalWithNew, exactWeeklyHours, totalScheduledHours, currentShiftHours));
        }
    }

    private static int recalculateTotalScheduledHours(
            ShiftDetail currentShiftDetail,
            ShiftDetailRepository repository
    ) {
        if (currentShiftDetail.getShift() == null || currentShiftDetail.getShift().getId() == null) {
            return 0;
        }

        List<ShiftDetail> allDetails = repository.findByShiftId(currentShiftDetail.getShift().getId());
        int total = 0;

        for (ShiftDetail detail : allDetails) {
            // Saltar el detalle actual si es una actualización
            if (currentShiftDetail.getId() != null &&
                    currentShiftDetail.getId().equals(detail.getId())) {
                continue;
            }

            if (detail.getStartTime() != null && detail.getEndTime() != null) {
                // RECALCULAR horas reales de cada detalle existente
                try {
                    LocalTime start = LocalTime.parse(detail.getStartTime());
                    LocalTime end = LocalTime.parse(detail.getEndTime());
                    total += (int) calculateShiftDuration(start, end);
                } catch (Exception e) {
                    // Si hay datos corruptos en BD, logear pero continuar
                    System.err.println("Datos de tiempo corruptos para ShiftDetail ID: " + detail.getId());
                }
            }
        }

        return total;
    }

    // ==========================================
    // MÉTODOS DE UTILIDAD
    // ==========================================

    public static boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        return Pattern.matches("^([01]?[0-9]|2[0-3]):([0-5][0-9])$", time);
    }

    private static long calculateShiftDuration(LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            // Cruza medianoche
            return ChronoUnit.HOURS.between(start, LocalTime.MAX) +
                    ChronoUnit.HOURS.between(LocalTime.MIN, end) + 1;
        } else {
            return ChronoUnit.HOURS.between(start, end);
        }
    }

    private static double parseHoursValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Valor de horas vacío");
        }

        String cleanValue = value.trim();

        if (cleanValue.contains(":")) {
            // Formato HH:MM
            String[] parts = cleanValue.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Formato de horas inválido: " + value);
            }

            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            return hours + (minutes / 60.0);
        } else {
            // Formato decimal
            return Double.parseDouble(cleanValue);
        }
    }

    public static int getExactWeeklyHoursFromConfig(GeneralConfigurationService configService) {
        try {
            String hoursStr = configService.getByType("WEEKLY_HOURS").getValue();

            if (hoursStr == null) {
                throw new IllegalStateException("No se encontró configuración de horas semanales.");
            }

            double hoursDecimal = parseHoursValue(hoursStr);
            int exactHours = (int) Math.floor(hoursDecimal);

            if (exactHours <= 0) {
                throw new IllegalStateException("Las horas semanales deben ser mayor a 0");
            }

            return exactHours;
        } catch (Exception e) {
            throw new IllegalStateException("Error obteniendo configuración de horas semanales: " + e.getMessage());
        }
    }

    private static Integer getCurrentBreakMinutes(GeneralConfigurationService configService) {
        try {
            String breakValue = configService.getByType("BREAK").getValue();
            return Integer.parseInt(breakValue);
        } catch (Exception e) {
            return 60; // Fallback por defecto
        }
    }

    // ==========================================
    // MÉTODOS DEPRECATED - MANTENER POR COMPATIBILIDAD
    // ==========================================

    @Deprecated
    public static int calculateShiftHours(String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        return (int) calculateShiftDuration(startTime, endTime);
    }

    @Deprecated
    public static int getTotalScheduledHoursForShift(ShiftDetail shiftDetail, ShiftDetailRepository shiftDetailRepository) {
        return recalculateTotalScheduledHours(shiftDetail, shiftDetailRepository);
    }

    @Deprecated
    public static int calculateBreakMinutes(String breakStart, String breakEnd) {
        LocalTime start = LocalTime.parse(breakStart);
        LocalTime end = LocalTime.parse(breakEnd);
        return (int) ChronoUnit.MINUTES.between(start, end);
    }
}
package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.controller.shift.ValidationController;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private final GeneralConfigurationService generalConfigurationService;
    private static final Pattern MILITARY_TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):([0-5][0-9])$");
    private static final Pattern AMPM_TIME_PATTERN = Pattern.compile("^([0]?[1-9]|1[0-2]):[0-5][0-9]\\s*(AM|PM)$");

    public ValidationService(GeneralConfigurationService generalConfigurationService) {
        this.generalConfigurationService = generalConfigurationService;
    }

    // ==========================================
    // VALIDACIÓN DE RANGOS DE TIEMPO
    // ==========================================

// VERIFICAR en tu ValidationService.java que el método validateTimeRange
// esté devolviendo errores específicos como estos:

    public Map<String, Object> validateTimeRange(String period, String startTime, String endTime, Double hoursPerDay) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Convertir tiempos AM/PM a formato militar
            String start24h = convertAmPmTo24h(startTime);
            String end24h = convertAmPmTo24h(endTime);

            // ✅ VALIDAR FORMATOS
            if (!isValidMilitaryTime(start24h) || !isValidMilitaryTime(end24h)) {
                result.put("isValid", false);
                result.put("error", "Formato de hora inválido. Use HH:MM AM/PM");
                result.put("hours", 0.0);
                result.put("period", period);
                return result;
            }

            LocalTime startLocalTime = LocalTime.parse(start24h);
            LocalTime endLocalTime = LocalTime.parse(end24h);

            // ✅ CALCULAR DURACIÓN
            double hours = calculateHoursDuration(startLocalTime, endLocalTime);

            // ✅ VALIDAR COHERENCIA DEL PERÍODO
            try {
                validatePeriodCoherence(period, start24h, end24h);
            } catch (IllegalArgumentException e) {
                result.put("isValid", false);
                result.put("error", e.getMessage()); // Mensaje específico del error
                result.put("hours", 0.0);
                result.put("period", period);
                return result;
            }

            // ✅ VALIDAR LÍMITES DIARIOS
            if (hoursPerDay != null && hoursPerDay > 0 && hours > hoursPerDay) {
                result.put("isValid", false);
                result.put("error", String.format("Las %.1f horas exceden el límite diario de %.1f horas",
                        hours, hoursPerDay));
                result.put("hours", hours);
                result.put("period", period);
                return result;
            }

            // ✅ VERIFICAR WARNING NOCTURNO
            String nightWarning = checkNightShiftWarning(start24h, end24h);

            // ✅ RESPUESTA EXITOSA
            result.put("isValid", true);
            result.put("hours", hours);
            result.put("period", period);
            result.put("startTime24h", start24h);
            result.put("endTime24h", end24h);

            if (nightWarning != null) {
                result.put("warning", nightWarning);
            }

        } catch (Exception e) {
            // ✅ ERROR ESPECÍFICO
            result.put("isValid", false);
            result.put("error", "Error procesando horario: " + e.getMessage());
            result.put("hours", 0.0);
            result.put("period", period);
        }

        return result;
    }
    public Map<String, Object> validateDailyTimeRanges(
            Map<String, ValidationController.DailyRangesValidationRequest.TimeRangeData> timeRanges,
            Double hoursPerDay) {

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> periodResults = new HashMap<>();
        double totalDayHours = 0.0;
        boolean allValid = true;

        try {
            for (Map.Entry<String, ValidationController.DailyRangesValidationRequest.TimeRangeData> entry : timeRanges.entrySet()) {
                String period = entry.getKey();
                ValidationController.DailyRangesValidationRequest.TimeRangeData range = entry.getValue();

                if (range.getStart() != null && !range.getStart().trim().isEmpty() &&
                        range.getEnd() != null && !range.getEnd().trim().isEmpty()) {

                    Map<String, Object> periodResult = validateTimeRange(
                            period, range.getStart(), range.getEnd(), null
                    );

                    periodResults.put(period, periodResult);

                    if ((Boolean) periodResult.get("isValid")) {
                        totalDayHours += (Double) periodResult.get("hours");
                    } else {
                        allValid = false;
                    }
                }
            }

            // Validar total del día
            if (hoursPerDay != null && hoursPerDay > 0 && totalDayHours > hoursPerDay) {
                allValid = false;
                result.put("error", String.format(
                        "Total de horas del día (%.1f) excede el límite de %.1f horas",
                        totalDayHours, hoursPerDay
                ));
            }

            result.put("isValid", allValid);
            result.put("totalHours", totalDayHours);
            result.put("periodResults", periodResults);

        } catch (Exception e) {
            result.put("isValid", false);
            result.put("error", e.getMessage());
            result.put("totalHours", 0.0);
        }

        return result;
    }

    // ==========================================
    // VALIDACIÓN DE BREAKS
    // ==========================================

    public Map<String, Object> validateBreak(String period, String breakStart, String breakEnd,
                                             String workStart, String workEnd) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Convertir todos los tiempos a formato 24h
            String breakStart24h = convertAmPmTo24h(breakStart);
            String breakEnd24h = convertAmPmTo24h(breakEnd);
            String workStart24h = convertAmPmTo24h(workStart);
            String workEnd24h = convertAmPmTo24h(workEnd);

            LocalTime breakStartTime = LocalTime.parse(breakStart24h);
            LocalTime breakEndTime = LocalTime.parse(breakEnd24h);
            LocalTime workStartTime = LocalTime.parse(workStart24h);
            LocalTime workEndTime = LocalTime.parse(workEnd24h);

            // Validar que el break esté dentro del horario laboral
            validateBreakWithinWorkHours(breakStartTime, breakEndTime, workStartTime, workEndTime);

            // Validar coherencia del período
            validateBreakPeriodCoherence(period, breakStart);

            // Calcular duración del break
            long breakMinutes = ChronoUnit.MINUTES.between(breakStartTime, breakEndTime);

            result.put("isValid", true);
            result.put("breakDurationMinutes", breakMinutes);
            result.put("breakStart24h", breakStart24h);
            result.put("breakEnd24h", breakEnd24h);

        } catch (Exception e) {
            result.put("isValid", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // ==========================================
    // CÁLCULO DE HORAS
    // ==========================================

    public Map<String, Object> calculateTotalHours(List<ValidationController.HoursCalculationRequest.ShiftDetailData> shiftDetails) {
        Map<String, Object> result = new HashMap<>();

        try {
            double totalHours = 0.0;
            int totalMinutes = 0;
            int totalBreakMinutes = 0;

            for (ValidationController.HoursCalculationRequest.ShiftDetailData detail : shiftDetails) {
                // Convertir tiempos a 24h
                String start24h = convertAmPmTo24h(detail.getStartTime());
                String end24h = convertAmPmTo24h(detail.getEndTime());

                LocalTime startTime = LocalTime.parse(start24h);
                LocalTime endTime = LocalTime.parse(end24h);

                // Calcular duración laboral
                long workMinutes = calculateMinutesDuration(startTime, endTime);

                // Restar break si existe
                if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                    String breakStart24h = convertAmPmTo24h(detail.getBreakStartTime());
                    String breakEnd24h = convertAmPmTo24h(detail.getBreakEndTime());

                    LocalTime breakStart = LocalTime.parse(breakStart24h);
                    LocalTime breakEnd = LocalTime.parse(breakEnd24h);

                    long breakDuration = ChronoUnit.MINUTES.between(breakStart, breakEnd);
                    workMinutes -= breakDuration;
                    totalBreakMinutes += breakDuration;
                }

                totalMinutes += workMinutes;
            }

            totalHours = totalMinutes / 60.0;

            result.put("totalHours", totalHours);
            result.put("totalMinutes", totalMinutes);
            result.put("totalBreakMinutes", totalBreakMinutes);
            result.put("netWorkMinutes", totalMinutes);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("totalHours", 0.0);
            result.put("totalMinutes", 0);
        }

        return result;
    }

    // ==========================================
    // CONVERSIÓN DE FORMATOS
    // ==========================================

    public Map<String, Object> convertTimeFormat(String time, String targetFormat) {
        Map<String, Object> result = new HashMap<>();

        try {
            String convertedTime;

            if ("24h".equals(targetFormat)) {
                convertedTime = convertAmPmTo24h(time);
            } else if ("12h".equals(targetFormat)) {
                convertedTime = convert24hToAmPm(time);
            } else {
                throw new IllegalArgumentException("Formato objetivo debe ser '24h' o '12h'");
            }

            result.put("originalTime", time);
            result.put("convertedTime", convertedTime);
            result.put("targetFormat", targetFormat);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("convertedTime", "");
        }

        return result;
    }

    // ==========================================
    // MÉTODOS AUXILIARES PRIVADOS
    // ==========================================

    private String convertAmPmTo24h(String time12h) {
        if (time12h == null || time12h.trim().isEmpty()) {
            throw new IllegalArgumentException("Tiempo no puede estar vacío");
        }

        String cleaned = time12h.trim().toUpperCase().replaceAll("\\s+", " ");

        if (!AMPM_TIME_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("Formato inválido: " + time12h + ". Use HH:MM AM/PM");
        }

        String[] parts = cleaned.split("\\s+");
        String timePart = parts[0];
        String amPm = parts[1];

        String[] timeParts = timePart.split(":");
        int hours = Integer.parseInt(timeParts[0]);
        int minutes = Integer.parseInt(timeParts[1]);

        if ("AM".equals(amPm) && hours == 12) {
            hours = 0;
        } else if ("PM".equals(amPm) && hours != 12) {
            hours += 12;
        }

        return String.format("%02d:%02d", hours, minutes);
    }

    private String convert24hToAmPm(String time24h) {
        if (!isValidMilitaryTime(time24h)) {
            throw new IllegalArgumentException("Formato 24h inválido: " + time24h);
        }

        LocalTime time = LocalTime.parse(time24h);
        return time.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }

    private boolean isValidMilitaryTime(String time) {
        return time != null && MILITARY_TIME_PATTERN.matcher(time).matches();
    }

    private double calculateHoursDuration(LocalTime start, LocalTime end) {
        long minutes = calculateMinutesDuration(start, end);
        return minutes / 60.0;
    }

    private long calculateMinutesDuration(LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            // Cruza medianoche
            return ChronoUnit.MINUTES.between(start, LocalTime.MAX) +
                    ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
        } else {
            return ChronoUnit.MINUTES.between(start, end);
        }
    }

    private void validatePeriodCoherence(String period, String start24h, String end24h) {
        LocalTime start = LocalTime.parse(start24h);
        LocalTime end = LocalTime.parse(end24h);
        boolean crossesMidnight = end.isBefore(start);

        LocalTime noon = LocalTime.NOON; // 12:00
        LocalTime nightStart = getNightStart(); // p.ej. 19:00 por config

        switch (period) {
            case "Mañana":
                // Solo verificar que inicie en AM (antes de 12:00 PM)
                if (!start.isBefore(noon)) {
                    throw new IllegalArgumentException("Mañana debe iniciar antes de 12:00 PM");
                }
                // No cruzar medianoche
                if (crossesMidnight) {
                    throw new IllegalArgumentException("Mañana no puede cruzar medianoche");
                }
                // Permitir que termine hasta las 6:59 PM (más flexible)
                // La restricción anterior era demasiado estricta
                break;

            case "Tarde":
                // Debe iniciar desde 12:00 PM y antes del inicio de noche
                if (start.isBefore(noon)) {
                    throw new IllegalArgumentException("Tarde debe iniciar a partir de 12:00 PM");
                }
                if (!start.isBefore(nightStart)) {
                    throw new IllegalArgumentException(
                            "Tarde debe iniciar antes de " + formatAmPm(nightStart));
                }
                // No debería cruzar medianoche
                if (crossesMidnight) {
                    throw new IllegalArgumentException("Tarde no puede cruzar medianoche");
                }
                // Debe finalizar antes (o igual) al inicio de noche
                if (end.isAfter(nightStart)) {
                    throw new IllegalArgumentException(
                            "Tarde no debe extenderse más allá de " + formatAmPm(nightStart));
                }
                break;

            case "Noche":
                // Debe empezar desde NIGHT_START o cruzar medianoche
                if (!crossesMidnight && start.isBefore(nightStart)) {
                    throw new IllegalArgumentException(
                            "Noche debe comenzar a partir de " + formatAmPm(nightStart) + " o cruzar medianoche");
                }
                break;

            default:
                // sin restricciones especiales
        }
    }

    private LocalTime getNightStart() {
        try {
            String nightStartConfig = generalConfigurationService.getByType("NIGHT_START").getValue();
            return LocalTime.parse(nightStartConfig); // ejemplo "19:00"
        } catch (Exception e) {
            return LocalTime.of(19, 0); // fallback
        }
    }

    private String formatAmPm(LocalTime t) {
        return t.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }



    private String checkNightShiftWarning(String start24h, String end24h) {
        try {
            String nightStartConfig = generalConfigurationService.getByType("NIGHT_START").getValue();
            LocalTime nightStartTime = LocalTime.parse(nightStartConfig);
            LocalTime startTime = LocalTime.parse(start24h);
            LocalTime endTime = LocalTime.parse(end24h);

            // Verificar si el turno incluye horario nocturno
            boolean includesNightHours = false;

            if (endTime.isBefore(startTime)) {
                // Cruza medianoche - siempre incluye horario nocturno
                includesNightHours = true;
            } else if (startTime.isAfter(nightStartTime) || startTime.equals(nightStartTime) ||
                    endTime.isAfter(nightStartTime)) {
                includesNightHours = true;
            }

            if (includesNightHours) {
                String displayTime = convert24hToAmPm(nightStartConfig);
                return "Desde las " + displayTime + " comienza la jornada nocturna";
            }

        } catch (Exception e) {
            // Si no se puede obtener la configuración, no mostrar warning
        }

        return null;
    }

    private void validateBreakWithinWorkHours(LocalTime breakStart, LocalTime breakEnd,
                                              LocalTime workStart, LocalTime workEnd) {
        boolean shiftCrossesMidnight = workEnd.isBefore(workStart);
        boolean breakCrossesMidnight = breakEnd.isBefore(breakStart);

        if (breakCrossesMidnight) {
            throw new IllegalArgumentException("El break no puede cruzar medianoche");
        }

        if (shiftCrossesMidnight) {
            // Para turnos nocturnos
            boolean breakInFirstPart = !breakStart.isBefore(workStart);
            boolean breakInSecondPart = !breakEnd.isAfter(workEnd);

            if (!breakInFirstPart && !breakInSecondPart) {
                throw new IllegalArgumentException("El break debe estar dentro del horario laboral");
            }
        } else {
            // Para turnos normales
            if (breakStart.isBefore(workStart) || breakStart.equals(workStart) ||
                    breakEnd.isAfter(workEnd) || breakEnd.equals(workEnd)) {
                throw new IllegalArgumentException("El break debe estar estrictamente dentro del horario laboral");
            }
        }
    }

    private void validateBreakPeriodCoherence(String period, String breakStartRaw) {
        String breakStart24h = convertAmPmTo24h(breakStartRaw);
        LocalTime b = LocalTime.parse(breakStart24h);
        LocalTime noon = LocalTime.NOON;
        LocalTime nightStart = getNightStart();

        if ("Mañana".equals(period)) {
            // Permitir AM y también PM mientras sea antes de NIGHT_START
            if (!b.isBefore(nightStart)) {
                throw new IllegalArgumentException("El break de Mañana debe ser antes de " + formatAmPm(nightStart));
            }
        } else if ("Tarde".equals(period)) {
            // Entre 12:00 PM y antes de NIGHT_START
            if (b.isBefore(noon) || !b.isBefore(nightStart)) {
                throw new IllegalArgumentException(
                        "El break de Tarde debe ser entre 12:00 PM y " + formatAmPm(nightStart));
            }
        } else if ("Noche".equals(period)) {
            // A partir de NIGHT_START (si rompe las 00:00 ya lo bloqueas en otra regla)
            if (b.isBefore(nightStart)) {
                throw new IllegalArgumentException("El break de Noche debe ser a partir de " + formatAmPm(nightStart));
            }
        }
    }

}
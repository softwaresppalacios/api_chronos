package sp.sistemaspalacios.api_chronos.validator.shift;

import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.boundaries.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.repository.boundaries.weeklyHours.WeeklyHoursRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.breakConfiguration.BreakConfigurationService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class ShiftDetailValidator {

    public static void validateShiftDetail(
            ShiftDetail shiftDetail,
            BreakConfigurationService breakConfigurationService,
            ShiftDetailRepository shiftDetailRepository,
            WeeklyHoursRepository weeklyHoursRepository
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
        // 5. Orden cronológico
        if (isStartTimeAfterEndTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de inicio no puede ser posterior a la hora de fin.");
        }
        // 6. Hora fin mayor que inicio
        if (isEndTimeBeforeStartTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin no puede ser menor o igual a la hora de inicio.");
        }
        // 7. Máximo diario (9 horas)
        if (isTimeDifferenceTooLong(shiftDetail.getStartTime(), shiftDetail.getEndTime(), 9)) {
            throw new IllegalArgumentException("La diferencia entre las horas de inicio y fin no puede ser mayor a 9 horas.");
        }
        // 8. Mínimo diario (2 horas)
        if (isTimeDifferenceTooShort(shiftDetail.getStartTime(), shiftDetail.getEndTime(), 2)) {
            throw new IllegalArgumentException("El turno diario no puede ser menor a 2 horas.");
        }
        // 9. Validación de horas semanales
        validateWeeklyHours(shiftDetail, shiftDetailRepository, weeklyHoursRepository);

        // 10. Validaciones de Break
        validateBreakTimes(shiftDetail, breakConfigurationService, shiftDetailRepository);
    }

    // Validar formato HH:mm
    public static boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        String timeRegex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])$";
        return Pattern.matches(timeRegex, time);
    }

    // Hora inicio > hora fin
    public static boolean isStartTimeAfterEndTime(String start, String end) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date dStart = sdf.parse(start);
            Date dEnd = sdf.parse(end);
            return dStart.after(dEnd);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    // Hora fin <= hora inicio
    public static boolean isEndTimeBeforeStartTime(String start, String end) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date dStart = sdf.parse(start);
            Date dEnd = sdf.parse(end);
            return dEnd.before(dStart) || dEnd.equals(dStart);
        } catch (ParseException e) {
            return true;
        }
    }

    // Excede X horas
    public static boolean isTimeDifferenceTooLong(String start, String end, int maxHours) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date dStart = sdf.parse(start);
            Date dEnd = sdf.parse(end);
            long diff = dEnd.getTime() - dStart.getTime();
            long hours = diff / (1000 * 60 * 60);
            return hours > maxHours;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    // Menos de X horas
    public static boolean isTimeDifferenceTooShort(String start, String end, int minHours) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date dStart = sdf.parse(start);
            Date dEnd = sdf.parse(end);
            long diff = dEnd.getTime() - dStart.getTime();
            long hours = diff / (1000 * 60 * 60);
            return hours < minHours;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    // Horas semanales
    public static void validateWeeklyHours(
            ShiftDetail shiftDetail,
            ShiftDetailRepository shiftDetailRepository,
            WeeklyHoursRepository weeklyHoursRepository
    ) {
        int currentShiftHours = calculateShiftHours(shiftDetail.getStartTime(), shiftDetail.getEndTime());
        int exactWeeklyHours = getExactWeeklyHoursFromConfig(weeklyHoursRepository);
        int totalScheduled = getTotalScheduledHoursForShift(shiftDetail, shiftDetailRepository);
        int totalWithNew = totalScheduled + currentShiftHours;

        if (totalWithNew > exactWeeklyHours) {
            throw new IllegalArgumentException(
                    String.format("El total de horas semanales (%d) excedería las %d horas exactas requeridas. Actualmente programadas: %d horas. Este turno: %d horas. Debe ser exactamente %d horas, no más.",
                            totalWithNew, exactWeeklyHours, totalScheduled, currentShiftHours, exactWeeklyHours));
        }
    }

    // Auxiliar: calcular horas (entero)
    public static int calculateShiftHours(String start, String end) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date dStart = sdf.parse(start);
            Date dEnd = sdf.parse(end);
            long diff = dEnd.getTime() - dStart.getTime();
            return (int) (diff / (1000 * 60 * 60));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    public static int getExactWeeklyHoursFromConfig(WeeklyHoursRepository weeklyHoursRepository) {
        List<WeeklyHours> weeklyHoursList = weeklyHoursRepository.findAll();
        if (weeklyHoursList.isEmpty()) {
            throw new IllegalStateException("No se encontró configuración de horas semanales. Debe configurar las horas exactas requeridas.");
        }
        WeeklyHours config = weeklyHoursList.get(0);
        String hoursStr = config.getHours();
        if (hoursStr == null || !hoursStr.contains(":")) {
            throw new IllegalStateException("Formato de horas semanales inválido: " + hoursStr + ". Debe ser formato HH:mm");
        }
        try {
            int exactHours = Integer.parseInt(hoursStr.split(":")[0]);
            if (exactHours <= 0) {
                throw new IllegalStateException("Las horas semanales deben ser mayor a 0");
            }
            return exactHours;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("No se pudo parsear las horas semanales: " + hoursStr);
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
            BreakConfigurationService breakConfigurationService,
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
        // Orden
        if (isStartTimeAfterEndTime(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime())) {
            throw new IllegalArgumentException("La hora de inicio del break no puede ser posterior a la hora de fin del break.");
        }
        // Duración positiva
        int breakMinutes = calculateBreakMinutes(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime());
        if (breakMinutes <= 0) {
            throw new IllegalArgumentException("La duración del break debe ser mayor a 0 minutos.");
        }
        // Duración y total por día
        Integer maxBreakMinutes = breakConfigurationService.getCurrentBreakMinutes();
        validateTotalShiftBreaks(shiftDetail, breakMinutes, maxBreakMinutes, shiftDetailRepository);
        // El break debe estar dentro del horario laboral
        validateBreakWithinWorkingHours(shiftDetail);
    }

    // Duración del break
    public static int calculateBreakMinutes(String breakStart, String breakEnd) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(breakStart);
            Date end = sdf.parse(breakEnd);
            long diff = end.getTime() - start.getTime();
            return (int) (diff / (1000 * 60));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido en break.");
        }
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

    // Break debe estar dentro del horario de trabajo
    public static void validateBreakWithinWorkingHours(ShiftDetail shiftDetail) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date workStart = sdf.parse(shiftDetail.getStartTime());
            Date workEnd = sdf.parse(shiftDetail.getEndTime());
            Date breakStart = sdf.parse(shiftDetail.getBreakStartTime());
            Date breakEnd = sdf.parse(shiftDetail.getBreakEndTime());
            if (breakStart.before(workStart) || breakStart.equals(workStart)) {
                throw new IllegalArgumentException("El break no puede comenzar al mismo tiempo o antes que el inicio del turno.");
            }
            if (breakEnd.after(workEnd) || breakEnd.equals(workEnd)) {
                throw new IllegalArgumentException("El break no puede terminar al mismo tiempo o después que el fin del turno.");
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error al validar las horas del break: " + e.getMessage());
        }
    }
}

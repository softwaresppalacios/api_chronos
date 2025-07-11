package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.repository.weeklyHours.WeeklyHoursRepository;
import sp.sistemaspalacios.api_chronos.service.breakConfiguration.BreakConfigurationService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShiftDetailService {
    @Autowired
    private WeeklyHoursRepository weeklyHoursRepository;

    @Autowired
    private ShiftDetailRepository shiftDetailRepository;

    // 🔹 NUEVA DEPENDENCIA: Para obtener la configuración del break
    @Autowired
    private BreakConfigurationService breakConfigurationService;

    public List<ShiftDetail> getAllShiftDetails() {
        return shiftDetailRepository.findAll();
    }

    public ShiftDetail getShiftDetailById(Long id) {
        return shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));
    }

    public List<ShiftDetail> getShiftDetailsByShiftId(Long shiftId) {
        return shiftDetailRepository.findByShiftId(shiftId);
    }

    public ShiftDetail createShiftDetail(ShiftDetail shiftDetail) {
        // Validaciones previas
        validateShiftDetail(shiftDetail);

        // 🔹 NUEVA VALIDACIÓN: Break
        validateBreakTimes(shiftDetail);

        // Verificación de que las horas no sean nulas
        if (shiftDetail.getStartTime() == null || shiftDetail.getEndTime() == null) {
            throw new IllegalArgumentException("Las horas de inicio y fin no pueden ser nulas.");
        }

        // Verificación de que la hora de inicio no sea posterior a la hora de fin
        if (isStartTimeAfterEndTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de inicio no puede ser posterior a la hora de fin.");
        }

        // Verificación de que no exceda el límite diario máximo (9 horas)
        if (isTimeDifferenceTooLong(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La diferencia entre las horas de inicio y fin no puede ser mayor a 9 horas.");
        }

        // Verificación de que cumpla el mínimo diario (4 horas)
        if (isDailyShiftTooShort(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("El turno diario no puede ser menor a 2 horas.");
        }

        // ✅ VALIDACIÓN CORREGIDA: Horas semanales exactas
        if (isWeeklyHoursExceeded(shiftDetail)) {
            String errorMsg = getWeeklyHoursErrorMessage(shiftDetail);
            throw new IllegalArgumentException(errorMsg);
        }

        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    // 🔹 NUEVA VALIDACIÓN: Validar tiempos de break
    private void validateBreakTimes(ShiftDetail shiftDetail) {
        // Si no se proporcionaron tiempos de break, no validar
        if (shiftDetail.getBreakStartTime() == null && shiftDetail.getBreakEndTime() == null) {
            return; // Break es opcional
        }

        // Si se proporciona uno, ambos deben estar presentes
        if (shiftDetail.getBreakStartTime() == null || shiftDetail.getBreakEndTime() == null) {
            throw new IllegalArgumentException("Si se define un break, tanto la hora de inicio como la de fin son obligatorias.");
        }

        // Validar formato de las horas del break
        if (!isValidMilitaryTime(shiftDetail.getBreakStartTime())) {
            throw new IllegalArgumentException("La hora de inicio del break debe estar en formato HH:mm (hora militar).");
        }
        if (!isValidMilitaryTime(shiftDetail.getBreakEndTime())) {
            throw new IllegalArgumentException("La hora de fin del break debe estar en formato HH:mm (hora militar).");
        }

        // Validar que la hora de inicio del break sea antes que la de fin
        if (isStartTimeAfterEndTime(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime())) {
            throw new IllegalArgumentException("La hora de inicio del break no puede ser posterior a la hora de fin del break.");
        }

        // 🔸 VALIDACIÓN CLAVE: Duración exacta del break según configuración
        validateBreakDuration(shiftDetail);

        // 🔸 VALIDACIÓN: El break debe estar dentro del horario de trabajo
        validateBreakWithinWorkingHours(shiftDetail);
    }

    // 🔹 VALIDAR DURACIÓN EXACTA DEL BREAK
    private void validateBreakDuration(ShiftDetail shiftDetail) {
        try {
            // 🔍 DEBUG INICIAL
            System.out.println("=== INICIO validateBreakDuration ===");
            System.out.println("breakStartTime: " + shiftDetail.getBreakStartTime());
            System.out.println("breakEndTime: " + shiftDetail.getBreakEndTime());
            System.out.println("shiftId: " + (shiftDetail.getShift() != null ? shiftDetail.getShift().getId() : "NULL"));
            System.out.println("currentShiftDetail ID: " + shiftDetail.getId());

            // Obtener los minutos configurados para el break (30)
            Integer configuredBreakMinutes = breakConfigurationService.getCurrentBreakMinutes();

            // Calcular la duración real del break en minutos
            int actualBreakMinutes = calculateBreakMinutes(
                    shiftDetail.getBreakStartTime(),
                    shiftDetail.getBreakEndTime()
            );

            System.out.println("=== DEBUG VALIDACIÓN BREAK INDIVIDUAL ===");
            System.out.println("Break configurado total por shift: " + configuredBreakMinutes + " minutos");
            System.out.println("Break actual este período: " + actualBreakMinutes + " minutos");

            // 🔹 NUEVA LÓGICA: Validar que el break individual sea positivo
            if (actualBreakMinutes <= 0) {
                throw new IllegalArgumentException("La duración del break debe ser mayor a 0 minutos.");
            }

            // 🔹 NUEVA VALIDACIÓN: Total acumulado de breaks del shift
            validateTotalShiftBreaks(shiftDetail, actualBreakMinutes, configuredBreakMinutes);

        } catch (ResourceNotFoundException e) {
            throw new IllegalArgumentException("No se ha configurado el tiempo de break. " +
                    "Debe configurar los minutos de break antes de asignar horarios de break a los turnos.");
        }
    }

    private void validateTotalShiftBreaks(ShiftDetail currentShiftDetail, int currentBreakMinutes, int maxTotalBreakMinutes) {
        if (currentShiftDetail.getShift() == null || currentShiftDetail.getShift().getId() == null) {
            // Si no hay shift, solo validar que no exceda el máximo individual
            if (currentBreakMinutes > maxTotalBreakMinutes) {
                throw new IllegalArgumentException(
                        String.format("El break de %d minutos excede el máximo permitido de %d minutos.",
                                currentBreakMinutes, maxTotalBreakMinutes)
                );
            }
            return;
        }

        try {
            // Obtener todos los ShiftDetail existentes del mismo shift
            List<ShiftDetail> existingShiftDetails = shiftDetailRepository.findByShiftId(
                    currentShiftDetail.getShift().getId()
            );

            // 🔍 DEBUG DETALLES DE BREAKS EXISTENTES
            System.out.println("=== DEBUG DETALLES DE BREAKS EXISTENTES ===");
            System.out.println("Shift ID: " + currentShiftDetail.getShift().getId());
            System.out.println("Día actual que se está validando: " + currentShiftDetail.getDayOfWeek());
            System.out.println("Cantidad de ShiftDetails encontrados: " + existingShiftDetails.size());

            // 🔹 NUEVA LÓGICA: Validar solo breaks del MISMO DÍA
            int totalExistingBreakMinutesForDay = 0;
            Integer currentDayOfWeek = currentShiftDetail.getDayOfWeek();

            for (ShiftDetail detail : existingShiftDetails) {
                System.out.println("ShiftDetail ID: " + detail.getId());
                System.out.println("  - dayOfWeek: " + detail.getDayOfWeek());
                System.out.println("  - startTime: " + detail.getStartTime());
                System.out.println("  - endTime: " + detail.getEndTime());
                System.out.println("  - breakStart: " + detail.getBreakStartTime());
                System.out.println("  - breakEnd: " + detail.getBreakEndTime());

                // 🔹 SOLO PROCESAR SI ES EL MISMO DÍA
                if (!detail.getDayOfWeek().equals(currentDayOfWeek)) {
                    System.out.println("  - Día diferente, no se incluye en el cálculo");
                    System.out.println("  ---");
                    continue;
                }

                // Excluir el registro actual si es una actualización
                if (currentShiftDetail.getId() != null && currentShiftDetail.getId().equals(detail.getId())) {
                    System.out.println("  - Es el registro actual (actualización), se excluye");
                    System.out.println("  ---");
                    continue;
                }

                // Sumar breaks existentes DEL MISMO DÍA
                if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                    int existingBreakMinutes = calculateBreakMinutes(
                            detail.getBreakStartTime(),
                            detail.getBreakEndTime()
                    );
                    totalExistingBreakMinutesForDay += existingBreakMinutes;
                    System.out.println("  - breakMinutes: " + existingBreakMinutes + " (INCLUIDO en cálculo)");
                    System.out.println("  - Sumando break de ShiftDetail ID " + detail.getId() + ": " + existingBreakMinutes + " minutos");
                } else {
                    System.out.println("  - breakMinutes: 0 (sin break)");
                }
                System.out.println("  ---");
            }

            // Calcular el total con el nuevo break PARA ESTE DÍA
            int totalWithNewBreakForDay = totalExistingBreakMinutesForDay + currentBreakMinutes;

            System.out.println("=== DEBUG VALIDACIÓN BREAK POR DÍA ===");
            System.out.println("Día de la semana: " + currentDayOfWeek + " (" + getDayName(currentDayOfWeek) + ")");
            System.out.println("Breaks existentes en este día: " + totalExistingBreakMinutesForDay + " minutos");
            System.out.println("Break del registro actual: " + currentBreakMinutes + " minutos");
            System.out.println("Total con nuevo break para este día: " + totalWithNewBreakForDay + " minutos");
            System.out.println("Máximo permitido por día: " + maxTotalBreakMinutes + " minutos");

            // 🔹 VALIDACIÓN PRINCIPAL: No exceder el total permitido POR DÍA
            if (totalWithNewBreakForDay > maxTotalBreakMinutes) {
                throw new IllegalArgumentException(
                        String.format("Break rechazado para %s. Total de breaks existentes en este día: %d min. " +
                                        "Break actual: %d min. Total resultante: %d min. " +
                                        "Máximo permitido por día: %d min.",
                                getDayName(currentDayOfWeek), totalExistingBreakMinutesForDay, currentBreakMinutes,
                                totalWithNewBreakForDay, maxTotalBreakMinutes)
                );
            }

            // 🔹 INFORMACIÓN PARA EL USUARIO (opcional, en logs)
            int remainingMinutesForDay = maxTotalBreakMinutes - totalWithNewBreakForDay;
            if (remainingMinutesForDay > 0) {
                System.out.println("Break aceptado para " + getDayName(currentDayOfWeek) +
                        ". Minutos restantes para este día: " + remainingMinutesForDay);
            } else {
                System.out.println("Break aceptado para " + getDayName(currentDayOfWeek) +
                        ". Se alcanzó el límite diario de breaks.");
            }

        } catch (Exception e) {
            System.err.println("Error validando total de breaks por día: " + e.getMessage());
            throw new IllegalArgumentException("Error validando la configuración de breaks por día: " + e.getMessage());
        }
    }

    // 🔹 MÉTODO AUXILIAR: Obtener nombre del día
    private String getDayName(Integer dayOfWeek) {
        switch (dayOfWeek) {
            case 1: return "Lunes";
            case 2: return "Martes";
            case 3: return "Miércoles";
            case 4: return "Jueves";
            case 5: return "Viernes";
            case 6: return "Sábado";
            case 7: return "Domingo";
            default: return "Día " + dayOfWeek;
        }
    }
    // 🔹 VALIDAR QUE EL BREAK ESTÉ DENTRO DEL HORARIO DE TRABAJO
    private void validateBreakWithinWorkingHours(ShiftDetail shiftDetail) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

            Date workStart = sdf.parse(shiftDetail.getStartTime());
            Date workEnd = sdf.parse(shiftDetail.getEndTime());
            Date breakStart = sdf.parse(shiftDetail.getBreakStartTime());
            Date breakEnd = sdf.parse(shiftDetail.getBreakEndTime());

            // El break debe comenzar después del inicio del turno
            if (breakStart.before(workStart) || breakStart.equals(workStart)) {
                throw new IllegalArgumentException("El break no puede comenzar al mismo tiempo o antes que el inicio del turno.");
            }

            // El break debe terminar antes del fin del turno
            if (breakEnd.after(workEnd) || breakEnd.equals(workEnd)) {
                throw new IllegalArgumentException("El break no puede terminar al mismo tiempo o después que el fin del turno.");
            }

        } catch (ParseException e) {
            throw new IllegalArgumentException("Error al validar las horas del break: " + e.getMessage());
        }
    }

    // 🔹 CALCULAR DURACIÓN DEL BREAK EN MINUTOS
    private int calculateBreakMinutes(String breakStartTime, String breakEndTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(breakStartTime);
            Date end = sdf.parse(breakEndTime);
            long differenceInMilliSeconds = end.getTime() - start.getTime();
            return (int) (differenceInMilliSeconds / (1000 * 60)); // Convertir a minutos
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido en break: " + e.getMessage());
        }
    }

    // ===== MÉTODOS EXISTENTES (sin cambios) =====

    private boolean isWeeklyHoursExceeded(ShiftDetail shiftDetail) {
        try {
            int currentShiftHours = calculateShiftHours(shiftDetail.getStartTime(), shiftDetail.getEndTime());
            int exactWeeklyHours = getExactWeeklyHoursFromConfig();
            int totalScheduledHoursThisWeek = getTotalScheduledHoursForShift(shiftDetail);
            int totalWithNewShift = totalScheduledHoursThisWeek + currentShiftHours;

            System.out.println("=== DEBUG VALIDACIÓN HORAS EXACTAS ===");
            System.out.println("Horas ya programadas en el shift: " + totalScheduledHoursThisWeek);
            System.out.println("Horas del nuevo turno: " + currentShiftHours);
            System.out.println("Total con este turno: " + totalWithNewShift);
            System.out.println("Horas exactas requeridas: " + exactWeeklyHours);

            if (totalWithNewShift > exactWeeklyHours) {
                System.out.println("ERROR: Excede las horas semanales exactas requeridas");
                return true;
            }

            if (isTimeDifferenceTooLong(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
                throw new IllegalArgumentException("La diferencia entre las horas de inicio y fin no puede ser mayor a 9 horas.");
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error en validación de horas exactas: " + e.getMessage());
            throw new IllegalStateException("Error en validación de horas semanales: " + e.getMessage());
        }
    }

    private int getExactWeeklyHoursFromConfig() {
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

    private int getTotalScheduledHoursForShift(ShiftDetail shiftDetail) {
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) {
            return 0;
        }

        try {
            List<ShiftDetail> allShiftDetails = shiftDetailRepository.findByShiftId(
                    shiftDetail.getShift().getId()
            );

            int totalHours = 0;

            for (ShiftDetail detail : allShiftDetails) {
                if (shiftDetail.getId() != null && shiftDetail.getId().equals(detail.getId())) {
                    continue;
                }

                if (detail.getStartTime() != null && detail.getEndTime() != null) {
                    totalHours += calculateShiftHours(detail.getStartTime(), detail.getEndTime());
                }
            }

            return totalHours;

        } catch (Exception e) {
            System.err.println("Error calculando horas programadas: " + e.getMessage());
            return 0;
        }
    }

    private String getWeeklyHoursErrorMessage(ShiftDetail shiftDetail) {
        int currentShiftHours = calculateShiftHours(shiftDetail.getStartTime(), shiftDetail.getEndTime());
        int exactWeeklyHours = getExactWeeklyHoursFromConfig();
        int totalScheduled = getTotalScheduledHoursForShift(shiftDetail);
        int totalWithNew = totalScheduled + currentShiftHours;

        if (totalWithNew > exactWeeklyHours) {
            return String.format("El total de horas semanales (%d) excedería las %d horas exactas requeridas. " +
                            "Actualmente programadas: %d horas. Este turno: %d horas. " +
                            "Debe ser exactamente %d horas, no más.",
                    totalWithNew, exactWeeklyHours, totalScheduled, currentShiftHours, exactWeeklyHours);
        } else {
            return String.format("La configuración resultaría en horas semanales insuficientes. " +
                            "Se requieren exactamente %d horas semanales. " +
                            "Total actual: %d horas.",
                    exactWeeklyHours, totalWithNew);
        }
    }

    private boolean isDailyShiftTooShort(String startTime, String endTime) {
        int shiftHours = calculateShiftHours(startTime, endTime);
        int minDailyHours = 2;
        return shiftHours < minDailyHours;
    }

    private int calculateShiftHours(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            long differenceInMilliSeconds = end.getTime() - start.getTime();
            return (int) (differenceInMilliSeconds / (1000 * 60 * 60));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    private boolean isStartTimeAfterEndTime(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            return start.after(end);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    private boolean isTimeDifferenceTooLong(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            long differenceInMilliSeconds = end.getTime() - start.getTime();
            long differenceInHours = differenceInMilliSeconds / (1000 * 60 * 60);
            return differenceInHours > 9;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    public ShiftDetail updateShiftDetail(Long id, ShiftDetail shiftDetail) {
        validateShiftDetail(shiftDetail);

        // 🔹 NUEVA VALIDACIÓN: Break también en updates
        validateBreakTimes(shiftDetail);

        ShiftDetail existing = shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));

        existing.setShift(shiftDetail.getShift());
        existing.setDayOfWeek(shiftDetail.getDayOfWeek());
        existing.setStartTime(shiftDetail.getStartTime());
        existing.setEndTime(shiftDetail.getEndTime());

        // 🔹 ACTUALIZAR CAMPOS DE BREAK
        existing.setBreakStartTime(shiftDetail.getBreakStartTime());
        existing.setBreakEndTime(shiftDetail.getBreakEndTime());

        existing.setUpdatedAt(new Date());

        return shiftDetailRepository.save(existing);
    }

    public void deleteShiftDetail(Long id) {
        if (!shiftDetailRepository.existsById(id)) {
            throw new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id);
        }
        shiftDetailRepository.deleteById(id);
    }

    private void validateShiftDetail(ShiftDetail shiftDetail) {
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) {
            throw new IllegalArgumentException("El turno (Shift) es obligatorio.");
        }
        if (shiftDetail.getDayOfWeek() == null || shiftDetail.getDayOfWeek() < 1 || shiftDetail.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("El día de la semana debe ser entre 1 (Lunes) y 7 (Domingo).");
        }
        if (!isValidMilitaryTime(shiftDetail.getStartTime())) {
            throw new IllegalArgumentException("La hora de inicio debe estar en formato HH:mm (hora militar).");
        }
        if (!isValidMilitaryTime(shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin debe estar en formato HH:mm (hora militar).");
        }
        if (isEndTimeBeforeStartTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin no puede ser menor o igual a la hora de inicio.");
        }
    }

    private boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }

        String timeRegex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])$";

        if (Pattern.matches(timeRegex, time)) {
            return true;
        }

        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        if (hours >= 24) {
            return true;
        }

        return false;
    }

    private boolean isTimeValid(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);

            if (start.after(end)) {
                throw new IllegalArgumentException("La hora de inicio no puede ser posterior a la hora de fin.");
            }

        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido. El formato debe ser HH:mm (hora militar, de 00:00 a 23:59).");
        }
        return true;
    }

    private boolean isEndTimeBeforeStartTime(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            return end.before(start) || end.equals(start);
        } catch (ParseException e) {
            return true;
        }
    }
}
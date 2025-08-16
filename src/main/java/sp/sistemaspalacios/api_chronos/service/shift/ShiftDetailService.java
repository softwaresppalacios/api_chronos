package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ShiftDetailService {

    private final ShiftDetailRepository shiftDetailRepository;
    private final GeneralConfigurationService generalConfigurationService;

    public ShiftDetailService(ShiftDetailRepository shiftDetailRepository,
                              GeneralConfigurationService generalConfigurationService) {
        this.shiftDetailRepository = shiftDetailRepository;
        this.generalConfigurationService = generalConfigurationService;
    }

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
        validateShiftDetail(shiftDetail);

        // ✅ Preservar formato original de configuración
        int configuredBreakMinutes = parseBreakMinutes(generalConfigurationService.getByType("BREAK").getValue());
        String weeklyHoursOriginal = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();
        String hoursPerDayOriginal = generalConfigurationService.getByType("DAILY_HOURS").getValue();
        String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

        // Validar que los valores sean parseables (pero mantener formato original)
        parseHoursFromValue(weeklyHoursOriginal); // Solo para validar
        parseHoursFromValue(hoursPerDayOriginal); // Solo para validar

        shiftDetail.setBreakMinutes(configuredBreakMinutes);
        shiftDetail.setWeeklyHours(weeklyHoursOriginal); // ✅ Guardar formato original
        shiftDetail.setNightHoursStart(nightStart);
        shiftDetail.setHoursPerDay(hoursPerDayOriginal); // ✅ Guardar formato original

        List<ShiftDetail> allBlocks = shiftDetailRepository
                .findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());

        int blocks = allBlocks.size() + 1;
        int breakMinutesPerBlock = (blocks > 0) ? (configuredBreakMinutes / blocks) : configuredBreakMinutes;

        if (shiftDetail.getBreakStartTime() != null && (shiftDetail.getBreakEndTime() == null || shiftDetail.getBreakEndTime().isEmpty())) {
            shiftDetail.setBreakEndTime(sumarMinutosAHora(shiftDetail.getBreakStartTime(), breakMinutesPerBlock));
        }

        validateBreakTimes(shiftDetail, configuredBreakMinutes);

        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    public ShiftDetail updateShiftDetail(Long id, ShiftDetail shiftDetail) {
        validateShiftDetail(shiftDetail);

        // ✅ Preservar formato original de configuración
        int configuredBreakMinutes = parseBreakMinutes(generalConfigurationService.getByType("BREAK").getValue());
        String weeklyHoursOriginal = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();
        String hoursPerDayOriginal = generalConfigurationService.getByType("DAILY_HOURS").getValue();
        String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

        // Validar que los valores sean parseables (pero mantener formato original)
        parseHoursFromValue(weeklyHoursOriginal); // Solo para validar
        parseHoursFromValue(hoursPerDayOriginal); // Solo para validar

        shiftDetail.setBreakMinutes(configuredBreakMinutes);
        shiftDetail.setWeeklyHours(weeklyHoursOriginal); // ✅ Guardar formato original
        shiftDetail.setNightHoursStart(nightStart);
        shiftDetail.setHoursPerDay(hoursPerDayOriginal); // ✅ Guardar formato original

        List<ShiftDetail> allBlocks = shiftDetailRepository
                .findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());

        int blocks = (int) allBlocks.stream().filter(s -> !s.getId().equals(id)).count() + 1;
        int breakMinutesPerBlock = (blocks > 0) ? (configuredBreakMinutes / blocks) : configuredBreakMinutes;

        if (shiftDetail.getBreakStartTime() != null && (shiftDetail.getBreakEndTime() == null || shiftDetail.getBreakEndTime().isEmpty())) {
            shiftDetail.setBreakEndTime(sumarMinutosAHora(shiftDetail.getBreakStartTime(), breakMinutesPerBlock));
        }

        validateBreakTimes(shiftDetail, configuredBreakMinutes);

        ShiftDetail existing = shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));

        existing.setShift(shiftDetail.getShift());
        existing.setDayOfWeek(shiftDetail.getDayOfWeek());
        existing.setStartTime(shiftDetail.getStartTime());
        existing.setEndTime(shiftDetail.getEndTime());
        existing.setBreakStartTime(shiftDetail.getBreakStartTime());
        existing.setBreakEndTime(shiftDetail.getBreakEndTime());
        existing.setBreakMinutes(shiftDetail.getBreakMinutes());
        existing.setWeeklyHours(shiftDetail.getWeeklyHours());
        existing.setNightHoursStart(shiftDetail.getNightHoursStart());
        existing.setHoursPerDay(shiftDetail.getHoursPerDay());
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

        // Validación mejorada para horarios nocturnos
        LocalTime startTime = LocalTime.parse(shiftDetail.getStartTime());
        LocalTime endTime = LocalTime.parse(shiftDetail.getEndTime());
        boolean crossesMidnight = endTime.isBefore(startTime);

        if (!crossesMidnight && (endTime.equals(startTime) || endTime.isBefore(startTime))) {
            throw new IllegalArgumentException("La hora de fin no puede ser menor o igual a la hora de inicio.");
        }
    }

    private boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        return Pattern.matches("^([01]?[0-9]|2[0-3]):([0-5][0-9])$", time);
    }

    // Método deprecado - mantener por compatibilidad pero no usar internamente
    @Deprecated
    private boolean isEndTimeBeforeStartTime(String startTime, String endTime) {
        try {
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            // Si cruza medianoche, no es un error
            if (end.isBefore(start)) return false;
            return end.equals(start);
        } catch (Exception e) {
            return true;
        }
    }

    public Map<String, Object> getWeeklyHoursSummary(Long shiftId) {
        List<ShiftDetail> details = shiftDetailRepository.findByShiftId(shiftId);

        int totalMinutes = 0;
        for (ShiftDetail d : details) {
            try {
                LocalTime start = LocalTime.parse(d.getStartTime());
                LocalTime end = LocalTime.parse(d.getEndTime());

                // Calcular duración considerando cruces de medianoche
                long duration;
                if (end.isBefore(start)) {
                    // Cruza medianoche
                    duration = ChronoUnit.MINUTES.between(start, LocalTime.MAX) +
                            ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
                } else {
                    duration = ChronoUnit.MINUTES.between(start, end);
                }
                totalMinutes += duration;

                // Restar breaks
                if (d.getBreakStartTime() != null && d.getBreakEndTime() != null) {
                    LocalTime breakStart = LocalTime.parse(d.getBreakStartTime());
                    LocalTime breakEnd = LocalTime.parse(d.getBreakEndTime());
                    long breakDuration = ChronoUnit.MINUTES.between(breakStart, breakEnd);
                    totalMinutes -= breakDuration;
                }

            } catch (Exception e) {
                throw new RuntimeException("Error al calcular duración: " + e.getMessage());
            }
        }

        int totalHours = totalMinutes / 60;
        int remainingMinutes = totalMinutes % 60;

        // ✅ Para el cálculo sí necesitamos convertir a decimal, pero solo internamente
        double weeklyLimit = parseHoursFromValue(generalConfigurationService.getByType("WEEKLY_HOURS").getValue());
        int weeklyLimitMinutes = (int)(weeklyLimit * 60);

        Map<String, Object> result = new HashMap<>();
        result.put("totalWorkedMinutes", totalMinutes);
        result.put("totalWorkedFormatted", String.format("%02d:%02d", totalHours, remainingMinutes));
        result.put("weeklyLimitMinutes", weeklyLimitMinutes);
        result.put("remainingMinutes", weeklyLimitMinutes - totalMinutes);

        return result;
    }

    private void validateBreakTimes(ShiftDetail shiftDetail, int configuredBreakMinutes) {
        if (shiftDetail.getBreakStartTime() == null && shiftDetail.getBreakEndTime() == null) return;
        if (shiftDetail.getBreakStartTime() == null || shiftDetail.getBreakEndTime() == null)
            throw new IllegalArgumentException("Ambas horas de break son requeridas.");
        if (!isValidMilitaryTime(shiftDetail.getBreakStartTime()))
            throw new IllegalArgumentException("Hora inicio break inválida.");
        if (!isValidMilitaryTime(shiftDetail.getBreakEndTime()))
            throw new IllegalArgumentException("Hora fin break inválida.");

        // Validación mejorada para breaks
        LocalTime breakStart = LocalTime.parse(shiftDetail.getBreakStartTime());
        LocalTime breakEnd = LocalTime.parse(shiftDetail.getBreakEndTime());

        if (breakEnd.isBefore(breakStart) || breakEnd.equals(breakStart)) {
            throw new IllegalArgumentException("El break no puede terminar antes o al mismo tiempo que comienza.");
        }

        validateBreakWithinWorkingHours(shiftDetail);
        validateBreakDuration(shiftDetail, configuredBreakMinutes);
    }

    private void validateBreakDuration(ShiftDetail shiftDetail, int configuredBreakMinutes) {
        List<ShiftDetail> allBlocks = shiftDetailRepository.findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());
        int totalBreak = 0;
        for (ShiftDetail detail : allBlocks) {
            if (shiftDetail.getId() != null && shiftDetail.getId().equals(detail.getId())) continue;
            if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                totalBreak += calculateBreakMinutes(detail.getBreakStartTime(), detail.getBreakEndTime());
            }
        }
        if (shiftDetail.getBreakStartTime() != null && shiftDetail.getBreakEndTime() != null) {
            totalBreak += calculateBreakMinutes(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime());
        }
        if (totalBreak > configuredBreakMinutes) {
            throw new IllegalArgumentException("Break total excede el límite permitido de " + configuredBreakMinutes + " minutos.");
        }
    }

    private void validateBreakWithinWorkingHours(ShiftDetail shiftDetail) {
        try {
            LocalTime workStart = LocalTime.parse(shiftDetail.getStartTime());
            LocalTime workEnd = LocalTime.parse(shiftDetail.getEndTime());
            LocalTime breakStart = LocalTime.parse(shiftDetail.getBreakStartTime());
            LocalTime breakEnd = LocalTime.parse(shiftDetail.getBreakEndTime());

            boolean shiftCrossesMidnight = workEnd.isBefore(workStart);

            if (shiftCrossesMidnight) {
                // Para turnos que cruzan medianoche, validar que el break esté en el rango correcto
                boolean breakIsValid = false;

                // El break puede estar en la parte PM del turno (después del inicio)
                if (breakStart.isAfter(workStart) && breakEnd.isAfter(breakStart)) {
                    breakIsValid = true;
                }
                // O puede estar en la parte AM del turno (antes del fin)
                else if (breakEnd.isBefore(workEnd) && breakStart.isBefore(breakEnd)) {
                    breakIsValid = true;
                }

                if (!breakIsValid) {
                    throw new IllegalArgumentException("El break debe estar dentro del rango laboral.");
                }
            } else {
                // Turno normal: validación estándar
                if (!breakStart.isAfter(workStart) || !breakEnd.isBefore(workEnd)) {
                    throw new IllegalArgumentException("El break debe estar dentro del rango laboral.");
                }
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Formato de hora inválido en validación de break.");
        }
    }

    private int calculateBreakMinutes(String breakStartTime, String breakEndTime) {
        try {
            LocalTime start = LocalTime.parse(breakStartTime);
            LocalTime end = LocalTime.parse(breakEndTime);
            return (int) ChronoUnit.MINUTES.between(start, end);
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    private String sumarMinutosAHora(String hora, int minutosASumar) {
        try {
            LocalTime time = LocalTime.parse(hora);
            LocalTime newTime = time.plusMinutes(minutosASumar);
            return newTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    // Método deprecado - mantener por compatibilidad
    @Deprecated
    private boolean isStartTimeAfterEndTime(String startTime, String endTime) {
        try {
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            // Los breaks no deben cruzar medianoche
            return start.isAfter(end);
        } catch (Exception e) {
            return true;
        }
    }

    // ✅ MÉTODO MEJORADO: Parsea horas para validación y cálculos internos
    private double parseHoursFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        value = value.trim();

        try {
            // Si contiene ":", es formato HH:MM
            if (value.contains(":")) {
                String[] parts = value.split(":");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Formato de hora inválido: " + value);
                }

                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);

                // Validar rangos
                if (hours < 0 || minutes < 0 || minutes >= 60) {
                    throw new IllegalArgumentException("Valores de hora inválidos: " + value);
                }

                return hours + (minutes / 60.0);
            }
            // Si no, es un número decimal directo como "8.5"
            else {
                double hours = Double.parseDouble(value);
                if (hours < 0) {
                    throw new IllegalArgumentException("Las horas no pueden ser negativas: " + value);
                }
                return hours;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + value + ". Debe ser formato HH:MM o decimal (ej: 8.5)");
        }
    }

    // ✅ MÉTODO PARA PARSEAR MINUTOS DE BREAK
    private int parseBreakMinutes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {
            double minutes = Double.parseDouble(value.trim());
            if (minutes < 0) {
                throw new IllegalArgumentException("Los minutos de break no pueden ser negativos: " + value);
            }
            return (int) Math.round(minutes);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor de break inválido: " + value + ". Debe ser un número.");
        }
    }
}
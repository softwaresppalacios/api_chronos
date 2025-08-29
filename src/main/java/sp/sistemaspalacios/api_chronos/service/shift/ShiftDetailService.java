package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.validator.shift.ShiftDetailValidator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // SOLO validar usando el validator centralizado
        ShiftDetailValidator.validateShiftDetail(
                shiftDetail,
                generalConfigurationService,
                shiftDetailRepository
        );

        // Configurar valores desde la configuración
        setConfigurationValues(shiftDetail);

        // Configurar break automático si es necesario
        configureBreakTimes(shiftDetail);

        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    public ShiftDetail updateShiftDetail(Long id, ShiftDetail shiftDetail) {
        // SOLO validar usando el validator centralizado
        ShiftDetailValidator.validateShiftDetail(
                shiftDetail,
                generalConfigurationService,
                shiftDetailRepository
        );

        // Configurar valores desde la configuración
        setConfigurationValues(shiftDetail);

        // Configurar break automático si es necesario
        configureBreakTimes(shiftDetail);

        ShiftDetail existing = shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));

        // Actualizar campos
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

    // MÉTODO SIMPLIFICADO: Solo configurar valores desde la configuración
    private void setConfigurationValues(ShiftDetail shiftDetail) {
        try {
            String breakValue = generalConfigurationService.getByType("BREAK").getValue();
            String weeklyHours = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();
            String hoursPerDay = generalConfigurationService.getByType("DAILY_HOURS").getValue();
            String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

            // NUNCA confiar en breakMinutes que viene del frontend - siempre usar configuración
            shiftDetail.setBreakMinutes(parseBreakMinutes(breakValue));

            // Almacenar configuración actual (formato original)
            shiftDetail.setWeeklyHours(weeklyHours);
            shiftDetail.setNightHoursStart(nightStart);
            shiftDetail.setHoursPerDay(hoursPerDay);

            // RECALCULAR duración real del break si existe
            recalculateBreakDuration(shiftDetail);

        } catch (Exception e) {
            throw new RuntimeException("Error al cargar configuración: " + e.getMessage(), e);
        }
    }


    private void recalculateBreakDuration(ShiftDetail shiftDetail) {
        if (shiftDetail.getBreakStartTime() != null &&
                !shiftDetail.getBreakStartTime().trim().isEmpty() &&
                shiftDetail.getBreakEndTime() != null &&
                !shiftDetail.getBreakEndTime().trim().isEmpty()) {

            try {
                LocalTime breakStart = LocalTime.parse(shiftDetail.getBreakStartTime());
                LocalTime breakEnd = LocalTime.parse(shiftDetail.getBreakEndTime());

                // RECALCULAR duración real (no confiar en frontend)
                int realBreakMinutes = (int) ChronoUnit.MINUTES.between(breakStart, breakEnd);

                // Actualizar con valor real calculado
                shiftDetail.setBreakMinutes(realBreakMinutes);

            } catch (Exception e) {
                throw new IllegalArgumentException("Error calculando duración del break: " + e.getMessage());
            }
        }
    }
    // MÉTODO SIMPLIFICADO: Solo configurar break automático
    private void configureBreakTimes(ShiftDetail shiftDetail) {
        if (shiftDetail.getBreakStartTime() != null &&
                (shiftDetail.getBreakEndTime() == null || shiftDetail.getBreakEndTime().isEmpty())) {

            int breakMinutesPerBlock = calculateBreakMinutesPerBlock(shiftDetail);
            shiftDetail.setBreakEndTime(addMinutesToTime(shiftDetail.getBreakStartTime(), breakMinutesPerBlock));
        }
    }

    private int calculateBreakMinutesPerBlock(ShiftDetail shiftDetail) {
        int configuredBreakMinutes = shiftDetail.getBreakMinutes() != null ? shiftDetail.getBreakMinutes() : 0;

        List<ShiftDetail> allBlocks = shiftDetailRepository
                .findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());

        int blocks = allBlocks.size() + 1;
        return (blocks > 0) ? (configuredBreakMinutes / blocks) : configuredBreakMinutes;
    }

    // CÁLCULO SIMPLE DE HORAS SEMANALES
    public Map<String, Object> getWeeklyHoursSummary(Long shiftId) {
        List<ShiftDetail> details = shiftDetailRepository.findByShiftId(shiftId);
        int totalMinutes = 0;

        for (ShiftDetail d : details) {
            totalMinutes += calculateWorkingMinutes(d);
        }

        int totalHours = totalMinutes / 60;
        int remainingMinutes = totalMinutes % 60;

        // Obtener límite semanal desde configuración
        double weeklyLimit = parseConfigurationHours(
                generalConfigurationService.getByType("WEEKLY_HOURS").getValue()
        );
        int weeklyLimitMinutes = (int)(weeklyLimit * 60);

        Map<String, Object> result = new HashMap<>();
        result.put("totalWorkedMinutes", totalMinutes);
        result.put("totalWorkedFormatted", String.format("%02d:%02d", totalHours, remainingMinutes));
        result.put("weeklyLimitMinutes", weeklyLimitMinutes);
        result.put("remainingMinutes", weeklyLimitMinutes - totalMinutes);

        return result;
    }

    // MÉTODOS AUXILIARES SIMPLIFICADOS
    private int calculateWorkingMinutes(ShiftDetail detail) {
        try {
            LocalTime start = LocalTime.parse(detail.getStartTime());
            LocalTime end = LocalTime.parse(detail.getEndTime());

            long duration;
            if (end.isBefore(start)) {
                // Cruza medianoche
                duration = ChronoUnit.MINUTES.between(start, LocalTime.MAX) +
                        ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
            } else {
                duration = ChronoUnit.MINUTES.between(start, end);
            }

            // Restar breaks
            if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                LocalTime breakStart = LocalTime.parse(detail.getBreakStartTime());
                LocalTime breakEnd = LocalTime.parse(detail.getBreakEndTime());
                long breakDuration = ChronoUnit.MINUTES.between(breakStart, breakEnd);
                duration -= breakDuration;
            }

            return (int) Math.max(0, duration);
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular duración: " + e.getMessage());
        }
    }

    private String addMinutesToTime(String timeStr, int minutesToAdd) {
        try {
            LocalTime time = LocalTime.parse(timeStr);
            LocalTime newTime = time.plusMinutes(minutesToAdd);
            return newTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    // MÉTODO ÚNICO PARA PARSEAR CONFIGURACIÓN
    private double parseConfigurationHours(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        value = value.trim();

        try {
            if (value.contains(":")) {
                // Formato HH:MM
                String[] parts = value.split(":");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Formato inválido: " + value);
                }

                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);

                if (hours < 0 || minutes < 0 || minutes >= 60) {
                    throw new IllegalArgumentException("Valores inválidos: " + value);
                }

                return hours + (minutes / 60.0);
            } else {
                // Formato decimal
                double hours = Double.parseDouble(value);
                if (hours < 0) {
                    throw new IllegalArgumentException("Las horas no pueden ser negativas: " + value);
                }
                return hours;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Formato inválido: " + value +
                    ". Use formato HH:MM o decimal", e);
        }
    }

    private int parseBreakMinutes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {
            double minutes = Double.parseDouble(value.trim());
            if (minutes < 0) {
                throw new IllegalArgumentException("Los minutos no pueden ser negativos: " + value);
            }
            return (int) Math.round(minutes);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor de break inválido: " + value, e);
        }
    }
}
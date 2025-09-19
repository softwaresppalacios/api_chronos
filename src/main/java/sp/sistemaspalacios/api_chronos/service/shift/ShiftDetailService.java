package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.validator.shift.ShiftDetailValidator;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShiftDetailService {

    private final ShiftDetailRepository shiftDetailRepository;
    private final GeneralConfigurationService generalConfigurationService;

    // Formateadores y helpers de tiempo
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DB = DateTimeFormatter.ofPattern("HH:mm");


    private static final DateTimeFormatter F12 =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("h:mm a")
                    .toFormatter();
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
        // Validación centralizada
        ShiftDetailValidator.validateShiftDetail(
                shiftDetail,
                generalConfigurationService,
                shiftDetailRepository
        );

        setConfigurationValues(shiftDetail);
        configureBreakTimes(shiftDetail);

        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    public ShiftDetail updateShiftDetail(Long id, ShiftDetail shiftDetail) {
        ShiftDetailValidator.validateShiftDetail(
                shiftDetail,
                generalConfigurationService,
                shiftDetailRepository
        );

        setConfigurationValues(shiftDetail);
        configureBreakTimes(shiftDetail);

        ShiftDetail existing = shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));

        existing.setShift(shiftDetail.getShift());
        existing.setDayOfWeek(shiftDetail.getDayOfWeek());
        // Asegúrate que TODO lo que guardes sea "HH:mm"
        existing.setStartTime(toHHmm(shiftDetail.getStartTime()));
        existing.setEndTime(toHHmm(shiftDetail.getEndTime()));
        existing.setBreakStartTime(toHHmm(shiftDetail.getBreakStartTime()));
        existing.setBreakEndTime(toHHmm(shiftDetail.getBreakEndTime()));
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

    // ==========================
    // Helpers de configuración
    // ==========================

    private void setConfigurationValues(ShiftDetail shiftDetail) {
        try {
            String breakValue = generalConfigurationService.getByType("BREAK").getValue();
            String weeklyHours = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();
            String hoursPerDay = generalConfigurationService.getByType("DAILY_HOURS").getValue();
            String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

            // Nunca confíes en breakMinutes de frontend
            shiftDetail.setBreakMinutes(parseBreakMinutes(breakValue));

            // Mantén estas cadenas tal cual en BD
            shiftDetail.setWeeklyHours(weeklyHours);
            shiftDetail.setNightHoursStart(nightStart);
            shiftDetail.setHoursPerDay(hoursPerDay);

            // Recalcula break si hay start/end
            recalculateBreakDuration(shiftDetail);

            // Normaliza horas a "HH:mm" antes de persistir
            shiftDetail.setStartTime(toHHmm(shiftDetail.getStartTime()));
            shiftDetail.setEndTime(toHHmm(shiftDetail.getEndTime()));
            shiftDetail.setBreakStartTime(toHHmm(shiftDetail.getBreakStartTime()));
            shiftDetail.setBreakEndTime(toHHmm(shiftDetail.getBreakEndTime()));

        } catch (Exception e) {
            throw new RuntimeException("Error al cargar configuración: " + e.getMessage(), e);
        }
    }
    // ==========================
    // Horas / Breaks
    // ==========================

    private void recalculateBreakDuration(ShiftDetail detail) {
        try {
            String bs = detail.getBreakStartTime();
            String be = detail.getBreakEndTime();

            if (bs == null || bs.isBlank() || be == null || be.isBlank()) {
                detail.setBreakMinutes(0);
                return;
            }

            LocalTime start = parseAny(bs);
            LocalTime end   = parseAny(be);

            int minutes = (int) Duration.between(start, end).toMinutes();
            if (minutes < 0) minutes = 0; // por seguridad; el break no debería cruzar medianoche
            detail.setBreakMinutes(minutes);
        } catch (Exception ex) {
            detail.setBreakMinutes(0);
        }
    }
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

    // ==========================
    // Resumen semanal
    // ==========================

    public Map<String, Object> getWeeklyHoursSummary(Long shiftId) {
        List<ShiftDetail> details = shiftDetailRepository.findByShiftId(shiftId);
        int totalMinutes = 0;

        for (ShiftDetail d : details) {
            totalMinutes += calculateWorkingMinutes(d);
        }

        int totalHours = totalMinutes / 60;
        int remainingMinutes = totalMinutes % 60;

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
    private int calculateWorkingMinutes(ShiftDetail detail) {
        try {
            LocalTime start = parseAny(detail.getStartTime());
            LocalTime end   = parseAny(detail.getEndTime());

            long duration;
            if (end.isBefore(start)) {
                duration = ChronoUnit.MINUTES.between(start, LocalTime.MAX)
                        + ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
            } else {
                duration = ChronoUnit.MINUTES.between(start, end);
            }

            if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                LocalTime breakStart = parseAny(detail.getBreakStartTime());
                LocalTime breakEnd   = parseAny(detail.getBreakEndTime());
                long breakDuration = ChronoUnit.MINUTES.between(breakStart, breakEnd);
                duration -= breakDuration;
            }

            return (int) Math.max(0, duration);
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular duración: " + e.getMessage());
        }
    }

    private String addMinutesToTime(String input, int minutes) {
        try {
            if (input == null || input.isBlank()) return input;
            LocalTime t = parseAny(input);
            LocalTime out = t.plusMinutes(minutes);
            return out.format(DB); // siempre "HH:mm"
        } catch (Exception e) {
            return input; // no rompas UI si viene mal
        }
    }


    private double parseConfigurationHours(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;

        value = value.trim();
        try {
            if (value.contains(":")) {
                String[] parts = value.split(":");
                if (parts.length != 2) throw new IllegalArgumentException("Formato inválido: " + value);
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                if (hours < 0 || minutes < 0 || minutes >= 60) {
                    throw new IllegalArgumentException("Valores inválidos: " + value);
                }
                return hours + (minutes / 60.0);
            } else {
                double hours = Double.parseDouble(value);
                if (hours < 0) throw new IllegalArgumentException("Horas negativas: " + value);
                return hours;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Formato inválido: " + value + " (use HH:MM o decimal)", e);
        }
    }
    // ==========================
    // Normalización + ParseAny
    // ==========================

    /** Normaliza a "HH:mm" si el valor existe. */
    private String toHHmmOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseAny(raw).format(HH_MM);
    }

    /** Aplica normalización a todos los campos de hora del detalle. */
    private void normalizeAllTimes(ShiftDetail d) {
        d.setStartTime(toHHmmOrNull(d.getStartTime()));
        d.setEndTime(toHHmmOrNull(d.getEndTime()));
        d.setBreakStartTime(toHHmmOrNull(d.getBreakStartTime()));
        d.setBreakEndTime(toHHmmOrNull(d.getBreakEndTime()));
    }



    private int parseBreakMinutes(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            double minutes = Double.parseDouble(value.trim());
            if (minutes < 0) throw new IllegalArgumentException("Minutos negativos: " + value);
            return (int) Math.round(minutes);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor de break inválido: " + value, e);
        }
    }

    private LocalTime parseAny(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Hora vacía");
        String s = raw.trim();
        // ¿viene con AM/PM?
        if (s.toUpperCase().endsWith("AM") || s.toUpperCase().endsWith("PM")) {
            return LocalTime.parse(normalizeAmPm(s), F12);
        }
        // Asumir "HH:mm"
        return LocalTime.parse(s);
    }

    private String normalizeAmPm(String s) {
        String x = s.trim().toUpperCase().replaceAll("\\s+", " ");
        // Asegurar un espacio antes de AM/PM
        x = x.replace("AM", " AM").replace("PM", " PM");
        x = x.replaceAll("\\s+(AM|PM)$", " $1");
        return x;
    }

    private String toHHmm(String maybe12h) {
        if (maybe12h == null || maybe12h.isBlank()) return maybe12h;
        return parseAny(maybe12h).format(DB);
    }

}

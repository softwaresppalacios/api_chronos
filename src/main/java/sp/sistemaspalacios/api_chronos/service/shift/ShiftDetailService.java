package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.NightHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
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

        int configuredBreakMinutes = Integer.parseInt(generalConfigurationService.getByType("BREAK").getValue());
        int weeklyHours = parseHoursFromValue(generalConfigurationService.getByType("WEEKLY_HOURS").getValue());
        int hoursPerDay = parseHoursFromValue(generalConfigurationService.getByType("DAILY_HOURS").getValue());
        String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

        shiftDetail.setBreakMinutes(configuredBreakMinutes);
        shiftDetail.setWeeklyHours(weeklyHours);
        shiftDetail.setNightHoursStart(nightStart);
        shiftDetail.setHoursPerDay(hoursPerDay);

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

        int configuredBreakMinutes = Integer.parseInt(generalConfigurationService.getByType("BREAK").getValue());
        int weeklyHours = parseHoursFromValue(generalConfigurationService.getByType("WEEKLY_HOURS").getValue());
        int hoursPerDay = parseHoursFromValue(generalConfigurationService.getByType("DAILY_HOURS").getValue());
        String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

        shiftDetail.setBreakMinutes(configuredBreakMinutes);
        shiftDetail.setWeeklyHours(weeklyHours);
        shiftDetail.setNightHoursStart(nightStart);
        shiftDetail.setHoursPerDay(hoursPerDay);

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
        if (isEndTimeBeforeStartTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin no puede ser menor o igual a la hora de inicio.");
        }
    }

    private boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        return Pattern.matches("^([01]?[0-9]|2[0-3]):([0-5][0-9])$", time);
    }

    private boolean isEndTimeBeforeStartTime(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            return sdf.parse(endTime).before(sdf.parse(startTime)) || sdf.parse(endTime).equals(sdf.parse(startTime));
        } catch (ParseException e) {
            return true;
        }
    }
    public Map<String, Object> getWeeklyHoursSummary(Long shiftId) {
        List<ShiftDetail> details = shiftDetailRepository.findByShiftId(shiftId);

        int totalMinutes = 0;
        for (ShiftDetail d : details) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                Date start = sdf.parse(d.getStartTime());
                Date end = sdf.parse(d.getEndTime());
                long duration = (end.getTime() - start.getTime()) / (1000 * 60);
                totalMinutes += duration;

                if (d.getBreakStartTime() != null && d.getBreakEndTime() != null) {
                    Date breakStart = sdf.parse(d.getBreakStartTime());
                    Date breakEnd = sdf.parse(d.getBreakEndTime());
                    long breakDuration = (breakEnd.getTime() - breakStart.getTime()) / (1000 * 60);
                    totalMinutes -= breakDuration;
                }

            } catch (ParseException e) {
                // Puedes manejar mejor este error si quieres
                throw new RuntimeException("Error al calcular duración: " + e.getMessage());
            }
        }

        int totalHours = totalMinutes / 60;
        int remainingMinutes = totalMinutes % 60;

        int weeklyLimit = parseHoursFromValue(generalConfigurationService.getByType("WEEKLY_HOURS").getValue());
        int weeklyLimitMinutes = weeklyLimit * 60;

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
        if (isStartTimeAfterEndTime(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime()))
            throw new IllegalArgumentException("El break no puede terminar antes de comenzar.");
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
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            Date workStart = sdf.parse(shiftDetail.getStartTime());
            Date workEnd = sdf.parse(shiftDetail.getEndTime());
            Date breakStart = sdf.parse(shiftDetail.getBreakStartTime());
            Date breakEnd = sdf.parse(shiftDetail.getBreakEndTime());
            if (!breakStart.after(workStart) || !breakEnd.before(workEnd)) {
                throw new IllegalArgumentException("El break debe estar dentro del rango laboral.");
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido en validación de break.");
        }
    }

    private int calculateBreakMinutes(String breakStartTime, String breakEndTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            long diff = sdf.parse(breakEndTime).getTime() - sdf.parse(breakStartTime).getTime();
            return (int) (diff / (1000 * 60));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    private String sumarMinutosAHora(String hora, int minutosASumar) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            Date date = sdf.parse(hora);
            long newTime = date.getTime() + (minutosASumar * 60 * 1000);
            return sdf.format(new Date(newTime));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    private boolean isStartTimeAfterEndTime(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            return sdf.parse(startTime).after(sdf.parse(endTime));
        } catch (ParseException e) {
            return true;
        }
    }

    private int parseHoursFromValue(String value) {
        String[] parts = value.split(":");
        int hours = Integer.parseInt(parts[0]);
        return hours;
    }
}

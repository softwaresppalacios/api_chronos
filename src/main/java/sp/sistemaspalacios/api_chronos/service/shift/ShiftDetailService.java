package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.NightHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.breakConfiguration.BreakConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.hoursPerDay.HoursPerDayService;
import sp.sistemaspalacios.api_chronos.service.boundaries.nightHours.NightHoursService;
import sp.sistemaspalacios.api_chronos.service.boundaries.weeklyHours.WeeklyHoursService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ShiftDetailService {

    private final ShiftDetailRepository shiftDetailRepository;
    private final WeeklyHoursService weeklyHoursService;
    private final NightHoursService nightHoursService;
    private final HoursPerDayService hoursPerDayService;
    private final BreakConfigurationService breakConfigurationService;

    public ShiftDetailService(
            ShiftDetailRepository shiftDetailRepository,
            WeeklyHoursService weeklyHoursService,
            NightHoursService nightHoursService,
            HoursPerDayService hoursPerDayService,
            BreakConfigurationService breakConfigurationService
    ) {
        this.shiftDetailRepository = shiftDetailRepository;
        this.weeklyHoursService = weeklyHoursService;
        this.nightHoursService = nightHoursService;
        this.hoursPerDayService = hoursPerDayService;
        this.breakConfigurationService = breakConfigurationService;
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

        int configuredBreakMinutes = breakConfigurationService.getCurrentBreakMinutes();
        int weeklyHours = weeklyHoursService.getCurrentWeeklyHours();
        NightHoursDTO nightHours = nightHoursService.getCurrentNightHours();
        int hoursPerDay = hoursPerDayService.getCurrentHoursPerDay().getHoursPerDay();

        shiftDetail.setBreakMinutes(configuredBreakMinutes);
        shiftDetail.setWeeklyHours(weeklyHours);
        if (nightHours != null) {
            shiftDetail.setNightHoursStart(String.valueOf(nightHours.getStartNight()));
        }
        shiftDetail.setHoursPerDay(hoursPerDay);

        List<ShiftDetail> allBlocks = shiftDetailRepository
                .findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());

        int blocks = allBlocks.size() + 1;
        int breakMinutesPerBlock = (blocks > 0) ? (configuredBreakMinutes / blocks) : configuredBreakMinutes;

        if (shiftDetail.getBreakStartTime() != null && (shiftDetail.getBreakEndTime() == null || shiftDetail.getBreakEndTime().isEmpty())) {
            shiftDetail.setBreakEndTime(
                    sumarMinutosAHora(shiftDetail.getBreakStartTime(), breakMinutesPerBlock)
            );
        }

        validateBreakTimes(shiftDetail, configuredBreakMinutes);

        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    public ShiftDetail updateShiftDetail(Long id, ShiftDetail shiftDetail) {
        validateShiftDetail(shiftDetail);

        int configuredBreakMinutes = breakConfigurationService.getCurrentBreakMinutes();
        int weeklyHours = weeklyHoursService.getCurrentWeeklyHours();
        NightHoursDTO nightHours = nightHoursService.getCurrentNightHours();
        int hoursPerDay = hoursPerDayService.getCurrentHoursPerDay().getHoursPerDay();

        shiftDetail.setBreakMinutes(configuredBreakMinutes);
        shiftDetail.setWeeklyHours(weeklyHours);
        if (nightHours != null) {
            shiftDetail.setNightHoursStart(String.valueOf(nightHours.getStartNight()));
        }
        shiftDetail.setHoursPerDay(hoursPerDay);

        List<ShiftDetail> allBlocks = shiftDetailRepository
                .findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());

        int blocks = (int) allBlocks.stream().filter(s -> !s.getId().equals(id)).count() + 1;
        int breakMinutesPerBlock = (blocks > 0) ? (configuredBreakMinutes / blocks) : configuredBreakMinutes;

        if (shiftDetail.getBreakStartTime() != null && (shiftDetail.getBreakEndTime() == null || shiftDetail.getBreakEndTime().isEmpty())) {
            shiftDetail.setBreakEndTime(
                    sumarMinutosAHora(shiftDetail.getBreakStartTime(), breakMinutesPerBlock)
            );
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
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        String timeRegex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])$";
        return Pattern.matches(timeRegex, time);
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

    private void validateBreakTimes(ShiftDetail shiftDetail, int configuredBreakMinutes) {
        if (shiftDetail.getBreakStartTime() == null && shiftDetail.getBreakEndTime() == null) {
            return;
        }
        if (shiftDetail.getBreakStartTime() == null || shiftDetail.getBreakEndTime() == null) {
            throw new IllegalArgumentException("Si se define un break, tanto la hora de inicio como la de fin son obligatorias.");
        }
        if (!isValidMilitaryTime(shiftDetail.getBreakStartTime())) {
            throw new IllegalArgumentException("La hora de inicio del break debe estar en formato HH:mm (hora militar).");
        }
        if (!isValidMilitaryTime(shiftDetail.getBreakEndTime())) {
            throw new IllegalArgumentException("La hora de fin del break debe estar en formato HH:mm (hora militar).");
        }
        if (isStartTimeAfterEndTime(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime())) {
            throw new IllegalArgumentException("La hora de inicio del break no puede ser posterior a la hora de fin del break.");
        }
        validateBreakWithinWorkingHours(shiftDetail);
        validateBreakDuration(shiftDetail, configuredBreakMinutes);
    }

    private void validateBreakDuration(ShiftDetail shiftDetail, int configuredBreakMinutes) {
        List<ShiftDetail> allBlocks = shiftDetailRepository
                .findByShiftIdAndDayOfWeek(shiftDetail.getShift().getId(), shiftDetail.getDayOfWeek());
        int totalBreak = 0;
        for (ShiftDetail detail : allBlocks) {
            if (shiftDetail.getId() != null && shiftDetail.getId().equals(detail.getId())) {
                continue;
            }
            if (detail.getBreakStartTime() != null && detail.getBreakEndTime() != null) {
                totalBreak += calculateBreakMinutes(detail.getBreakStartTime(), detail.getBreakEndTime());
            }
        }
        if (shiftDetail.getBreakStartTime() != null && shiftDetail.getBreakEndTime() != null) {
            totalBreak += calculateBreakMinutes(shiftDetail.getBreakStartTime(), shiftDetail.getBreakEndTime());
        }
        if (totalBreak > configuredBreakMinutes) {
            throw new IllegalArgumentException(
                    "La suma de todos los bloques de break para este día (" + totalBreak + " minutos) excede el máximo configurado (" + configuredBreakMinutes + " minutos)."
            );
        }
    }

    private void validateBreakWithinWorkingHours(ShiftDetail shiftDetail) {
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

    private int calculateBreakMinutes(String breakStartTime, String breakEndTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(breakStartTime);
            Date end = sdf.parse(breakEndTime);
            long differenceInMilliSeconds = end.getTime() - start.getTime();
            return (int) (differenceInMilliSeconds / (1000 * 60));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido en break: " + e.getMessage());
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

    public Map<String, Object> getWeeklyHoursSummary(Long shiftId) {
        int requiredHours = weeklyHoursService.getCurrentWeeklyHours();
        int scheduledHours = 0;

        List<ShiftDetail> details = shiftDetailRepository.findByShiftId(shiftId);
        for (ShiftDetail detail : details) {
            if (detail.getStartTime() != null && detail.getEndTime() != null) {
                scheduledHours += calculateShiftHours(detail.getStartTime(), detail.getEndTime());
            }
        }
        int missingHours = Math.max(0, requiredHours - scheduledHours);

        Map<String, Object> summary = new HashMap<>();
        summary.put("scheduledHours", scheduledHours);
        summary.put("requiredHours", requiredHours);
        summary.put("missingHours", missingHours);

        if (missingHours > 0) {
            System.out.println("⚠️  WARNING: Faltan " + missingHours + " horas para completar el turno semanal del shiftId " + shiftId);
        }
        return summary;
    }
}

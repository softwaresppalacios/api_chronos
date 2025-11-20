package sp.sistemaspalacios.api_chronos.service.employeeAttendance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.attendance.AttendanceValidationResult;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AlertType;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AttendanceAlertConfiguration;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.repository.employeeAttendance.EmployeeAttendanceRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.service.attendanceAlert.AttendanceAlertConfigurationService;
import sp.sistemaspalacios.api_chronos.service.notification.NotificationService;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceValidationService {

    private final EmployeeAttendanceRepository attendanceRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final AttendanceAlertConfigurationService alertConfigService;
    private final NotificationService notificationService;

    @Transactional
    public AttendanceValidationResult validateAttendance(EmployeeAttendance attendance) {
        try {
            log.info("🔍 Validando marcación - Employee: {}, Type: {}, Time: {}",
                    attendance.getEmployeeSchedule().getEmployeeId(),
                    attendance.getType(),
                    attendance.getTimestamp());

            AttendanceValidationResult result = new AttendanceValidationResult();
            result.setEmployeeId(attendance.getEmployeeSchedule().getEmployeeId());
            result.setAttendanceType(attendance.getType());
            result.setActualTime(attendance.getTimestamp());

            if (attendance.getType() != AttendanceType.CLOCK_IN) {
                result.setValid(true);
                result.setMessage("Marcación registrada correctamente");
                return result;
            }

            LocalDate attendanceDate = attendance.getTimestamp().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            EmployeeSchedule schedule = attendance.getEmployeeSchedule();
            EmployeeScheduleDay scheduleDay = getScheduleDayForDate(schedule, attendanceDate);

            if (scheduleDay == null || scheduleDay.getTimeBlocks().isEmpty()) {
                log.warn("⚠️ No hay horario configurado para esta fecha");
                result.setValid(true);
                result.setMessage("Sin horario configurado para validar");
                return result;
            }

            EmployeeScheduleTimeBlock firstBlock = scheduleDay.getTimeBlocks().stream()
                    .min(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                    .orElse(null);

            if (firstBlock == null) {
                result.setValid(true);
                result.setMessage("Sin horario de entrada configurado");
                return result;
            }

            LocalTime scheduledTime = firstBlock.getStartTime().toLocalTime();
            LocalTime actualTime = attendance.getTimestamp().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalTime();

            long secondsDifference = ChronoUnit.SECONDS.between(scheduledTime, actualTime);
            int minutesDifference = (int)(secondsDifference / 60);

            result.setScheduledTime(scheduledTime);
            result.setMinutesLate(minutesDifference);
            result.setSecondsLate((int)secondsDifference);

            AttendanceAlertConfiguration matchedAlert = determineAlert(minutesDifference);

            if (matchedAlert == null) {
                result.setValid(true);
                result.setStatus("NO_CONFIG");
                result.setMessage("Sin configuración de alerta aplicable");
                return result;
            }

            result.setAlertType(matchedAlert.getAlertType().name());
            result.setStatus(matchedAlert.getAction().name());

            String message = buildMessage(matchedAlert, minutesDifference, secondsDifference);
            result.setMessage(message);

            boolean shouldBlock = matchedAlert.getAction() == sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AlertAction.BLOCK;
            result.setValid(!shouldBlock);

            if (matchedAlert.getSendNotification()) {
                sendConfigurableNotification(
                        result.getEmployeeId(),
                        message,
                        matchedAlert,
                        scheduledTime,
                        actualTime
                );
            }

            log.info("✅ Validación completada - Employee: {}, Alert: {}, Action: {}",
                    result.getEmployeeId(),
                    matchedAlert.getAlertType(),
                    matchedAlert.getAction());

            return result;

        } catch (Exception e) {
            log.error("❌ Error validando asistencia: {}", e.getMessage(), e);

            AttendanceValidationResult errorResult = new AttendanceValidationResult();
            errorResult.setValid(true);
            errorResult.setMessage("Error en validación: " + e.getMessage());
            return errorResult;
        }
    }

    private AttendanceAlertConfiguration determineAlert(int minutesDifference) {
        List<AttendanceAlertConfiguration> configs = alertConfigService.getAllActiveConfigurations();

        if (minutesDifference == 0) {
            return configs.stream()
                    .filter(c -> c.getAlertType() == AlertType.ON_TIME)
                    .findFirst()
                    .orElse(null);
        }

        if (minutesDifference < 0) {
            int minutesEarly = Math.abs(minutesDifference);

            for (AttendanceAlertConfiguration config : configs) {
                if (config.getAlertType() == AlertType.EARLY_EXCESSIVE &&
                        minutesEarly >= config.getThresholdMinutes()) {
                    return config;
                }
                if (config.getAlertType() == AlertType.EARLY_MODERATE &&
                        minutesEarly >= config.getThresholdMinutes()) {
                    return config;
                }
            }
        } else {
            AttendanceAlertConfiguration bestMatch = null;

            for (AttendanceAlertConfiguration config : configs) {
                if (config.getAlertType() == AlertType.LATE_SEVERE &&
                        minutesDifference > 20) {
                    return config;
                }
                if (config.getAlertType() == AlertType.LATE_MODERATE &&
                        minutesDifference >= config.getThresholdMinutes() &&
                        minutesDifference <= 20) {
                    bestMatch = config;
                }
                if (config.getAlertType() == AlertType.LATE_MINOR &&
                        minutesDifference > 0 &&
                        minutesDifference < config.getThresholdMinutes() &&
                        bestMatch == null) {
                    bestMatch = config;
                }
            }

            return bestMatch;
        }

        return null;
    }

    private String buildMessage(
            AttendanceAlertConfiguration config,
            int minutesDifference,
            long secondsDifference
    ) {
        String message = config.getMessageTemplate();

        int absMinutes = Math.abs(minutesDifference);
        int absSeconds = (int)Math.abs(secondsDifference);
        double hours = absMinutes / 60.0;

        message = message.replace("{minutes}", String.valueOf(absMinutes));
        message = message.replace("{seconds}", String.valueOf(absSeconds));
        message = message.replace("{hours}", String.format("%.1f", hours));

        return message;
    }

    private void sendConfigurableNotification(
            Long employeeId,
            String message,
            AttendanceAlertConfiguration config,
            LocalTime scheduledTime,
            LocalTime actualTime
    ) {
        try {
            String detailedMessage = String.format(
                    "🔔 ALERTA: %s\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━\n" +
                            "👤 Empleado: %d\n" +
                            "⏰ Hora programada: %s\n" +
                            "🕐 Hora de marcación: %s\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━\n" +
                            "%s",
                    config.getAlertType().name(),
                    employeeId,
                    scheduledTime,
                    actualTime,
                    message
            );

            String recipients = config.getNotificationRecipients();
            if (recipients == null || recipients.trim().isEmpty()) {
                recipients = employeeId.toString();
            }

            notificationService.sendLatenessNotification(recipients, detailedMessage);

            log.info("📤 Notificación enviada: {}", config.getAlertType());

        } catch (Exception e) {
            log.error("❌ Error enviando notificación: {}", e.getMessage(), e);
        }
    }

    private EmployeeScheduleDay getScheduleDayForDate(
            EmployeeSchedule schedule,
            LocalDate date
    ) {
        if (schedule.getDays() == null || schedule.getDays().isEmpty()) {
            return null;
        }

        return schedule.getDays().stream()
                .filter(day -> {
                    LocalDate dayDate = day.getDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                    return dayDate.equals(date);
                })
                .findFirst()
                .orElse(null);
    }

    public Map<String, Object> getDailySummary(Long employeeId, LocalDate date) {
        Map<String, Object> summary = new HashMap<>();

        try {
            List<EmployeeSchedule> schedules = scheduleRepository
                    .findActiveSchedulesByEmployeeAndDate(employeeId, date);

            if (schedules.isEmpty()) {
                summary.put("hasSchedule", false);
                summary.put("message", "Sin horario asignado para esta fecha");
                return summary;
            }

            EmployeeSchedule schedule = schedules.get(0);
            EmployeeScheduleDay scheduleDay = getScheduleDayForDate(schedule, date);

            if (scheduleDay == null) {
                summary.put("hasSchedule", false);
                summary.put("message", "Sin horario para este día");
                return summary;
            }

            List<EmployeeAttendance> attendances = attendanceRepository
                    .findByEmployeeScheduleAndDate(schedule, java.sql.Date.valueOf(date));

            summary.put("hasSchedule", true);
            summary.put("employeeId", employeeId);
            summary.put("date", date);
            summary.put("scheduledBlocks", scheduleDay.getTimeBlocks().size());
            summary.put("totalAttendances", attendances.size());

            boolean hasClockIn = attendances.stream()
                    .anyMatch(a -> a.getType() == AttendanceType.CLOCK_IN);
            boolean hasClockOut = attendances.stream()
                    .anyMatch(a -> a.getType() == AttendanceType.CLOCK_OUT);

            summary.put("hasClockIn", hasClockIn);
            summary.put("hasClockOut", hasClockOut);

            if (!hasClockIn) {
                summary.put("status", "AUSENTE");
                summary.put("message", "Sin marcación de entrada");
            } else if (!hasClockOut) {
                summary.put("status", "EN_CURSO");
                summary.put("message", "Turno en curso");
            } else {
                summary.put("status", "COMPLETO");
                summary.put("message", "Turno completado");
            }

            return summary;

        } catch (Exception e) {
            log.error("Error obteniendo resumen diario: {}", e.getMessage(), e);
            summary.put("error", e.getMessage());
            return summary;
        }
    }
}
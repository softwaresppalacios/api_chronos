package sp.sistemaspalacios.api_chronos.dto.attendance;

import lombok.Data;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;

import java.time.LocalTime;
import java.util.Date;

@Data
public class AttendanceValidationResult {

    private Long employeeId;
    private AttendanceType attendanceType;
    private Date actualTime;
    private LocalTime scheduledTime;

    private boolean valid;
    private String status;
    private String alertType;
    private String message;

    private int minutesLate;
    private int secondsLate;
    private boolean notificationSent;
}
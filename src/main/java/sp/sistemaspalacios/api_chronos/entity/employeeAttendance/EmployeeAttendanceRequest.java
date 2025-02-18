package sp.sistemaspalacios.api_chronos.entity.employeeAttendance;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeAttendanceRequest {
    private Long employeeScheduleId;
    private AttendanceType attendanceType;
    private String clockInTime;
}

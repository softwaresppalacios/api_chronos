package sp.sistemaspalacios.api_chronos.entity.employeeAttendance;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import java.sql.Time;
import java.util.Date;

@Entity
@Table(name = "employee_attendance")
@Data
public class EmployeeAttendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_schedule_id", nullable = false)
    private EmployeeSchedule employeeSchedule;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Date timestamp = new Date();

    @Enumerated(EnumType.STRING)
    private AttendanceType type;

    private Boolean isLate;
    private String message;
}

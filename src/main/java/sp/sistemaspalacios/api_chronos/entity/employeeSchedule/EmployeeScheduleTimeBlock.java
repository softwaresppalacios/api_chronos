package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Time;
import java.util.Date;

@Entity
@Table(name = "employee_schedule_time_blocks")
@Data
public class EmployeeScheduleTimeBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_schedule_day_id", nullable = false)
    private EmployeeScheduleDay employeeScheduleDay;

    private Time startTime;
    private Time endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    public Object getNumberId() {
        return null;
    }

    public Object getShift() {
        return null;
    }

    public void setDay(EmployeeScheduleDay clonedDay) {
    }
}
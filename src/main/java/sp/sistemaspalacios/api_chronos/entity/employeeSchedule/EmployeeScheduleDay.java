package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "employee_schedule_days")
@Data
public class EmployeeScheduleDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Temporal(TemporalType.DATE)
    private Date date;

    private Integer dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = " employee_schedule_id ", nullable = false)
    private EmployeeSchedule employeeSchedule;

    @OneToMany(mappedBy = "employeeScheduleDay", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmployeeScheduleTimeBlock> timeBlocks;

    // Nuevo campo para days_parent_id
    @Column(name = "days_parent_id")
    private Long daysParentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    public ZoneId getDaysParent() {
        return null;
    }

    public Long getParentDayId() {
        return null;
    }
}
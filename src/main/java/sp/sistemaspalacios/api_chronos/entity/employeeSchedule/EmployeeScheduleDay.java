package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "employee_schedule_days")
@Data
public class EmployeeScheduleDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_schedule_id")
    private EmployeeSchedule employeeSchedule;

    @Temporal(TemporalType.DATE)
    private Date date;

    private Integer dayOfWeek;

    @OneToMany(mappedBy = "employeeScheduleDay", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmployeeScheduleTimeBlock> timeBlocks;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}
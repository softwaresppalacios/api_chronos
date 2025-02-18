package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;

import java.util.Date;

@Entity
@Table(name = "employee_schedules")
@Data
public class EmployeeSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    @ManyToOne
    @JoinColumn(name = "shift_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Shifts shift;

    @Temporal(TemporalType.DATE)
    private Date startDate;

    @Temporal(TemporalType.DATE)
    private Date endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}

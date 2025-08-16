package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "employee_schedules")
@Data
public class EmployeeSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shifts shift;

    @Temporal(TemporalType.DATE)
    private Date startDate;

    @Temporal(TemporalType.DATE)
    private Date endDate;

    @OneToMany(mappedBy = "employeeSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeScheduleDay> days = new ArrayList<>();

    // MODIFICACIÓN IMPORTANTE: Columna para almacenar el ID de days
    @Column(name = "days_parent_id")
    private Long daysParentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    //  FIX: Constructor que inicializa la lista
    public EmployeeSchedule() {
        this.days = new ArrayList<>();
    }

    // FIX: Getter que asegura lista mutable
    public List<EmployeeScheduleDay> getDays() {
        if (days == null) {
            days = new ArrayList<>();
        }
        return days;
    }

    public EmployeeScheduleDTO getEmployee() {
        return null;
    }
}
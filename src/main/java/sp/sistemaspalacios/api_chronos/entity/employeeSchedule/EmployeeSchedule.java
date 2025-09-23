package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;

@Entity
@Table(name = "employee_schedules")
@Data
public class EmployeeSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shifts shift;

    private LocalDate startDate;
    private LocalDate endDate;


    @OneToMany(mappedBy = "employeeSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeScheduleDay> days = new ArrayList<>();

    // Columna para almacenar el ID de days
    @Column(name = "days_parent_id")
    private Long daysParentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // Constructor que inicializa la lista
    public EmployeeSchedule() {
        this.days = new ArrayList<>();
    }

    // Getter que asegura lista mutable
    public List<EmployeeScheduleDay> getDays() {
        if (days == null) {
            days = new ArrayList<>();
        }
        return days;
    }

    // ====== AUTO-POBLAR FECHAS DE AUDITORÍA ======
    @PrePersist
    protected void onCreate() {
        ZoneId zone = ZoneId.systemDefault(); // ajusta si necesitas una zona específica
        // created_at = hoy a las 00:00
        Instant todayAt00 = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        this.createdAt = Date.from(todayAt00);
        // updated_at = ahora (o también 00:00 si prefieres)
        this.updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Date();
    }
}

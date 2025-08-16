package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "schedule_assignment_group")
@Data
public class ScheduleAssignmentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "period_start", nullable = false)
    @Temporal(TemporalType.DATE)
    private Date periodStart;

    @Column(name = "period_end", nullable = false)
    @Temporal(TemporalType.DATE)
    private Date periodEnd;

    //  FIX: Lista mutable con inicialización
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "schedule_group_details",
            joinColumns = @JoinColumn(name = "group_id")
    )
    @Column(name = "employee_schedule_id")
    private List<Long> employeeScheduleIds = new ArrayList<>();

    @Column(name = "total_hours", precision = 10, scale = 2)
    private BigDecimal totalHours = BigDecimal.ZERO;

    @Column(name = "regular_hours", precision = 10, scale = 2)
    private BigDecimal regularHours = BigDecimal.ZERO;

    @Column(name = "overtime_hours", precision = 10, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "overtime_type", length = 50)
    private String overtimeType;

    // NUEVAS COLUMNAS PARA FESTIVOS
    @Column(name = "festivo_hours", precision = 10, scale = 2)
    private BigDecimal festivoHours = BigDecimal.ZERO;

    @Column(name = "festivo_type", length = 50)
    private String festivoType;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    // FIX: Constructor que fuerza inicialización
    public ScheduleAssignmentGroup() {
        this.employeeScheduleIds = new ArrayList<>();
    }

    // FIX: Getter que asegura lista mutable
    public List<Long> getEmployeeScheduleIds() {
        if (employeeScheduleIds == null) {
            employeeScheduleIds = new ArrayList<>();
        }
        // Si no es mutable, crear nueva ArrayList
        if (!(employeeScheduleIds instanceof ArrayList)) {
            employeeScheduleIds = new ArrayList<>(employeeScheduleIds);
        }
        return employeeScheduleIds;
    }

    // FIX: Setter que asegura lista mutable
    public void setEmployeeScheduleIds(List<Long> employeeScheduleIds) {
        if (employeeScheduleIds == null) {
            this.employeeScheduleIds = new ArrayList<>();
        } else {
            // Crear siempre una nueva ArrayList para garantizar mutabilidad
            this.employeeScheduleIds = new ArrayList<>(employeeScheduleIds);
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
        // Asegurar que la lista esté inicializada
        if (employeeScheduleIds == null) {
            employeeScheduleIds = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}
package sp.sistemaspalacios.api_chronos.entity.shift;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;  // ← AGREGAR
import org.hibernate.annotations.UpdateTimestamp;    // ← AGREGAR

import java.util.Date;

@Entity
@Table(name = "shift_details")
@Data
public class ShiftDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)  // ← AGREGAR fetch = FetchType.LAZY
    @JoinColumn(name = "shift_id", nullable = false)
    @JsonBackReference
    private Shifts shift;

    @Column(name = "day_of_week")  // ← AGREGAR para consistencia con BD
    private Integer dayOfWeek;

    @Column(name = "start_time")   // ← AGREGAR para consistencia con BD
    private String startTime;

    @Column(name = "end_time")     // ← AGREGAR para consistencia con BD
    private String endTime;

    @Column(name = "break_start_time")
    private String breakStartTime;

    @Column(name = "break_end_time")
    private String breakEndTime;

    @Column(name = "break_minutes")
    private Integer breakMinutes;

    @Column(name = "weekly_hours")
    private String weeklyHours;

    @Column(name = "hours_per_day")
    private String hoursPerDay;

    @Column(name = "night_hours_start")
    private String nightHoursStart;

    @Column(name = "night_hours_end")
    private String nightHoursEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp  // ← AGREGAR para auto-generar fecha de creación
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp    // ← AGREGAR para auto-actualizar fecha de modificación
    private Date updatedAt;
}
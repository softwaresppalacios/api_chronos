package sp.sistemaspalacios.api_chronos.entity.shift;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = "shift_details")
@Data
public class ShiftDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "shift_id", nullable = false)
    @JsonBackReference
    private Shifts shift;

    private Integer dayOfWeek;
    private String startTime;
    private String endTime;

    @Column(name = "break_start_time")
    private String breakStartTime;

    @Column(name = "break_end_time")
    private String breakEndTime;

    @Column(name = "break_minutes")
    private Integer breakMinutes;

    @Column(name = "weekly_hours")
    private String weeklyHours;  // ✅ String para mantener formatos como "44:30" o "48.5"

    @Column(name = "hours_per_day")
    private String hoursPerDay;  // ✅ String para mantener "8.5" u otros

    @Column(name = "night_hours_start")
    private String nightHoursStart;

    @Column(name = "night_hours_end")
    private String nightHoursEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}

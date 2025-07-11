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

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}

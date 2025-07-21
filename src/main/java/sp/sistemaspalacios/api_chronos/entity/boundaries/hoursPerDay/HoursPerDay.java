package sp.sistemaspalacios.api_chronos.entity.boundaries.hoursPerDay;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hours_per_day_configuration")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoursPerDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hours_per_day", nullable = false)
    private Integer hoursPerDay;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}

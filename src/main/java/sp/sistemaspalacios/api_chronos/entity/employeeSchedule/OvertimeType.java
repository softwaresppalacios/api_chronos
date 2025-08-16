package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "overtime_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=50)
    private String code; // "EXTRA_DIURNA"

    @Column(name="display_name", nullable=false, length=120)
    private String displayName; // "Extra diurna"

    @Column(nullable=false, precision=5, scale=2)
    private BigDecimal percentage; // 0.25 = 25%

    @Column(nullable=false)
    private boolean active = true;
}
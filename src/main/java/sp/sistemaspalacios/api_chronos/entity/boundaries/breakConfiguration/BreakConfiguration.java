package sp.sistemaspalacios.api_chronos.entity.boundaries.breakConfiguration;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Data
@Table(name = "break_configuration")
public class BreakConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minutes", nullable = false)
    private Integer minutes; // Tiempo de descanso en minutos únicamente

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;

    // Constructor por defecto
    public BreakConfiguration() {}

    // Constructor con parámetros
    public BreakConfiguration(Integer minutes) {
        this.minutes = minutes;
    }
}
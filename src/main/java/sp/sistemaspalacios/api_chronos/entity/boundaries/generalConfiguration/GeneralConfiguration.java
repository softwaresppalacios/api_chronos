package sp.sistemaspalacios.api_chronos.entity.boundaries.generalConfiguration;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "general_configurations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String type;

    @Column(nullable = false)
    private String value;   // ← JPA lo convertirá automáticamente a INTERVAL

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
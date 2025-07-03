package sp.sistemaspalacios.api_chronos.entity.weeklyHours;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class WeeklyHours {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Asegúrate de que el campo 'id' esté presente

    @Column
    private String hours;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // Getter y Setter para 'id'
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // Otros getters y setters
    public String getHours() {
        return hours;
    }

    public void setHours(String hours) {
        this.hours = hours;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getMinHours() {
        return 0;
    }

    public long getMaxHours() {
        return 0;
    }
}

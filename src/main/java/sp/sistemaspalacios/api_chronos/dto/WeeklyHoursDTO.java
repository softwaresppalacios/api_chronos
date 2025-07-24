package sp.sistemaspalacios.api_chronos.dto;

import java.time.LocalDateTime;
public class WeeklyHoursDTO {
    private String hours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long id;  // Se guarda solo para lecturas, no se establece al crear un nuevo objeto.

    // Getters and setters
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

    public Long getId() {
        return id;
    }

    // No necesitas un setId aquí si no vas a manejar el ID manualmente.
}

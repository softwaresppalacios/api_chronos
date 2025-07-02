package sp.sistemaspalacios.api_chronos.dto;

import java.time.LocalDateTime;

public class WeeklyHoursDTO {

    private Long id;
    private String hours;  // Cambiado de Duration a String
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
}

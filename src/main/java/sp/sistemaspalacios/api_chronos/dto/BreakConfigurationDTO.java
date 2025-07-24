package sp.sistemaspalacios.api_chronos.dto;

import java.util.Date;

public class BreakConfigurationDTO {

    private Long id;
    private Integer minutes;
    private Date createdAt;  // java.util.Date
    private Date updatedAt;  // java.util.Date

    // Constructor por defecto
    public BreakConfigurationDTO() {}

    // Constructor completo
    public BreakConfigurationDTO(Long id, Integer minutes, Date createdAt, Date updatedAt) {
        this.id = id;
        this.minutes = minutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Constructor para crear (sin id, sin fechas)
    public BreakConfigurationDTO(Integer minutes) {
        this.minutes = minutes;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public void setMinutes(Integer minutes) {
        this.minutes = minutes;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "BreakConfigurationDTO{" +
                "id=" + id +
                ", minutes=" + minutes +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
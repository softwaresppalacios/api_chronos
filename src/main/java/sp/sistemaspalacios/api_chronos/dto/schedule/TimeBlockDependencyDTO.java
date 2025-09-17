package sp.sistemaspalacios.api_chronos.dto.schedule;

public class TimeBlockDependencyDTO {
    private Long id;
    private Long employeeScheduleDayId;
    private String startTime;
    private String endTime;
    private Integer dependencyId;
    private Long numberId; // ← AGREGAR ESTA LÍNEA

    // Constructores
    public TimeBlockDependencyDTO() {}

    public TimeBlockDependencyDTO(Long id, Long employeeScheduleDayId, String startTime, String endTime) {
        this.id = id;
        this.employeeScheduleDayId = employeeScheduleDayId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeScheduleDayId() {
        return employeeScheduleDayId;
    }

    public void setEmployeeScheduleDayId(Long employeeScheduleDayId) {
        this.employeeScheduleDayId = employeeScheduleDayId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Integer getDependencyId() {
        return dependencyId;
    }

    public void setDependencyId(Integer dependencyId) {
        this.dependencyId = dependencyId;
    }

    // ← AGREGAR ESTOS MÉTODOS GETTER Y SETTER PARA numberId
    public Long getNumberId() {
        return numberId;
    }

    public void setNumberId(Long numberId) {
        this.numberId = numberId;
    }

    @Override
    public String toString() {
        return "TimeBlockDependencyDTO{" +
                "id=" + id +
                ", employeeScheduleDayId=" + employeeScheduleDayId +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", dependencyId=" + dependencyId +
                ", numberId=" + numberId + // ← AGREGAR ESTA LÍNEA
                '}';
    }
}
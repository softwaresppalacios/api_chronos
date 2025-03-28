package sp.sistemaspalacios.api_chronos.dto;

public class TimeBlockDependencyDTO {
    private Long id;
    private Long employeeScheduleDayId;
    private String startTime;
    private String endTime;
    private Integer dependencyId;

    // Getters y Setters
    public Long getId() {
        return id;
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
}
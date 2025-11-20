package sp.sistemaspalacios.api_chronos.dto.schedule;

public class TimeBlockDTO {
    private Long id;
    private Long employeeScheduleDayId;
    private String startTime;
    private String endTime;
    private String numberId;
    private String breakStartTime;
    private String breakEndTime;

    // =================== GETTERS Y SETTERS ===================

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

    public String getNumberId() {
        return numberId;
    }

    public void setNumberId(String numberId) {
        this.numberId = numberId;
    }

    public String getBreakStartTime() {
        return breakStartTime;
    }

    public void setBreakStartTime(String breakStartTime) {
        this.breakStartTime = breakStartTime;
    }

    public String getBreakEndTime() {
        return breakEndTime;
    }

    public void setBreakEndTime(String breakEndTime) {
        this.breakEndTime = breakEndTime;
    }

    @Override
    public String toString() {
        return "TimeBlockDTO{" +
                "id=" + id +
                ", employeeScheduleDayId=" + employeeScheduleDayId +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", numberId='" + numberId + '\'' +
                ", breakStartTime='" + breakStartTime + '\'' +
                ", breakEndTime='" + breakEndTime + '\'' +
                '}';
    }
}
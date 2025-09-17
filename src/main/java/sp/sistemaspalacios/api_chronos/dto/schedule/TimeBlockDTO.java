package sp.sistemaspalacios.api_chronos.dto.schedule;

import javax.swing.text.Position;

public class TimeBlockDTO {
    private Long id;
    private Long employeeScheduleDayId;
    private Long daysParentId;
    private String startTime;
    private String endTime;
    private String numberId; // Campo para la cédula

    // Getters y Setters CORRECTOS
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

    // Getter y Setter para numberId (¡IMPORTANTE!)
    public String getNumberId() {
        return numberId;
    }

    public void setNumberId(String numberId) {
        this.numberId = numberId;
    }

    public Position getPosition() {
        return null;
    }

    public String getDaysParentId() {
        return null;
    }
}
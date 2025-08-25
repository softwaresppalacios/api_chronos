package sp.sistemaspalacios.api_chronos.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class HolidayExemptionDTO {

    private Long id;
    private Long employeeId;
    private LocalDate holidayDate;
    private String holidayName;
    private String exemptionReason;
    private Long scheduleAssignmentGroupId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor vacío
    public HolidayExemptionDTO() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }

    public String getHolidayName() { return holidayName; }
    public void setHolidayName(String holidayName) { this.holidayName = holidayName; }

    public String getExemptionReason() { return exemptionReason; }
    public void setExemptionReason(String exemptionReason) { this.exemptionReason = exemptionReason; }

    public Long getScheduleAssignmentGroupId() { return scheduleAssignmentGroupId; }
    public void setScheduleAssignmentGroupId(Long scheduleAssignmentGroupId) {
        this.scheduleAssignmentGroupId = scheduleAssignmentGroupId;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
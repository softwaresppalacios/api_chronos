package sp.sistemaspalacios.api_chronos.entity.employeeSchedule;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "holiday_exemptions")
public class HolidayExemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "holiday_date")
    private LocalDate holidayDate;

    @Column(name = "holiday_name")
    private String holidayName;

    @Column(name = "exemption_reason", columnDefinition = "TEXT")
    private String exemptionReason;

    @Column(name = "schedule_assignment_group_id")
    private Long scheduleAssignmentGroupId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructor vacío
    public HolidayExemption() {}

    // Se ejecuta antes de guardar (para created_at)
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // Se ejecuta antes de actualizar (para updated_at)
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters y Setters básicos
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
    public void setScheduleAssignmentGroupId(Long scheduleAssignmentGroupId) { this.scheduleAssignmentGroupId = scheduleAssignmentGroupId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
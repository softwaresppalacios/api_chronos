package sp.sistemaspalacios.api_chronos.dto.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** DTOs y Excepciones para Employee Schedule (estilo POJO con getters/setters) */
public final class ScheduleDto {

    // ========= REQUESTS =========

    public static class AssignmentRequest {
        @NotNull(message = "assignments es requerido")
        @NotEmpty(message = "Al menos una asignación es requerida")
        @Valid
        private List<ScheduleAssignment> assignments = new ArrayList<>();

        public void setAssignments(List<ScheduleAssignment> assignments) {
            this.assignments = (assignments != null) ? assignments : new ArrayList<>();
        }
        public AssignmentRequest() {}

        public List<ScheduleAssignment> getAssignments() { return assignments; }

        @Override
        public String toString() {
            return "AssignmentRequest{assignments=" + assignments + '}';
        }
    }

    public static class ScheduleAssignment {
        @NotNull(message = "Employee ID es requerido")
        private Long employeeId;

        @NotNull(message = "Shift ID es requerido")
        private Long shiftId;

        @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        public ScheduleAssignment() {}

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        @Override
        public String toString() {
            return "ScheduleAssignment{" +
                    "employeeId=" + employeeId +
                    ", shiftId=" + shiftId +
                    ", startDate=" + startDate +
                    ", endDate=" + endDate +
                    '}';
        }
    }

    public static class HolidayConfirmationRequest {
        @NotNull(message = "confirmedAssignments es requerido")
        @NotEmpty(message = "Debes confirmar al menos una asignación")
        @Valid
        private List<ConfirmedAssignment> confirmedAssignments = new ArrayList<>();

        public void setConfirmedAssignments(List<ConfirmedAssignment> confirmedAssignments) {
            this.confirmedAssignments = (confirmedAssignments != null) ? confirmedAssignments : new ArrayList<>();
        }
        public HolidayConfirmationRequest() {}
        public List<ConfirmedAssignment> getConfirmedAssignments() { return confirmedAssignments; }
    }

    public static class ConfirmedAssignment {
        @NotNull private Long employeeId;
        @NotNull private Long shiftId;

        @NotNull
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @Valid // ⬅️ importante para validar cada HolidayDecision
        private List<HolidayDecision> holidayDecisions = new ArrayList<>(); // ⬅️ evita null

        public ConfirmedAssignment() {}
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public List<HolidayDecision> getHolidayDecisions() { return holidayDecisions; }
        public void setHolidayDecisions(List<HolidayDecision> holidayDecisions) { this.holidayDecisions = holidayDecisions; }
    }

    public static class HolidayDecision { @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate holidayDate;

        private boolean applyHolidayCharge;
        private String exemptionReason;

        // Tu servicio usa reflexión sobre estos objetos. Dejamos Object.
        private List<Map<String, Object>> shiftSegments = new ArrayList<>(); // ⬅️ evita null

        public HolidayDecision() {}
        public LocalDate getHolidayDate() { return holidayDate; }
        public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
        public boolean isApplyHolidayCharge() { return applyHolidayCharge; }
        public void setApplyHolidayCharge(boolean applyHolidayCharge) { this.applyHolidayCharge = applyHolidayCharge; }
        public String getExemptionReason() { return exemptionReason; }
        public void setExemptionReason(String exemptionReason) { this.exemptionReason = exemptionReason; }
        public List<Map<String, Object>> getShiftSegments() { return shiftSegments; }
        public void setShiftSegments(List<Map<String, Object>> shiftSegments) { this.shiftSegments = shiftSegments; }
    }

    // ========= RESPONSES =========

    public static class AssignmentResult {
        private boolean success;
        private String message;
        private List<Object> updatedEmployees;  // Usar tu DTO
        private List<HolidayWarning> holidayWarnings;
        private boolean requiresConfirmation;

        public AssignmentResult() {}
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public List<Object> getUpdatedEmployees() {
            return updatedEmployees;
        }
        public void setUpdatedEmployees(List<Object> updatedEmployees) {
            this.updatedEmployees = updatedEmployees;
        }

        public List<HolidayWarning> getHolidayWarnings() { return holidayWarnings; }
        public void setHolidayWarnings(List<HolidayWarning> holidayWarnings) {
            this.holidayWarnings = holidayWarnings;
        }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean requiresConfirmation) {
            this.requiresConfirmation = requiresConfirmation;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // ⬅️ nuevo

    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<HolidayWarning> holidayWarnings;

        public ValidationResult() {}
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public List<HolidayWarning> getHolidayWarnings() { return holidayWarnings; }
        public void setHolidayWarnings(List<HolidayWarning> holidayWarnings) { this.holidayWarnings = holidayWarnings; }
    }

    public static class EmployeeHoursSummary {
        private Long employeeId;
        private Double totalHours;
        private Double assignedHours;
        private Double overtimeHours;
        private String overtimeType;
        private Double festivoHours;
        private String festivoType;
        private Map<String, Object> overtimeBreakdown;

        public EmployeeHoursSummary() {}
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Double getTotalHours() { return totalHours; }
        public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }
        public Double getAssignedHours() { return assignedHours; }
        public void setAssignedHours(Double assignedHours) { this.assignedHours = assignedHours; }
        public Double getOvertimeHours() { return overtimeHours; }
        public void setOvertimeHours(Double overtimeHours) { this.overtimeHours = overtimeHours; }
        public String getOvertimeType() { return overtimeType; }
        public void setOvertimeType(String overtimeType) { this.overtimeType = overtimeType; }
        public Double getFestivoHours() { return festivoHours; }
        public void setFestivoHours(Double festivoHours) { this.festivoHours = festivoHours; }
        public String getFestivoType() { return festivoType; }
        public void setFestivoType(String festivoType) { this.festivoType = festivoType; }
        public Map<String, Object> getOvertimeBreakdown() { return overtimeBreakdown; }
        public void setOvertimeBreakdown(Map<String, Object> overtimeBreakdown) { this.overtimeBreakdown = overtimeBreakdown; }
    }

    public static class HolidayWarning {
        private LocalDate holidayDate;
        private String holidayName;
        private Long employeeId;
        private String employeeName;
        private List<ShiftSegmentDetail> shiftSegments;
        private boolean requiresConfirmation;

        public HolidayWarning() {}
        public LocalDate getHolidayDate() { return holidayDate; }
        public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
        public String getHolidayName() { return holidayName; }
        public void setHolidayName(String holidayName) { this.holidayName = holidayName; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public List<ShiftSegmentDetail> getShiftSegments() { return shiftSegments; }
        public void setShiftSegments(List<ShiftSegmentDetail> shiftSegments) { this.shiftSegments = shiftSegments; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
    }

    public static class ShiftSegmentDetail {
        private String segmentName;
        private String startTime;
        private String endTime;
        private String breakStartTime;
        private String breakEndTime;
        private Integer breakMinutes;
        private Double workingHours;
        private Double breakHours;
        private Double effectiveHours;

        public ShiftSegmentDetail() {}
        public String getSegmentName() { return segmentName; }
        public void setSegmentName(String segmentName) { this.segmentName = segmentName; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public String getBreakStartTime() { return breakStartTime; }
        public void setBreakStartTime(String breakStartTime) { this.breakStartTime = breakStartTime; }
        public String getBreakEndTime() { return breakEndTime; }
        public void setBreakEndTime(String breakEndTime) { this.breakEndTime = breakEndTime; }
        public Integer getBreakMinutes() { return breakMinutes; }
        public void setBreakMinutes(Integer breakMinutes) { this.breakMinutes = breakMinutes; }
        public Double getWorkingHours() { return workingHours; }
        public void setWorkingHours(Double workingHours) { this.workingHours = workingHours; }
        public Double getBreakHours() { return breakHours; }
        public void setBreakHours(Double breakHours) { this.breakHours = breakHours; }
        public Double getEffectiveHours() { return effectiveHours; }
        public void setEffectiveHours(Double effectiveHours) { this.effectiveHours = effectiveHours; }
    }

    public static class ScheduleConflict {
        private Long employeeId;
        private LocalDate conflictDate;
        private Long existingScheduleId;
        private String message;

        public ScheduleConflict() {}
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public LocalDate getConflictDate() { return conflictDate; }
        public void setConflictDate(LocalDate conflictDate) { this.conflictDate = conflictDate; }
        public Long getExistingScheduleId() { return existingScheduleId; }
        public void setExistingScheduleId(Long existingScheduleId) { this.existingScheduleId = existingScheduleId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // ========= EXCEPCIONES =========

    public static class ConflictException extends RuntimeException {
        private final List<ScheduleConflict> conflicts;
        public ConflictException(String message, List<ScheduleConflict> conflicts) {
            super(message);
            this.conflicts = conflicts;
        }
        public List<ScheduleConflict> getConflicts() { return conflicts; }
    }

    public static class ValidationException extends RuntimeException {
        private final List<String> validationErrors;
        public ValidationException(String message, List<String> validationErrors) {
            super(message);
            this.validationErrors = validationErrors;
        }
        public List<String> getValidationErrors() { return validationErrors; }
    }
}
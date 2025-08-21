package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.EmployeeScheduleService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/employee-schedules")
public class EmployeeScheduleController {

    private final EmployeeScheduleService employeeScheduleService;

    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService) {
        this.employeeScheduleService = employeeScheduleService;
    }

    /**
     * =================== ENDPOINT PRINCIPAL ===================
     * Asignar múltiples turnos con validación automática de conflictos
     */
    @PostMapping("/assign-multiple")
    public ResponseEntity<?> assignMultipleSchedules(@RequestBody AssignmentRequest request) {
        try {
            // El servicio maneja TODA la lógica
            AssignmentResult result = employeeScheduleService.processMultipleAssignments(request);

            return ResponseEntity.ok(result);

        } catch (ConflictException e) {
            // Conflictos de solapamiento
            return ResponseEntity.status(409).body(Map.of(
                    "error", "SCHEDULE_CONFLICT",
                    "message", e.getMessage(),
                    "conflicts", e.getConflicts()
            ));

        } catch (ValidationException e) {
            // Errores de validación
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VALIDATION_ERROR",
                    "message", e.getMessage(),
                    "details", e.getValidationErrors()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", "Error interno del servidor: " + e.getMessage()
            ));
        }
    }

    /**
     * Obtener resumen de horas por empleado
     */
    @GetMapping("/employee/{employeeId}/hours-summary")
    public ResponseEntity<EmployeeHoursSummary> getEmployeeHoursSummary(@PathVariable Long employeeId) {
        try {
            EmployeeHoursSummary summary = employeeScheduleService.calculateEmployeeHoursSummary(employeeId);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Validar asignación SIN guardar (para preview)
     */
    @PostMapping("/validate-assignment")
    public ResponseEntity<?> validateAssignment(@RequestBody AssignmentRequest request) {
        try {
            ValidationResult result = employeeScheduleService.validateAssignmentOnly(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Confirmar asignación de festivos con motivo opcional
     */
    @PostMapping(
            value = "/confirm-holiday-assignment",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> confirmHolidayAssignment(@RequestBody HolidayConfirmationRequest request) {
        try {
            // validación mínima
            if (request == null || request.getConfirmedAssignments() == null || request.getConfirmedAssignments().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "confirmedAssignments es requerido y no debe ser vacío"
                ));
            }

            AssignmentResult result = employeeScheduleService.processHolidayAssignment(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace(); // para ver el stack en la consola
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // =================== ENDPOINTS EXISTENTES (mantener compatibilidad) ===================

    @GetMapping
    public ResponseEntity<List<EmployeeScheduleDTO>> getAllSchedules() {
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getAllEmployeeSchedules();
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeScheduleDTO> getScheduleById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().build();
        }
        EmployeeScheduleDTO schedule = employeeScheduleService.getEmployeeScheduleById(id);
        return ResponseEntity.ok(schedule);
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeId(@PathVariable Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByEmployeeId(employeeId);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/by-dependency-id")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByDependencyId(
            @RequestParam Long dependencyId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm:ss") LocalTime startTime,
            @RequestParam(required = false) Long shiftId) {

        try {
            List<EmployeeScheduleDTO> result = employeeScheduleService.getSchedulesByDependencyId(
                    dependencyId, startDate, endDate, startTime, shiftId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchedule(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body("ID inválido");
        }
        try {
            employeeScheduleService.deleteEmployeeSchedule(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // =================== DTOs ===================

    public static class AssignmentRequest {
        private List<ScheduleAssignment> assignments;

        public List<ScheduleAssignment> getAssignments() { return assignments; }
        public void setAssignments(List<ScheduleAssignment> assignments) { this.assignments = assignments; }
    }

    public static class ScheduleAssignment {
        private Long employeeId;
        private Long shiftId;
        private LocalDate startDate;
        private LocalDate endDate;

        // Getters y Setters
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    }

    public static class AssignmentResult {
        private boolean success;
        private String message;
        private List<EmployeeHoursSummary> updatedEmployees;
        private List<HolidayWarning> holidayWarnings;
        private boolean requiresConfirmation;

        // Getters y Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<EmployeeHoursSummary> getUpdatedEmployees() { return updatedEmployees; }
        public void setUpdatedEmployees(List<EmployeeHoursSummary> updatedEmployees) { this.updatedEmployees = updatedEmployees; }
        public List<HolidayWarning> getHolidayWarnings() { return holidayWarnings; }
        public void setHolidayWarnings(List<HolidayWarning> holidayWarnings) { this.holidayWarnings = holidayWarnings; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
    }

    public static class EmployeeHoursSummary {
        private Long employeeId;
        private Double totalHours;
        private Double assignedHours;        // horas regulares
        private Double overtimeHours;        // horas extras (NO festivas)
        private String overtimeType;         // tipo de recargo NO festivo
        private Double festivoHours;         // NUEVO: horas festivas
        private String festivoType;          // NUEVO: tipo de recargo festivo
        private Map<String, Object> overtimeBreakdown;

        // Constructores
        public EmployeeHoursSummary() {}

        // Getters y Setters
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

        // NUEVOS CAMPOS
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
        private boolean requiresConfirmation;

        // Getters y Setters
        public LocalDate getHolidayDate() { return holidayDate; }
        public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
        public String getHolidayName() { return holidayName; }
        public void setHolidayName(String holidayName) { this.holidayName = holidayName; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
    }

    public static class HolidayConfirmationRequest {
        private List<ConfirmedAssignment> confirmedAssignments;
        public List<ConfirmedAssignment> getConfirmedAssignments() { return confirmedAssignments; }
        public void setConfirmedAssignments(List<ConfirmedAssignment> confirmedAssignments) { this.confirmedAssignments = confirmedAssignments; }
    }

    public static class ConfirmedAssignment {
        private Long employeeId;
        private Long shiftId;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate endDate; // puede venir null

        private List<HolidayDecision> holidayDecisions;

        // getters/setters
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
    public static class HolidayDecision {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate holidayDate;

        private boolean applyHolidayCharge;
        private String exemptionReason;

        // getters/setters
        public LocalDate getHolidayDate() { return holidayDate; }
        public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }
        public boolean isApplyHolidayCharge() { return applyHolidayCharge; }
        public void setApplyHolidayCharge(boolean applyHolidayCharge) { this.applyHolidayCharge = applyHolidayCharge; }
        public String getExemptionReason() { return exemptionReason; }
        public void setExemptionReason(String exemptionReason) { this.exemptionReason = exemptionReason; }
    }

    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<HolidayWarning> holidayWarnings;

        // Getters y Setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        public List<HolidayWarning> getHolidayWarnings() { return holidayWarnings; }
        public void setHolidayWarnings(List<HolidayWarning> holidayWarnings) { this.holidayWarnings = holidayWarnings; }
    }

    // =================== EXCEPCIONES ===================

    public static class ConflictException extends RuntimeException {
        private List<ScheduleConflict> conflicts;

        public ConflictException(String message, List<ScheduleConflict> conflicts) {
            super(message);
            this.conflicts = conflicts;
        }

        public List<ScheduleConflict> getConflicts() { return conflicts; }
    }

    public static class ValidationException extends RuntimeException {
        private List<String> validationErrors;

        public ValidationException(String message, List<String> validationErrors) {
            super(message);
            this.validationErrors = validationErrors;
        }

        public List<String> getValidationErrors() { return validationErrors; }
    }

    public static class ScheduleConflict {
        private Long employeeId;
        private LocalDate conflictDate;
        private Long existingScheduleId;
        private String message;

        // Getters y Setters
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public LocalDate getConflictDate() { return conflictDate; }
        public void setConflictDate(LocalDate conflictDate) { this.conflictDate = conflictDate; }
        public Long getExistingScheduleId() { return existingScheduleId; }
        public void setExistingScheduleId(Long existingScheduleId) { this.existingScheduleId = existingScheduleId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }


}
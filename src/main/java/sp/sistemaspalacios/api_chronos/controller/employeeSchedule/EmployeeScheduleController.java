package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.EmployeeScheduleService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/employee-schedules")
public class EmployeeScheduleController {

    private final EmployeeScheduleService employeeScheduleService;

    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService) {
        this.employeeScheduleService = employeeScheduleService;
    }

    // =================== ENDPOINTS ===================

    @PostMapping("/assign-multiple")
    public ResponseEntity<AssignmentResult> assignMultipleSchedules(@Valid @RequestBody AssignmentRequest request) {
        AssignmentResult result = employeeScheduleService.processMultipleAssignments(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/employee/{employeeId}/hours-summary")
    public ResponseEntity<EmployeeHoursSummary> getEmployeeHoursSummary(@PathVariable Long employeeId) {
        EmployeeHoursSummary summary = employeeScheduleService.calculateEmployeeHoursSummary(employeeId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/validate-assignment")
    public ResponseEntity<ValidationResult> validateAssignment(@Valid @RequestBody AssignmentRequest request) {
        ValidationResult result = employeeScheduleService.validateAssignmentOnly(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/confirm-holiday-assignment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssignmentResult> confirmHolidayAssignment(@Valid @RequestBody HolidayConfirmationRequest request) {
        AssignmentResult result = employeeScheduleService.processHolidayAssignment(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<EmployeeScheduleDTO>> getAllSchedules() {
        return ResponseEntity.ok(employeeScheduleService.getAllEmployeeSchedules());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeScheduleDTO> getScheduleById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeScheduleService.getEmployeeScheduleById(id));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeId(@PathVariable Long employeeId) {
        return ResponseEntity.ok(employeeScheduleService.getSchedulesByEmployeeId(employeeId));
    }

    @GetMapping("/by-dependency-id")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByDependencyId(
            @RequestParam Long dependencyId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm:ss") LocalTime startTime,
            @RequestParam(required = false) Long shiftId) {

        return ResponseEntity.ok(employeeScheduleService.getSchedulesByDependencyId(
                dependencyId, startDate, endDate, startTime, shiftId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        employeeScheduleService.deleteEmployeeSchedule(id);
        return ResponseEntity.noContent().build();
    }


    }

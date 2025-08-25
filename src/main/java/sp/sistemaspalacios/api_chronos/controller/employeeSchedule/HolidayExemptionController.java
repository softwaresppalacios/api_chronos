package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.HolidayExemptionDTO;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.HolidayExemptionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holiday-exemptions")
public class HolidayExemptionController {

    private final HolidayExemptionService holidayExemptionService;

    // Constructor
    public HolidayExemptionController(HolidayExemptionService holidayExemptionService) {
        this.holidayExemptionService = holidayExemptionService;
    }

    // Crear una nueva excepción festiva
    @PostMapping
    public ResponseEntity<?> createExemption(@RequestBody ExemptionRequest request) {
        try {
            HolidayExemptionDTO exemption = holidayExemptionService.saveExemption(
                    request.getEmployeeId(),
                    request.getHolidayDate(),
                    request.getHolidayName(),
                    request.getExemptionReason(),
                    request.getScheduleAssignmentGroupId()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Excepción guardada correctamente");
            response.put("data", exemption);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Obtener excepciones de un empleado
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<HolidayExemptionDTO>> getExemptionsByEmployee(@PathVariable Long employeeId) {
        List<HolidayExemptionDTO> exemptions = holidayExemptionService.getExemptionsByEmployee(employeeId);
        return ResponseEntity.ok(exemptions);
    }

    // Clase para recibir los datos del frontend
    public static class ExemptionRequest {
        private Long employeeId;
        private String holidayDate;
        private String holidayName;
        private String exemptionReason;
        private Long scheduleAssignmentGroupId;

        // Constructor vacío
        public ExemptionRequest() {}

        // Getters y Setters básicos
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

        public java.time.LocalDate getHolidayDate() {
            return java.time.LocalDate.parse(holidayDate);
        }
        public void setHolidayDate(String holidayDate) { this.holidayDate = holidayDate; }

        public String getHolidayName() { return holidayName; }
        public void setHolidayName(String holidayName) { this.holidayName = holidayName; }

        public String getExemptionReason() { return exemptionReason; }
        public void setExemptionReason(String exemptionReason) { this.exemptionReason = exemptionReason; }

        public Long getScheduleAssignmentGroupId() { return scheduleAssignmentGroupId; }
        public void setScheduleAssignmentGroupId(Long scheduleAssignmentGroupId) {
            this.scheduleAssignmentGroupId = scheduleAssignmentGroupId;
        }
    }
}

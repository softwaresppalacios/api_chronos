package sp.sistemaspalacios.api_chronos.controller.attendance;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.service.employeeAttendance.AttendanceValidationService;
import sp.sistemaspalacios.api_chronos.service.employeeAttendance.EmployeeAttendanceService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/attendance-validation") // ← CAMBIO: Nueva ruta
@RequiredArgsConstructor
public class AttendanceController {

    private final EmployeeAttendanceService attendanceService;
    private final AttendanceValidationService validationService;

    /**
     * Registrar marcación con validación automática
     * POST /api/attendance-validation/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerAttendance(
            @RequestBody Map<String, Object> request
    ) {
        Map<String, Object> response = new HashMap<>();

        try {
            Long scheduleId = ((Number) request.get("scheduleId")).longValue();
            String typeStr = (String) request.get("type");
            AttendanceType type = AttendanceType.valueOf(typeStr);

            EmployeeAttendance attendance = attendanceService.registerAttendance(
                    scheduleId, type
            );

            response.put("success", true);
            response.put("message", attendance.getMessage());
            response.put("attendance", attendance);
            response.put("isLate", attendance.getIsLate());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("blocked", true);
            response.put("error", e.getMessage());
            return ResponseEntity.status(403).body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Obtener resumen de asistencia del día
     * GET /api/attendance-validation/summary/{employeeId}?date=2025-11-19
     */
    @GetMapping("/summary/{employeeId}")
    public ResponseEntity<Map<String, Object>> getDailySummary(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Map<String, Object> summary = validationService.getDailySummary(employeeId, date);
        return ResponseEntity.ok(summary);
    }
}
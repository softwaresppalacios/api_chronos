package sp.sistemaspalacios.api_chronos.controller.employeeAttendance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.service.employeeAttendance.EmployeeAttendanceService;

import java.util.Date;

@RestController
@RequestMapping("/api/attendance")
public class EmployeeAttendanceController {

    @Autowired
    private EmployeeAttendanceService service;

    // Clase interna para recibir el JSON en la petición
    public static class AttendanceRequest {
        public Long scheduleId;
        public AttendanceType type;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerAttendance(@RequestBody AttendanceRequest request) {
        try {
            // Llamar al servicio con el ID en lugar de un objeto EmployeeSchedule
            EmployeeAttendance attendance = service.registerAttendance(request.scheduleId, request.type);
            return ResponseEntity.ok(attendance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/register-manual")
    public ResponseEntity<?> registerManualAttendance(@RequestBody ManualAttendanceRequest request) {
        try {
            EmployeeAttendance attendance = service.registerManualAttendance(
                    request.scheduleId,
                    request.type,
                    request.timestamp,
                    request.message // Se usará el campo message para la justificación
            );
            return ResponseEntity.ok(attendance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Clase interna para recibir el JSON en la petición
    public static class ManualAttendanceRequest {
        public Long scheduleId;
        public AttendanceType type;
        public Date timestamp; // Fecha y hora de la marcación manual
        public String message; // Justificación de la marcación manual
    }


}


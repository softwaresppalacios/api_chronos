package sp.sistemaspalacios.api_chronos.controller.employeeAttendance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.service.employeeAttendance.EmployeeAttendanceService;


import java.util.List;


@RestController
@RequestMapping("/api/attendance")
public class EmployeeAttendanceController {
    @Autowired
    private EmployeeAttendanceService service;

    public static class AttendanceRequest {
        public Long scheduleId;
        public AttendanceType type;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerAttendance(@RequestBody AttendanceRequest request) {
        try {
            EmployeeSchedule schedule = new EmployeeSchedule();
            schedule.setId(request.scheduleId);
            EmployeeAttendance attendance = service.registerAttendance(schedule, request.type);
            return ResponseEntity.ok(attendance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/schedule/{id}")
    public ResponseEntity<List<EmployeeAttendance>> getAttendancesBySchedule(@PathVariable Long id) {
        EmployeeSchedule schedule = new EmployeeSchedule();
        schedule.setId(id);
        return ResponseEntity.ok(service.getAttendancesBySchedule(schedule));
    }
}

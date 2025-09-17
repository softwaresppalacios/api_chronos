package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeShiftDetail;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.shift.EmployeeShiftDetailService;

import java.sql.Time;
import java.util.List;
import java.util.Map;

@RestController

@RequestMapping("/api/employee-shift-details")
public class EmployeeShiftDetailController {

    private final EmployeeShiftDetailService employeeShiftDetailService;

    public EmployeeShiftDetailController(EmployeeShiftDetailService employeeShiftDetailService) {
        this.employeeShiftDetailService = employeeShiftDetailService;
    }

    // Obtener todos los detalles de turnos de empleados
    @GetMapping
    public ResponseEntity<List<EmployeeShiftDetail>> getAllShiftDetails() {
        return ResponseEntity.ok(employeeShiftDetailService.getAllShiftDetails());
    }

    // Obtener un detalle de turno específico por ID
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeShiftDetail> getShiftDetailById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeShiftDetailService.getShiftDetailById(id));
    }

    // Obtener detalles de turnos de un empleado por EmployeeScheduleId
    @GetMapping("/schedule/{employeeScheduleId}")
    public ResponseEntity<List<EmployeeShiftDetail>> getShiftDetailsByEmployeeScheduleId(@PathVariable Long employeeScheduleId) {
        return ResponseEntity.ok(employeeShiftDetailService.getShiftDetailsByEmployeeScheduleId(employeeScheduleId));
    }

    // Crear un nuevo detalle de turno para un solo empleado
    @PostMapping
    public ResponseEntity<EmployeeShiftDetail> createShiftDetail(@RequestBody EmployeeShiftDetail shiftDetail) {
        return ResponseEntity.ok(employeeShiftDetailService.createShiftDetail(shiftDetail));
    }

    // Crear turnos para varios empleados con validación de horario
    @PostMapping("/bulk")
    public ResponseEntity<List<EmployeeShiftDetail>> createShiftDetailsForMultipleEmployees(@RequestBody Map<String, Object> request) {
        List<Long> employeeScheduleIds = (List<Long>) request.get("employeeScheduleIds");
        EmployeeShiftDetail shiftDetail = new EmployeeShiftDetail();
        shiftDetail.setDayOfWeek((Integer) request.get("dayOfWeek"));
        shiftDetail.setIsExempt((Boolean) request.get("isExempt"));

        if (!Boolean.TRUE.equals(shiftDetail.getIsExempt())) {
            shiftDetail.setStartTime(Time.valueOf(request.get("startTime").toString()));
            shiftDetail.setEndTime(Time.valueOf(request.get("endTime").toString()));
        }

        Time referenceStartTime = Time.valueOf(request.get("referenceStartTime").toString());
        Time referenceEndTime = Time.valueOf(request.get("referenceEndTime").toString());

        return ResponseEntity.ok(employeeShiftDetailService.createShiftDetailsForMultipleEmployees(
                employeeScheduleIds, shiftDetail, referenceStartTime, referenceEndTime));
    }

    // Eliminar un detalle de turno por ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShiftDetail(@PathVariable Long id) {
        employeeShiftDetailService.deleteShiftDetail(id);
        return ResponseEntity.noContent().build();
    }
}
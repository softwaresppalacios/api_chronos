package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.EmployeeScheduleService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employee-schedules")
public class EmployeeScheduleController {
    @Autowired
    private EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final EmployeeScheduleService employeeScheduleService;

    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService) {
        this.employeeScheduleService = employeeScheduleService;
    }

    /** 🔹 Obtiene todos los horarios de empleados */
    @GetMapping
    public ResponseEntity<List<EmployeeScheduleDTO>> getAllSchedules() {
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getAllEmployeeSchedules();
        return ResponseEntity.ok(schedules);
    }

    /** 🔹 Obtiene un horario por su ID */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeScheduleDTO> getScheduleById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().build();
        }
        EmployeeScheduleDTO schedule = employeeScheduleService.getEmployeeScheduleById(id);
        return ResponseEntity.ok(schedule);
    }

    /** 🔹 Obtiene los horarios de un empleado por su ID */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeId(@PathVariable Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByEmployeeId(employeeId);
        return ResponseEntity.ok(schedules);
    }

    /** 🔹 Obtiene los horarios según el turno (Shift ID) */
    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByShiftId(@PathVariable Long shiftId) {
        if (shiftId == null || shiftId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByShiftId(shiftId);
        return ResponseEntity.ok(schedules);
    }


    @PostMapping
    public ResponseEntity<?> createSchedule(@RequestBody EmployeeSchedule schedule) {
        try {
            // Guardar el horario en la base de datos
            EmployeeSchedule created = employeeScheduleService.createEmployeeSchedule(schedule);

            // Construir la respuesta final
            Map<String, Object> response = new HashMap<>();
            response.put("id", created.getId());
            response.put("employeeId", created.getEmployeeId());
            response.put("shift", created.getShift());
            response.put("startDate", created.getStartDate());
            response.put("endDate", created.getEndDate());
            response.put("createdAt", created.getCreatedAt());
            response.put("updatedAt", created.getUpdatedAt());

            // Obtener los días y bloques de horarios
            List<Map<String, Object>> days = employeeScheduleDayRepository.findByEmployeeSchedule_Id(created.getId())
                    .stream()
                    .map(this::convertDayToMap)
                    .collect(Collectors.toList());

            response.put("days", days);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    private Map<String, Object> convertDayToMap(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new HashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", day.getDate());
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        // Convertir los bloques de horarios
        List<Map<String, String>> timeBlocks = day.getTimeBlocks()
                .stream()
                .map(this::convertTimeBlockToMap)
                .collect(Collectors.toList());

        dayMap.put("timeBlocks", timeBlocks);

        return dayMap;
    }








    private Map<String, String> convertTimeBlockToMap(EmployeeScheduleTimeBlock block) {
        Map<String, String> blockMap = new HashMap<>();
        blockMap.put("startTime", block.getStartTime().toString());
        blockMap.put("endTime", block.getEndTime().toString());
        return blockMap;
    }

    /** 🔹 Actualiza un horario de empleado */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSchedule(@PathVariable Long id, @RequestBody EmployeeSchedule schedule) {
        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body("ID inválido");
        }
        try {
            EmployeeSchedule updated = employeeScheduleService.updateEmployeeSchedule(id, schedule);
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** 🔹 Elimina un horario de empleado */
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

    /** 🔹 Manejo de excepciones para `ResourceNotFoundException` */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    /** 🔹 Manejo de excepciones para `IllegalArgumentException` */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @GetMapping("/by-employee-ids")
    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(@RequestParam List<Long> employeeIds) {
        return employeeScheduleService.getSchedulesByEmployeeIds(employeeIds);
    }

    /** 🔹 Obtiene los horarios dentro de un rango de fechas */
    @GetMapping("/by-date-range")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByDateRange(
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {

        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByDateRange(startDate, endDate);
        return ResponseEntity.ok(schedules);
    }

}

package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.dto.TimeBlockDependencyDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.EmployeeScheduleService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

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



    /** 🔹 Obtiene los horarios según el turno (Shift ID) */
    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByShiftId(@PathVariable Long shiftId) {
        if (shiftId == null || shiftId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByShiftId(shiftId);
        return ResponseEntity.ok(schedules);
    }

    /** 🔹 Crea un nuevo horario de empleado */
    @PostMapping
    public ResponseEntity<?> createSchedules(@RequestBody List<Map<String, Object>> scheduleRequests) {
        try {
            // Convertir las solicitudes a objetos EmployeeSchedule
            List<EmployeeSchedule> schedules = scheduleRequests.stream()
                    .map(this::parseIndividualRequest)
                    .collect(Collectors.toList());

            // Crear los horarios
            List<EmployeeSchedule> createdSchedules = employeeScheduleService.createMultipleSchedules(schedules);

            // Convertir a formato de respuesta
            List<Map<String, Object>> responses = createdSchedules.stream()
                    .map(this::createSingleScheduleResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private Map<String, Object> createSingleScheduleResponse(EmployeeSchedule created) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", created.getId());
        response.put("employeeId", created.getEmployeeId());
        response.put("shift", convertShiftToMap(created.getShift()));
        response.put("startDate", created.getStartDate());
        response.put("endDate", created.getEndDate());
        response.put("createdAt", created.getCreatedAt());
        response.put("updatedAt", created.getUpdatedAt());
        response.put("daysParentId", created.getDaysParentId());

        Map<String, Object> daysStructure = new LinkedHashMap<>();
        daysStructure.put("id", created.getDaysParentId());
        daysStructure.put("items", created.getDays().stream()
                .map(this::convertDayToMap)
                .collect(Collectors.toList()));

        response.put("days", daysStructure);
        return response;
    }
    private EmployeeSchedule parseIndividualRequest(Map<String, Object> scheduleRequest) {
        EmployeeSchedule schedule = new EmployeeSchedule();

        // Extract employeeId
        Object employeeIdObj = scheduleRequest.get("employeeId");
        if (employeeIdObj == null) {
            throw new IllegalArgumentException("Employee ID is required");
        }
        schedule.setEmployeeId(Long.parseLong(employeeIdObj.toString()));

        // Extract shift
        Object shiftObj = scheduleRequest.get("shift");
        if (shiftObj == null) {
            throw new IllegalArgumentException("Shift is required");
        }

        Map<String, Object> shiftMap = (Map<String, Object>) shiftObj;
        Shifts shift = new Shifts();
        shift.setId(Long.parseLong(shiftMap.get("id").toString()));
        schedule.setShift(shift);

        // Extract dates
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            schedule.setStartDate(dateFormat.parse(scheduleRequest.get("startDate").toString()));
            schedule.setEndDate(dateFormat.parse(scheduleRequest.get("endDate").toString()));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid date format. Use yyyy-MM-dd");
        }

        return schedule;
    }
    private Map<String, Object> createSingleSchedule(EmployeeSchedule schedule) {
        EmployeeSchedule created = employeeScheduleService.createEmployeeSchedule(schedule);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", created.getId());
        response.put("employeeId", created.getEmployeeId());

        // Convertir shift a mapa
        response.put("shift", convertShiftToMap(created.getShift()));

        response.put("startDate", created.getStartDate());
        response.put("endDate", created.getEndDate());
        response.put("createdAt", created.getCreatedAt());
        response.put("updatedAt", created.getUpdatedAt());

        // Estructura days con ID (usando el ID del horario padre)
        Map<String, Object> daysStructure = new LinkedHashMap<>();
        daysStructure.put("id", created.getId()); // Usamos el ID del horario como ID de days

        daysStructure.put("items", created.getDays().stream()
                .map(this::convertDayToMap)
                .collect(Collectors.toList()));

        response.put("days", daysStructure);
        return response;
    }

    private Map<String, Object> convertShiftToMap(Shifts shift) {
        if (shift == null) return null;

        Map<String, Object> shiftMap = new LinkedHashMap<>();
        shiftMap.put("id", shift.getId());
        shiftMap.put("name", shift.getName());
        shiftMap.put("description", shift.getDescription());
        shiftMap.put("timeBreak", shift.getTimeBreak());
        shiftMap.put("dependencyId", shift.getDependencyId());
        shiftMap.put("createdAt", shift.getCreatedAt());
        shiftMap.put("updatedAt", shift.getUpdatedAt());

        if (shift.getShiftDetails() != null) {
            shiftMap.put("shiftDetails", shift.getShiftDetails().stream()
                    .map(this::convertShiftDetailToMap)
                    .collect(Collectors.toList()));
        }

        return shiftMap;
    }

    private Map<String, Object> convertShiftDetailToMap(ShiftDetail detail) {
        Map<String, Object> detailMap = new LinkedHashMap<>();
        detailMap.put("id", detail.getId());
        detailMap.put("dayOfWeek", detail.getDayOfWeek());
        detailMap.put("startTime", detail.getStartTime());
        detailMap.put("endTime", detail.getEndTime());
        detailMap.put("createdAt", detail.getCreatedAt());
        detailMap.put("updatedAt", detail.getUpdatedAt());
        return detailMap;
    }

    private Map<String, Object> convertDayToMap(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new LinkedHashMap<>();
        dayMap.put("id", day.getId()); // Use the actual day ID, not the schedule ID
        dayMap.put("date", day.getDate());
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        // Convertir timeBlocks
        List<Map<String, String>> timeBlocks = day.getTimeBlocks().stream()
                .map(this::convertTimeBlockToMap)
                .collect(Collectors.toList());

        dayMap.put("timeBlocks", timeBlocks);
        return dayMap;
    }






    @PutMapping("/time-blocks/by-dependency")
    public ResponseEntity<?> updateTimeBlocksByDependency(@RequestBody List<TimeBlockDependencyDTO> timeBlockDTOList) {
        try {
            // Validaciones generales (si son necesarias)
            if (timeBlockDTOList == null || timeBlockDTOList.isEmpty()) {
                return ResponseEntity.badRequest().body("No se proporcionaron bloques de tiempo.");
            }

            // Lista para almacenar las respuestas de los bloques de tiempo actualizados
            List<Map<String, Object>> updatedBlocks = new ArrayList<>();

            // Iterar sobre la lista de bloques de tiempo y actualizarlos
            for (TimeBlockDependencyDTO timeBlockDTO : timeBlockDTOList) {
                // Validar cada bloque de tiempo individualmente (puedes reutilizar la lógica de validación existente)
                if (timeBlockDTO.getId() == null || timeBlockDTO.getId() <= 0) {
                    return ResponseEntity.badRequest().body("ID del bloque de tiempo inválido.");
                }
                if (timeBlockDTO.getEmployeeScheduleDayId() == null || timeBlockDTO.getEmployeeScheduleDayId() <= 0) {
                    return ResponseEntity.badRequest().body("ID del día asociado inválido.");
                }


                // Actualizar el bloque de tiempo
                EmployeeScheduleTimeBlock updatedBlock = employeeScheduleService.updateTimeBlockByDependency(timeBlockDTO);

                // Crear respuesta con la estructura solicitada para cada bloque actualizado
                Map<String, Object> blockResponse = new LinkedHashMap<>();
                blockResponse.put("id", updatedBlock.getId());
                blockResponse.put("employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId());
                blockResponse.put("startTime", updatedBlock.getStartTime().toString());
                blockResponse.put("endTime", updatedBlock.getEndTime().toString());

                updatedBlocks.add(blockResponse);
            }

            // Responder con todos los bloques actualizados
            return ResponseEntity.ok(updatedBlocks);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeIds(
            @RequestParam List<Long> employeeIds) {

        try {
            List<EmployeeScheduleDTO> result = employeeScheduleService.getSchedulesByEmployeeIds(employeeIds);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
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

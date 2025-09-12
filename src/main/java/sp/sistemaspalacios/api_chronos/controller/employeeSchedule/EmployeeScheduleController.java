package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.dto.TimeBlockDTO;
import sp.sistemaspalacios.api_chronos.dto.TimeBlockDependencyDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.EmployeeScheduleService;

import java.sql.Time;
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
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository;
    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService,  EmployeeScheduleDayRepository employeeScheduleDayRepository,
                                      EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository) {
        this.employeeScheduleService = employeeScheduleService;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.employeeScheduleTimeBlockRepository = employeeScheduleTimeBlockRepository;
    }
// AGREGAR estos endpoints a tu EmployeeScheduleController.java

    @PostMapping("/timeblocks")
    public ResponseEntity<?> createTimeBlock(@RequestBody Map<String, Object> timeBlockData) {
        try {
            // Validar y extraer datos
            Long employeeScheduleDayId = Long.parseLong(timeBlockData.get("employeeScheduleDayId").toString());
            String startTime = timeBlockData.get("startTime").toString();
            String endTime = timeBlockData.get("endTime").toString();

            // Buscar el día
            EmployeeScheduleDay day = employeeScheduleDayRepository
                    .findById(employeeScheduleDayId)
                    .orElseThrow(() -> new ResourceNotFoundException("Día no encontrado: " + employeeScheduleDayId));

            // Crear bloque
            EmployeeScheduleTimeBlock newBlock = new EmployeeScheduleTimeBlock();
            newBlock.setEmployeeScheduleDay(day);
            newBlock.setStartTime(java.sql.Time.valueOf(startTime));
            newBlock.setEndTime(java.sql.Time.valueOf(endTime));
            newBlock.setCreatedAt(new Date());

            EmployeeScheduleTimeBlock savedBlock = employeeScheduleTimeBlockRepository.save(newBlock);

            // Respuesta
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", savedBlock.getId());
            response.put("message", "Bloque creado correctamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/timeblocks/{timeBlockId}")
    public ResponseEntity<?> deleteTimeBlock(@PathVariable Long timeBlockId) {
        try {
            System.out.println("Intentando eliminar bloque ID: " + timeBlockId);

            EmployeeScheduleTimeBlock block = employeeScheduleTimeBlockRepository
                    .findById(timeBlockId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bloque no encontrado: " + timeBlockId));

            System.out.println("Bloque encontrado: " + block.getId() +
                    ", Día: " + block.getEmployeeScheduleDay().getId());

            employeeScheduleTimeBlockRepository.deleteById(timeBlockId);

            System.out.println("Bloque eliminado exitosamente");

            // Devolver JSON en lugar de String
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Bloque eliminado correctamente");
            response.put("deletedId", timeBlockId);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            System.err.println("Bloque no encontrado: " + e.getMessage());
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);

        } catch (Exception e) {
            System.err.println("Error eliminando bloque: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error interno: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PutMapping("/time-blocks")
    public ResponseEntity<?> updateTimeBlock(@RequestBody TimeBlockDTO timeBlockDTO) {
        try {
            // Validate basic input
            validateTimeBlockInput(timeBlockDTO);

            // Update time block
            EmployeeScheduleTimeBlock updatedBlock = employeeScheduleService.updateTimeBlock(timeBlockDTO);

            // Create structured response
            Map<String, Object> response = createTimeBlockResponse(updatedBlock);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    private void validateTimeBlockInput(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO.getId() == null || timeBlockDTO.getId() <= 0) {
            throw new IllegalArgumentException("Invalid time block ID");
        }
        if (timeBlockDTO.getEmployeeScheduleDayId() == null || timeBlockDTO.getEmployeeScheduleDayId() <= 0) {
            throw new IllegalArgumentException("Invalid employee schedule day ID");
        }
        if (timeBlockDTO.getNumberId() == null || timeBlockDTO.getNumberId().isEmpty()) {
            throw new IllegalArgumentException("Invalid employee identification number");
        }
    }
    private Map<String, Object> createTimeBlockResponse(EmployeeScheduleTimeBlock updatedBlock) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", updatedBlock.getId());
        response.put("employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId());
        response.put("startTime", updatedBlock.getStartTime() != null ? updatedBlock.getStartTime().toString() : null);
        response.put("endTime", updatedBlock.getEndTime() != null ? updatedBlock.getEndTime().toString() : null);
        response.put("numberId", updatedBlock.getEmployeeScheduleDay().getEmployeeSchedule().getEmployeeId().toString());
        response.put("updatedAt", updatedBlock.getUpdatedAt());

        return response;
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

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeId(@PathVariable Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByEmployeeId(employeeId);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/by-dependency-id")
    public ResponseEntity<List<Map<String, Object>>> getSchedulesByDependencyId(
            @RequestParam Long dependencyId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm:ss") LocalTime startTime,
            @RequestParam(required = false) Long shiftId) {

        if (dependencyId == null || dependencyId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        System.out.println("Parámetros recibidos:");
        System.out.println("- dependencyId: " + dependencyId);
        System.out.println("- startDate: " + startDate);
        System.out.println("- endDate: " + endDate);
        System.out.println("- startTime: " + startTime);
        System.out.println("- shiftId: " + shiftId);

        try {
            List<Map<String, Object>> result = employeeScheduleService.getSchedulesByDependencyId(
                    dependencyId, startDate, endDate, startTime, shiftId);

            System.out.println("Resultado: " + result.size() + " grupos encontrados");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("Error en getSchedulesByDependencyId: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/cleanup-empty-days/{employeeId}")
    public ResponseEntity<?> cleanupEmptyDaysForEmployee(@PathVariable Long employeeId) {
        try {
            System.out.println("Solicitud de limpieza para empleado: " + employeeId);

            employeeScheduleService.cleanupEmptyDaysForEmployee(employeeId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Limpieza de días vacíos completada");
            response.put("employeeId", employeeId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error en limpieza: " + e.getMessage());

            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
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

    @GetMapping("/by-employee-id/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeId(@PathVariable String employeeId) {
        try {
            // Convertir string a Long
            Long empId = Long.parseLong(employeeId);

            // Usar el método existente que ya tienes
            List<EmployeeScheduleDTO> schedules = employeeScheduleService.getSchedulesByEmployeeId(empId);

            return ResponseEntity.ok(schedules);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }




    @DeleteMapping("/schedule-days/{dayId}")
    public ResponseEntity<?> deleteCompleteScheduleDay(@PathVariable Long dayId) {
        try {
            // Verificar que el día existe
            EmployeeScheduleDay day = employeeScheduleDayRepository
                    .findById(dayId)
                    .orElseThrow(() -> new ResourceNotFoundException("Día de horario no encontrado con id: " + dayId));

            // Primero eliminar todos los timeBlocks de este día
            employeeScheduleTimeBlockRepository.deleteByEmployeeScheduleDayId(dayId);

            // Luego eliminar el día mismo
            employeeScheduleDayRepository.deleteById(dayId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Día y sus horarios eliminados completamente");
            response.put("dayId", dayId);
            response.put("date", day.getDate());

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno del servidor: " + e.getMessage());
        }
    }





// REEMPLAZAR el método updateTimeBlocksByDependency en tu EmployeeScheduleController.java

    @PutMapping("/time-blocks/by-dependency")
    public ResponseEntity<?> updateTimeBlocksByDependency(@RequestBody List<TimeBlockDependencyDTO> timeBlockDTOList) {
        try {
            System.out.println("=== INICIANDO PROCESAMIENTO MEJORADO ===");
            System.out.println("Número de bloques recibidos: " + (timeBlockDTOList != null ? timeBlockDTOList.size() : 0));

            if (timeBlockDTOList == null || timeBlockDTOList.isEmpty()) {
                return ResponseEntity.badRequest().body("No se proporcionaron bloques de tiempo.");
            }

            List<Map<String, Object>> processedBlocks = new ArrayList<>();
            Set<Long> affectedEmployees = new HashSet<>();
            int successCount = 0;
            int errorCount = 0;

            for (TimeBlockDependencyDTO timeBlockDTO : timeBlockDTOList) {
                try {
                    System.out.println("Procesando bloque: ID=" + timeBlockDTO.getId() +
                            ", DayID=" + timeBlockDTO.getEmployeeScheduleDayId() +
                            ", Start=" + timeBlockDTO.getStartTime() +
                            ", End=" + timeBlockDTO.getEndTime() +
                            ", NumberId=" + timeBlockDTO.getNumberId()); // ← AGREGAR ESTA LÍNEA

                    // Validaciones básicas
                    if (timeBlockDTO.getEmployeeScheduleDayId() == null || timeBlockDTO.getEmployeeScheduleDayId() <= 0) {
                        System.err.println("Error: employeeScheduleDayId inválido");
                        errorCount++;
                        continue;
                    }

                    // Obtener el día para obtener employeeId
                    EmployeeScheduleDay day = employeeScheduleDayRepository
                            .findById(timeBlockDTO.getEmployeeScheduleDayId())
                            .orElse(null);

                    if (day == null) {
                        System.err.println("Error: Día no encontrado ID=" + timeBlockDTO.getEmployeeScheduleDayId());
                        errorCount++;
                        continue;
                    }

                    Long employeeId = day.getEmployeeSchedule().getEmployeeId();
                    affectedEmployees.add(employeeId);

                    // VALIDACIÓN ADICIONAL: Verificar que numberId coincida con employeeId
                    if (timeBlockDTO.getNumberId() != null && !timeBlockDTO.getNumberId().equals(employeeId)) {
                        System.err.println("ADVERTENCIA: NumberId (" + timeBlockDTO.getNumberId() +
                                ") no coincide con EmployeeId (" + employeeId + ")");
                    }

                    // Verificar si los horarios están vacíos o son inválidos
                    boolean shouldDelete = isInvalidTimeBlock(timeBlockDTO.getStartTime(), timeBlockDTO.getEndTime());

                    if (shouldDelete) {
                        // ELIMINAR BLOQUE
                        if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
                            System.out.println("Eliminando bloque ID: " + timeBlockDTO.getId());

                            employeeScheduleTimeBlockRepository.deleteById(timeBlockDTO.getId());

                            // Verificar si el día quedó vacío
                            List<EmployeeScheduleTimeBlock> remainingBlocks =
                                    employeeScheduleTimeBlockRepository.findByEmployeeScheduleDayId(day.getId());

                            if (remainingBlocks.isEmpty()) {
                                employeeScheduleDayRepository.deleteById(day.getId());
                                System.out.println("Día eliminado: " + day.getId());
                            }

                            Map<String, Object> blockResponse = new LinkedHashMap<>();
                            blockResponse.put("id", timeBlockDTO.getId());
                            blockResponse.put("action", "DELETED");
                            blockResponse.put("numberId", timeBlockDTO.getNumberId()); // ← AGREGAR ESTA LÍNEA
                            processedBlocks.add(blockResponse);
                        } else {
                            System.out.println("Bloque sin ID válido para eliminar, omitiendo");
                        }

                    } else if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
                        // ACTUALIZAR BLOQUE EXISTENTE
                        System.out.println("Actualizando bloque existente ID: " + timeBlockDTO.getId());

                        EmployeeScheduleTimeBlock existingBlock = employeeScheduleTimeBlockRepository
                                .findById(timeBlockDTO.getId()).orElse(null);

                        if (existingBlock != null) {
                            existingBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
                            existingBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));
                            existingBlock.setUpdatedAt(new Date());

                            EmployeeScheduleTimeBlock updatedBlock = employeeScheduleTimeBlockRepository.save(existingBlock);

                            Map<String, Object> blockResponse = new LinkedHashMap<>();
                            blockResponse.put("id", updatedBlock.getId());
                            blockResponse.put("employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId());
                            blockResponse.put("startTime", updatedBlock.getStartTime().toString());
                            blockResponse.put("endTime", updatedBlock.getEndTime().toString());
                            blockResponse.put("numberId", timeBlockDTO.getNumberId()); // ← AGREGAR ESTA LÍNEA
                            blockResponse.put("action", "UPDATED");
                            processedBlocks.add(blockResponse);
                        } else {
                            System.err.println("Bloque no encontrado para actualizar: " + timeBlockDTO.getId());
                            errorCount++;
                            continue;
                        }

                    } else {
                        // CREAR NUEVO BLOQUE
                        System.out.println("Creando nuevo bloque para día: " + timeBlockDTO.getEmployeeScheduleDayId());

                        EmployeeScheduleTimeBlock newBlock = new EmployeeScheduleTimeBlock();
                        newBlock.setEmployeeScheduleDay(day);
                        newBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
                        newBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));
                        newBlock.setCreatedAt(new Date());

                        EmployeeScheduleTimeBlock savedBlock = employeeScheduleTimeBlockRepository.save(newBlock);

                        Map<String, Object> blockResponse = new LinkedHashMap<>();
                        blockResponse.put("id", savedBlock.getId());
                        blockResponse.put("employeeScheduleDayId", savedBlock.getEmployeeScheduleDay().getId());
                        blockResponse.put("startTime", savedBlock.getStartTime().toString());
                        blockResponse.put("endTime", savedBlock.getEndTime().toString());
                        blockResponse.put("numberId", timeBlockDTO.getNumberId()); // ← AGREGAR ESTA LÍNEA
                        blockResponse.put("action", "CREATED");
                        processedBlocks.add(blockResponse);
                    }

                    successCount++;

                } catch (Exception e) {
                    System.err.println("Error procesando bloque: " + e.getMessage());
                    e.printStackTrace();
                    errorCount++;
                }
            }

            // Limpiar empleados afectados
            for (Long employeeId : affectedEmployees) {
                try {
                    employeeScheduleService.cleanupEmptyDaysForEmployee(employeeId);
                    System.out.println("Limpieza completada para empleado: " + employeeId);
                } catch (Exception e) {
                    System.err.println("Error limpiando empleado " + employeeId + ": " + e.getMessage());
                }
            }

            System.out.println("=== RESUMEN FINAL ===");
            System.out.println("Procesados exitosamente: " + successCount);
            System.out.println("Errores: " + errorCount);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Bloques procesados correctamente");
            response.put("processedCount", successCount);
            response.put("errorCount", errorCount);
            response.put("processedBlocks", processedBlocks);
            response.put("affectedEmployees", affectedEmployees.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno del servidor: " + e.getMessage());
        }
    }

// AGREGAR estos métodos auxiliares al EmployeeScheduleController.java

    private boolean isInvalidTimeBlock(String startTime, String endTime) {
        if (startTime == null || endTime == null) {
            return true;
        }

        String start = startTime.trim();
        String end = endTime.trim();

        // Verificar si están vacíos
        if (start.isEmpty() || end.isEmpty()) {
            return true;
        }

        // Verificar si contienen placeholders de máscara de input
        if (start.contains("__") || end.contains("__")) {
            return true;
        }

        // Verificar si son horarios por defecto inválidos
        if ("00:00:00".equals(start) && "00:00:00".equals(end)) {
            return true;
        }

        return false;
    }

    private String normalizeTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Horario no puede estar vacío");
        }

        timeStr = timeStr.trim();

        // Remover placeholders de máscaras de input
        if (timeStr.contains("__")) {
            throw new IllegalArgumentException("Horario contiene caracteres inválidos: " + timeStr);
        }

        // Formato HH:mm:ss (ya completo)
        if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return timeStr;
        }

        // Formato HH:mm (agregar segundos)
        if (timeStr.matches("\\d{2}:\\d{2}")) {
            return timeStr + ":00";
        }

        // Formato H:mm (agregar cero inicial)
        if (timeStr.matches("\\d{1}:\\d{2}")) {
            return "0" + timeStr + ":00";
        }

        throw new IllegalArgumentException("Formato de horario inválido: " + timeStr);
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

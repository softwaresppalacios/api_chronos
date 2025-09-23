package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.AssignmentRequest;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.AssignmentResult;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.HolidayConfirmationRequest;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.ValidationResult;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDependencyDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.service.common.TimeService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeScheduleService;

import java.sql.Time;
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
    private final TimeService timeService;

    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService, TimeService timeService,
                                      EmployeeScheduleDayRepository employeeScheduleDayRepository,
                                      EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository
                                      ) {
        this.employeeScheduleService = employeeScheduleService;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.employeeScheduleTimeBlockRepository = employeeScheduleTimeBlockRepository;
        this.timeService = timeService;


    }

    // =================== SCHEDULE MANAGEMENT ===================

    // REEMPLAZAR el método assign-multiple en EmployeeScheduleController.java
    @PostMapping("/assign-multiple")
    public ResponseEntity<Map<String, Object>> assignMultiple(
            @Valid @RequestBody AssignmentRequest request
    ) {
        Map<String, Object> body = new HashMap<>();

        try {
            // ===== VALIDACIÓN INICIAL =====
            if (request == null) {
                body.put("success", false);
                body.put("error", "BAD_REQUEST");
                body.put("message", "El cuerpo de la solicitud es requerido");
                return ResponseEntity.badRequest().body(body);
            }

            if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
                body.put("success", false);
                body.put("error", "BAD_REQUEST");
                body.put("message", "Debe proporcionar al menos una asignación");
                return ResponseEntity.badRequest().body(body);
            }

            // ===== DEBUG LOGGING =====
            System.out.println("=== ASSIGN MULTIPLE REQUEST ===");
            System.out.println("Request: " + request);
            System.out.println("Assignments size: " + request.getAssignments().size());

            for (int i = 0; i < request.getAssignments().size(); i++) {
                ScheduleDto.ScheduleAssignment a = request.getAssignments().get(i);
                System.out.println("Assignment " + i + ": " + a);

                // Validación básica de cada assignment
                if (a.getEmployeeId() == null || a.getEmployeeId() <= 0) {
                    body.put("success", false);
                    body.put("error", "VALIDATION_ERROR");
                    body.put("message", "Employee ID inválido en asignación " + (i + 1));
                    return ResponseEntity.badRequest().body(body);
                }

                if (a.getShiftId() == null || a.getShiftId() <= 0) {
                    body.put("success", false);
                    body.put("error", "VALIDATION_ERROR");
                    body.put("message", "Shift ID inválido en asignación " + (i + 1));
                    return ResponseEntity.badRequest().body(body);
                }

                if (a.getStartDate() == null) {
                    body.put("success", false);
                    body.put("error", "VALIDATION_ERROR");
                    body.put("message", "Fecha de inicio requerida en asignación " + (i + 1));
                    return ResponseEntity.badRequest().body(body);
                }
            }
            System.out.println("=== END DEBUG ===");

            // ===== PROCESAMIENTO PRINCIPAL =====
            AssignmentResult result = employeeScheduleService.processMultipleAssignments(request);

            // ===== VALIDACIÓN DE RESULTADO =====
            if (result == null) {
                System.err.println("WARNING: El servicio retornó null, creando resultado por defecto");
                result = new AssignmentResult();
                result.setSuccess(true);
                result.setMessage("Asignación procesada.");
                result.setUpdatedEmployees(new ArrayList<>());
                result.setHolidayWarnings(new ArrayList<>());
                result.setRequiresConfirmation(false);
            } else {
                // Normalizar listas para evitar nulls en JSON
                if (result.getUpdatedEmployees() == null) {
                    result.setUpdatedEmployees(new ArrayList<>());
                }
                if (result.getHolidayWarnings() == null) {
                    result.setHolidayWarnings(new ArrayList<>());
                }
            }

            // ===== RESPUESTA EXITOSA =====
            body.put("success", result.isSuccess());
            if (result.getMessage() != null && !result.getMessage().isBlank()) {
                body.put("message", result.getMessage());
            }
            body.put("result", result);

            System.out.println("Asignación completada exitosamente para " +
                    request.getAssignments().size() + " asignaciones");

            return ResponseEntity.ok(body);

        } catch (ScheduleDto.ConflictException ce) {
            System.err.println("ConflictException: " + ce.getMessage());
            body.put("success", false);
            body.put("error", "SCHEDULE_CONFLICT");
            body.put("message", ce.getMessage());
            body.put("conflicts", ce.getConflicts());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);

        } catch (ScheduleDto.ValidationException ve) {
            System.err.println("ValidationException: " + ve.getMessage());
            if (ve.getValidationErrors() != null) {
                System.err.println("Validation errors: " + ve.getValidationErrors());
            }
            body.put("success", false);
            body.put("error", "VALIDATION_ERROR");
            body.put("message", ve.getMessage() != null ? ve.getMessage() : "La solicitud no pasó validación");
            body.put("validationErrors", ve.getValidationErrors() != null ? ve.getValidationErrors() : new ArrayList<>());
            return ResponseEntity.badRequest().body(body);

        } catch (IllegalArgumentException iae) {
            System.err.println("IllegalArgumentException: " + iae.getMessage());
            iae.printStackTrace();
            body.put("success", false);
            body.put("error", "BAD_REQUEST");
            body.put("message", iae.getMessage() != null ? iae.getMessage() : "La solicitud contiene datos inválidos");
            body.put("rootCause", iae.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(body);

        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            System.err.println("DataIntegrityViolationException: " + dive.getMessage());
            dive.printStackTrace();
            body.put("success", false);
            body.put("error", "SCHEDULE_CONFLICT");
            body.put("message", "No se pudo asignar los turnos - posible conflicto en base de datos");
            body.put("rootCause", "DataIntegrityViolationException");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);

        } catch (RuntimeException re) {
            System.err.println("RuntimeException: " + re.getMessage());
            re.printStackTrace();

            // Extraer causa raíz para mejor diagnóstico
            Throwable rootCause = re;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }

            body.put("success", false);
            body.put("error", "RUNTIME_ERROR");
            body.put("message", "Error en tiempo de ejecución: " + re.getMessage());
            body.put("rootCause", rootCause.getClass().getSimpleName());
            body.put("rootMessage", rootCause.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);

        } catch (Exception ex) {
            System.err.println("Unexpected Exception: " + ex.getClass().getName() + " - " + ex.getMessage());
            ex.printStackTrace();

            body.put("success", false);
            body.put("error", "INTERNAL_ERROR");
            body.put("message", "Ocurrió un error inesperado: " + ex.getMessage());
            body.put("rootCause", ex.getClass().getSimpleName());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    private String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + (c.getMessage() != null ? (": " + c.getMessage()) : "");
    }


    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onBeanValidation(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "VALIDATION_ERROR");
        body.put("message", "La solicitud no pasó validación");
        body.put("validationErrors", ex.getBindingResult().getFieldErrors()
                .stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).toList());
        return ResponseEntity.badRequest().body(body);
    }


    @PostMapping("/confirm-holiday-assignment")
    public ResponseEntity<AssignmentResult> confirmHolidayAssignment(
            @Valid @RequestBody HolidayConfirmationRequest request) {
        AssignmentResult result = employeeScheduleService.processHolidayAssignment(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/validate-assignment")
    public ResponseEntity<ValidationResult> validateAssignment(
            @Valid @RequestBody AssignmentRequest request) {
        ValidationResult result = employeeScheduleService.validateAssignmentOnly(request);
        return ResponseEntity.ok(result);
    }


    // =================== SCHEDULE QUERIES ===================

    @GetMapping
    public ResponseEntity<List<EmployeeScheduleDTO>> getAllSchedules() {
        List<EmployeeScheduleDTO> schedules = employeeScheduleService.getAllEmployeeSchedules();
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeScheduleDTO> getScheduleById(@PathVariable Long id) {
        EmployeeScheduleDTO schedule = employeeScheduleService.getEmployeeScheduleById(id);
        return ResponseEntity.ok(schedule);
    }


    @GetMapping("/employee/{employeeId}/hours-summary")
    public ResponseEntity<EmployeeHoursSummaryDTO> getEmployeeHoursSummary(
            @PathVariable Long employeeId) {
        EmployeeHoursSummaryDTO summary =
                employeeScheduleService.calculateEmployeeHoursSummary(employeeId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/by-employee-ids")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeIds(
            @RequestParam List<Long> employeeIds) {
        List<EmployeeScheduleDTO> schedules =
                employeeScheduleService.getSchedulesByEmployeeIds(employeeIds);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByShiftId(
            @PathVariable Long shiftId) {
        List<EmployeeScheduleDTO> schedules =
                employeeScheduleService.getSchedulesByShiftId(shiftId);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/by-date-range")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        List<EmployeeScheduleDTO> schedules =
                employeeScheduleService.getSchedulesByDateRange(startDate, endDate);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/by-dependency-id")
    public ResponseEntity<List<Map<String, Object>>> getSchedulesByDependencyId(
            @RequestParam(required = false) Long dependencyId, // ← Ahora es opcional
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm:ss") LocalTime startTime,
            @RequestParam(required = false) Long shiftId) {

        // Validar que al menos hay un parámetro de búsqueda
        if (dependencyId == null && startDate == null && endDate == null && startTime == null && shiftId == null) {
            System.out.println("ERROR: No se proporcionaron parámetros de búsqueda");
            return ResponseEntity.badRequest().build();
        }

        try {
            System.out.println("Parámetros recibidos:");
            System.out.println("- dependencyId: " + (dependencyId != null ? dependencyId : "TODAS"));
            System.out.println("- startDate: " + startDate);
            System.out.println("- endDate: " + endDate);
            System.out.println("- startTime: " + startTime);
            System.out.println("- shiftId: " + shiftId);

            List<Map<String, Object>> result = employeeScheduleService.getSchedulesByDependencyId(
                    dependencyId, startDate, endDate, startTime, shiftId);

            System.out.println("Resultado: " + result.size() + " grupos encontrados");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("Error en getSchedulesByDependencyId: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
    @GetMapping("/by-employee-id/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeIdString(@PathVariable String employeeId) {
        try {
            Long empId = Long.parseLong(employeeId);
            List<EmployeeScheduleDTO> schedules = employeeScheduleService.getCompleteSchedulesByEmployeeId(empId);
            return ResponseEntity.ok(schedules);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeScheduleDTO>> getSchedulesByEmployeeId(@PathVariable Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            System.out.println("=== ENDPOINT /employee/" + employeeId + " ===");

            // ESTA debe ser la línea clave - llamando al método correcto
            List<EmployeeScheduleDTO> schedules = employeeScheduleService.getCompleteSchedulesByEmployeeId(employeeId);

            System.out.println("Schedules encontrados: " + schedules.size());
            return ResponseEntity.ok(schedules);

        } catch (Exception e) {
            System.err.println("Error en endpoint individual: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }




        // =================== SCHEDULE CRUD ===================

    @PostMapping
    public ResponseEntity<List<Map<String, Object>>> createSchedules(
            @RequestBody List<Map<String, Object>> scheduleRequests) {
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
            return ResponseEntity.badRequest().body(
                    List.of(Map.of("error", e.getMessage()))
            );
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeSchedule> updateSchedule(
            @PathVariable Long id,
            @RequestBody EmployeeSchedule schedule) {
        EmployeeSchedule updated = employeeScheduleService.updateEmployeeSchedule(id, schedule);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        employeeScheduleService.deleteEmployeeSchedule(id);
        return ResponseEntity.noContent().build();
    }

    // =================== TIME BLOCK MANAGEMENT ===================

    @PostMapping("/timeblocks")
    public ResponseEntity<Map<String, Object>> createTimeBlock(
            @RequestBody Map<String, Object> timeBlockData) {
        try {
            Long employeeScheduleDayId = Long.parseLong(timeBlockData.get("employeeScheduleDayId").toString());
            String startTime = timeBlockData.get("startTime").toString();
            String endTime = timeBlockData.get("endTime").toString();

            EmployeeScheduleDay day = employeeScheduleDayRepository
                    .findById(employeeScheduleDayId)
                    .orElseThrow(() -> new ResourceNotFoundException("Día no encontrado: " + employeeScheduleDayId));

            EmployeeScheduleTimeBlock newBlock = new EmployeeScheduleTimeBlock();
            newBlock.setEmployeeScheduleDay(day);
            newBlock.setStartTime(Time.valueOf(startTime));
            newBlock.setEndTime(Time.valueOf(endTime));
            newBlock.setCreatedAt(new Date());

            EmployeeScheduleTimeBlock savedBlock = employeeScheduleTimeBlockRepository.save(newBlock);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", savedBlock.getId());
            response.put("message", "Bloque creado correctamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/time-blocks")
    public ResponseEntity<Map<String, Object>> updateTimeBlock(
            @RequestBody TimeBlockDTO timeBlockDTO) {
        try {
            validateTimeBlockInput(timeBlockDTO);
            EmployeeScheduleTimeBlock updatedBlock = employeeScheduleService.updateTimeBlock(timeBlockDTO);
            Map<String, Object> response = createTimeBlockResponse(updatedBlock);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/time-blocks/by-dependency")
    public ResponseEntity<Map<String, Object>> updateTimeBlocksByDependency(
            @RequestBody List<TimeBlockDependencyDTO> timeBlockDTOList) {
        try {
            if (timeBlockDTOList == null || timeBlockDTOList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se proporcionaron bloques de tiempo."));
            }

            List<Map<String, Object>> processedBlocks = new ArrayList<>();
            Set<Long> affectedEmployees = new HashSet<>();
            int successCount = 0;
            int errorCount = 0;

            for (TimeBlockDependencyDTO timeBlockDTO : timeBlockDTOList) {
                try {
                    if (timeBlockDTO.getEmployeeScheduleDayId() == null || timeBlockDTO.getEmployeeScheduleDayId() <= 0) {
                        errorCount++;
                        continue;
                    }

                    EmployeeScheduleDay day = employeeScheduleDayRepository
                            .findById(timeBlockDTO.getEmployeeScheduleDayId())
                            .orElse(null);

                    if (day == null) {
                        errorCount++;
                        continue;
                    }

                    Long employeeId = day.getEmployeeSchedule().getEmployeeId();
                    affectedEmployees.add(employeeId);

                    boolean shouldDelete = isInvalidTimeBlock(timeBlockDTO.getStartTime(), timeBlockDTO.getEndTime());

                    if (shouldDelete) {
                        // ELIMINAR BLOQUE
                        if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
                            employeeScheduleTimeBlockRepository.deleteById(timeBlockDTO.getId());

                            List<EmployeeScheduleTimeBlock> remainingBlocks =
                                    employeeScheduleTimeBlockRepository.findByEmployeeScheduleDayId(day.getId());

                            if (remainingBlocks.isEmpty()) {
                                employeeScheduleDayRepository.deleteById(day.getId());
                            }

                            Map<String, Object> blockResponse = new LinkedHashMap<>();
                            blockResponse.put("id", timeBlockDTO.getId());
                            blockResponse.put("action", "DELETED");
                            blockResponse.put("numberId", timeBlockDTO.getNumberId());
                            processedBlocks.add(blockResponse);
                        }
                    } else if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
                        // ACTUALIZAR BLOQUE EXISTENTE
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
                            blockResponse.put("numberId", timeBlockDTO.getNumberId());
                            blockResponse.put("action", "UPDATED");
                            processedBlocks.add(blockResponse);
                        } else {
                            errorCount++;
                            continue;
                        }
                    } else {
                        // CREAR NUEVO BLOQUE
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
                        blockResponse.put("numberId", timeBlockDTO.getNumberId());
                        blockResponse.put("action", "CREATED");
                        processedBlocks.add(blockResponse);
                    }

                    successCount++;

                } catch (Exception e) {
                    errorCount++;
                }
            }

            // Limpiar empleados afectados
            for (Long employeeId : affectedEmployees) {
                try {
                    employeeScheduleService.cleanupEmptyDaysForEmployee(employeeId);
                } catch (Exception e) {
                    // Log error but continue
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Bloques procesados correctamente");
            response.put("processedCount", successCount);
            response.put("errorCount", errorCount);
            response.put("processedBlocks", processedBlocks);
            response.put("affectedEmployees", affectedEmployees.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor: " + e.getMessage()));
        }
    }

    @DeleteMapping("/timeblocks/{timeBlockId}")
    public ResponseEntity<Map<String, Object>> deleteTimeBlock(@PathVariable Long timeBlockId) {
        try {
            EmployeeScheduleTimeBlock block = employeeScheduleTimeBlockRepository
                    .findById(timeBlockId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bloque no encontrado: " + timeBlockId));

            Long dayId = block.getEmployeeScheduleDay().getId();
            employeeScheduleTimeBlockRepository.deleteById(timeBlockId);

            // Verificar si el día quedó vacío
            List<EmployeeScheduleTimeBlock> remainingBlocks =
                    employeeScheduleTimeBlockRepository.findByEmployeeScheduleDayId(dayId);

            if (remainingBlocks.isEmpty()) {
                employeeScheduleDayRepository.deleteById(dayId);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Bloque eliminado correctamente");
            response.put("deletedId", timeBlockId);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }

    @DeleteMapping("/schedule-days/{dayId}")
    public ResponseEntity<Map<String, Object>> deleteCompleteScheduleDay(@PathVariable Long dayId) {
        try {
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
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor: " + e.getMessage()));
        }
    }

    // =================== MAINTENANCE OPERATIONS ===================

    @PostMapping("/cleanup-empty-days/{employeeId}")
    public ResponseEntity<Map<String, Object>> cleanupEmptyDaysForEmployee(
            @PathVariable Long employeeId) {
        employeeScheduleService.cleanupEmptyDaysForEmployee(employeeId);

        Map<String, Object> response = Map.of(
                "success", true,
                "message", "Limpieza de días vacíos completada",
                "employeeId", employeeId
        );
        return ResponseEntity.ok(response);
    }

    // =================== UTILITY METHODS ===================

    private boolean isInvalidTimeBlock(String startTime, String endTime) {
        if (startTime == null || endTime == null) return true;

        String start = startTime.trim();
        String end = endTime.trim();

        return start.isEmpty() || end.isEmpty() ||
                start.contains("__") || end.contains("__") ||
                ("00:00:00".equals(start) && "00:00:00".equals(end));
    }

    private String normalizeTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Horario no puede estar vacío");
        }

        timeStr = timeStr.trim();

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
        if (created.getDays() != null) {
            daysStructure.put("items", created.getDays().stream()
                    .map(this::convertDayToMap)
                    .collect(Collectors.toList()));
        } else {
            daysStructure.put("items", Collections.emptyList());
        }

        response.put("days", daysStructure);
        return response;
    }

    private EmployeeSchedule parseIndividualRequest(Map<String, Object> scheduleRequest) {
        EmployeeSchedule schedule = new EmployeeSchedule();

        Object employeeIdObj = scheduleRequest.get("employeeId");
        if (employeeIdObj == null) {
            throw new IllegalArgumentException("Employee ID is required");
        }
        schedule.setEmployeeId(Long.parseLong(employeeIdObj.toString()));

        Object shiftObj = scheduleRequest.get("shift");
        if (shiftObj == null) {
            throw new IllegalArgumentException("Shift is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> shiftMap = (Map<String, Object>) shiftObj;
        Shifts shift = new Shifts();
        shift.setId(Long.parseLong(shiftMap.get("id").toString()));
        schedule.setShift(shift);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        schedule.setStartDate(LocalDate.parse(scheduleRequest.get("startDate").toString())); // usa ISO yyyy-MM-dd
        if (scheduleRequest.get("endDate") != null && !scheduleRequest.get("endDate").toString().isBlank()) {
            schedule.setEndDate(LocalDate.parse(scheduleRequest.get("endDate").toString()));
        }

        return schedule;
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
        dayMap.put("id", day.getId());
        dayMap.put("date", day.getDate());
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        if (day.getTimeBlocks() != null) {
            List<Map<String, String>> timeBlocks = day.getTimeBlocks().stream()
                    .map(this::convertTimeBlockToMap)
                    .collect(Collectors.toList());
            dayMap.put("timeBlocks", timeBlocks);
        } else {
            dayMap.put("timeBlocks", Collections.emptyList());
        }

        return dayMap;
    }

    private Map<String, String> convertTimeBlockToMap(EmployeeScheduleTimeBlock block) {
        Map<String, String> blockMap = new HashMap<>();
        blockMap.put("startTime", block.getStartTime().toString());
        blockMap.put("endTime", block.getEndTime().toString());
        return blockMap;
    }


    @GetMapping("/employee/{employeeId}/daily-breakdown")
    public ResponseEntity<Map<String, Object>> getDailyBreakdown(@PathVariable Long employeeId) {
        employeeScheduleService.testDiagnostic(employeeId);

        try {
            Map<String, Object> breakdown = employeeScheduleService.getDailyBreakdown(employeeId);
            return ResponseEntity.ok(breakdown);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo obtener el detalle diario"));
        }
    }
}
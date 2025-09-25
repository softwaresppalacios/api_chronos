package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleAssignmentGroupDTO;
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
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment.ScheduleAssignmentGroupService;
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
    private final ScheduleAssignmentGroupService groupService;
    public EmployeeScheduleController(EmployeeScheduleService employeeScheduleService, TimeService timeService,
                                      EmployeeScheduleDayRepository employeeScheduleDayRepository,
                                      EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository,
                                      ScheduleAssignmentGroupService groupService
                                      ) {
        this.employeeScheduleService = employeeScheduleService;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.employeeScheduleTimeBlockRepository = employeeScheduleTimeBlockRepository;
        this.timeService = timeService;
        this.groupService = groupService;


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
            for (int i = 0; i < request.getAssignments().size(); i++) {
                ScheduleDto.ScheduleAssignment a = request.getAssignments().get(i);


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

            body.put("success", result.isSuccess());
            if (result.getMessage() != null && !result.getMessage().isBlank()) {
                body.put("message", result.getMessage());
            }
            body.put("result", result);


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
        if (dependencyId == null && startDate == null && endDate == null && startTime == null && shiftId == null) {
            return ResponseEntity.badRequest().build();
        }

        try {

            List<Map<String, Object>> result = employeeScheduleService.getSchedulesByDependencyId(
                    dependencyId, startDate, endDate, startTime, shiftId);

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
            List<EmployeeScheduleDTO> schedules = employeeScheduleService.getCompleteSchedulesByEmployeeId(employeeId);
            return ResponseEntity.ok(schedules);

        } catch (Exception e) {
            System.err.println("Error en endpoint individual: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


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


    // ✅ MÉTODO DE RECÁLCULO MEJORADO Y SINCRONIZADO
    private void recalculateEmployeeGroupsSync(Long employeeId) {
        try {
            System.out.println("\n🔄 === INICIANDO RECÁLCULO PARA EMPLEADO: " + employeeId + " ===");

            // Verificar que groupService no sea null
            if (groupService == null) {
                System.err.println("❌ ERROR CRÍTICO: groupService es NULL");
                return;
            }

            // Obtener grupos del empleado
            List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId);
            System.out.println("📊 GRUPOS ENCONTRADOS: " + groups.size());

            if (groups.isEmpty()) {
                System.out.println("⚠️ NO HAY GRUPOS PARA RECALCULAR - empleado: " + employeeId);
                return;
            }

            // Recalcular cada grupo
            int recalculatedCount = 0;
            for (ScheduleAssignmentGroupDTO group : groups) {
                try {
                    System.out.println("\n🔢 RECALCULANDO GRUPO ID: " + group.getId());

                    // ANTES DEL RECÁLCULO
                    System.out.println("📊 ANTES:");
                    System.out.println("  - Total Hours: " + group.getTotalHours());
                    System.out.println("  - Regular Hours: " + group.getRegularHours());
                    System.out.println("  - Overtime Hours: " + group.getOvertimeHours());

                    // EJECUTAR RECÁLCULO
                    groupService.recalculateGroup(group.getId());

                    // VERIFICAR DESPUÉS
                    ScheduleAssignmentGroupDTO updated = groupService.getGroupById(group.getId());
                    System.out.println("📊 DESPUÉS:");
                    System.out.println("  - Total Hours: " + updated.getTotalHours());
                    System.out.println("  - Regular Hours: " + updated.getRegularHours());
                    System.out.println("  - Overtime Hours: " + updated.getOvertimeHours());

                    recalculatedCount++;
                    System.out.println("✅ GRUPO " + group.getId() + " RECALCULADO EXITOSAMENTE");

                } catch (Exception e) {
                    System.err.println("❌ ERROR RECALCULANDO GRUPO " + group.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("\n✅ === RECÁLCULO COMPLETADO ===");
            System.out.println("👤 Empleado: " + employeeId);
            System.out.println("📊 Grupos recalculados: " + recalculatedCount + "/" + groups.size());

        } catch (Exception e) {
            System.err.println("❌ ERROR GENERAL EN RECÁLCULO EMPLEADO " + employeeId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void autoRecalculateEmployeeGroups(Long employeeId) {
        try {
            System.out.println("🔄 === INICIANDO RECÁLCULO PARA EMPLEADO: " + employeeId + " ===");

            // Verificar que groupService no sea null
            if (groupService == null) {
                System.err.println("❌ ERROR CRÍTICO: groupService es NULL");
                return;
            }

            List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId);
            System.out.println("📊 GRUPOS ENCONTRADOS: " + groups.size());

            if (groups.isEmpty()) {
                System.out.println("⚠️ NO HAY GRUPOS PARA RECALCULAR - empleado: " + employeeId);
                return;
            }

            for (ScheduleAssignmentGroupDTO group : groups) {
                try {
                    System.out.println("🔢 RECALCULANDO GRUPO ID: " + group.getId());

                    // ANTES DEL RECÁLCULO
                    System.out.println("📊 ANTES - Total Hours: " + group.getTotalHours());
                    System.out.println("📊 ANTES - Regular Hours: " + group.getRegularHours());

                    groupService.recalculateGroup(group.getId());

                    // VERIFICAR DESPUÉS
                    ScheduleAssignmentGroupDTO updated = groupService.getGroupById(group.getId());
                    System.out.println("📊 DESPUÉS - Total Hours: " + updated.getTotalHours());
                    System.out.println("📊 DESPUÉS - Regular Hours: " + updated.getRegularHours());

                    System.out.println("✅ GRUPO " + group.getId() + " RECALCULADO EXITOSAMENTE");

                } catch (Exception e) {
                    System.err.println("❌ ERROR RECALCULANDO GRUPO " + group.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("✅ === RECÁLCULO COMPLETADO PARA EMPLEADO: " + employeeId + " ===");

        } catch (Exception e) {
            System.err.println("❌ ERROR EN RECÁLCULO EMPLEADO " + employeeId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    @PutMapping("/time-blocks/by-dependency")
    public ResponseEntity<Map<String, Object>> updateTimeBlocksByDependency(
            @RequestBody List<TimeBlockDependencyDTO> timeBlockDTOList) {
        try {
            if (timeBlockDTOList == null || timeBlockDTOList.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            List<Map<String, Object>> processedBlocks = new ArrayList<>();
            Map<String, Object> response = new LinkedHashMap<>();

            // 📊 RECOLECTAR EMPLEADOS AFECTADOS PARA RECÁLCULO
            Set<Long> affectedEmployees = new HashSet<>();

            System.out.println("📄 PROCESANDO " + timeBlockDTOList.size() + " BLOQUES");

            for (TimeBlockDependencyDTO timeBlockDTO : timeBlockDTOList) {
                System.out.println("\n📦 PROCESANDO BLOQUE: " + timeBlockDTO);
                System.out.println("  - ID: " + timeBlockDTO.getId());
                System.out.println("  - Start Time: " + timeBlockDTO.getStartTime());
                System.out.println("  - End Time: " + timeBlockDTO.getEndTime());
                System.out.println("  - Break Start Time: " + timeBlockDTO.getBreakStartTime());
                System.out.println("  - Break End Time: " + timeBlockDTO.getBreakEndTime());

                // 📊 AGREGAR EMPLEADO A LA LISTA DE AFECTADOS
                if (timeBlockDTO.getNumberId() != null) {
                    try {
                        affectedEmployees.add(timeBlockDTO.getNumberId());
                    } catch (Exception e) {
                        System.err.println("⚠️ Error parseando employeeId: " + timeBlockDTO.getNumberId());
                    }
                }

                try {
                    // Validaciones básicas
                    if (timeBlockDTO.getEmployeeScheduleDayId() == null) {
                        System.out.println("❌ ERROR: employeeScheduleDayId es null");
                        continue;
                    }

                    boolean isDelete = (timeBlockDTO.getStartTime() == null || timeBlockDTO.getStartTime().trim().isEmpty()) &&
                            (timeBlockDTO.getEndTime() == null || timeBlockDTO.getEndTime().trim().isEmpty());

                    if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
                        // ✅ ACTUALIZAR BLOQUE EXISTENTE
                        System.out.println("📄 ACTUALIZANDO BLOQUE EXISTENTE ID: " + timeBlockDTO.getId());

                        EmployeeScheduleTimeBlock existingBlock = employeeScheduleTimeBlockRepository
                                .findById(timeBlockDTO.getId()).orElse(null);

                        if (existingBlock != null) {
                            if (isDelete) {
                                // ✅ ELIMINAR BLOQUE
                                System.out.println("🗑️ ELIMINANDO BLOQUE ID: " + timeBlockDTO.getId());
                                employeeScheduleTimeBlockRepository.delete(existingBlock);

                                Map<String, Object> blockResponse = new LinkedHashMap<>();
                                blockResponse.put("id", timeBlockDTO.getId());
                                blockResponse.put("action", "DELETED");
                                blockResponse.put("numberId", timeBlockDTO.getNumberId());
                                processedBlocks.add(blockResponse);
                            } else {
                                // ✅ ACTUALIZAR BLOQUE CON BREAKS
                                System.out.println("💾 ACTUALIZANDO CONTENIDO DEL BLOQUE");

                                existingBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
                                existingBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));

                                // ✅ MANEJAR BREAKS EN ACTUALIZACIÓN
                                if (timeBlockDTO.getBreakStartTime() != null && !timeBlockDTO.getBreakStartTime().trim().isEmpty()) {
                                    existingBlock.setBreakStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getBreakStartTime())));
                                    System.out.println("☕ Break start actualizado: " + timeBlockDTO.getBreakStartTime());
                                } else {
                                    existingBlock.setBreakStartTime(null);
                                    System.out.println("🧹 Break start limpiado");
                                }

                                if (timeBlockDTO.getBreakEndTime() != null && !timeBlockDTO.getBreakEndTime().trim().isEmpty()) {
                                    existingBlock.setBreakEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getBreakEndTime())));
                                    System.out.println("☕ Break end actualizado: " + timeBlockDTO.getBreakEndTime());
                                } else {
                                    existingBlock.setBreakEndTime(null);
                                    System.out.println("🧹 Break end limpiado");
                                }

                                existingBlock.setUpdatedAt(new Date());
                                EmployeeScheduleTimeBlock updatedBlock = employeeScheduleTimeBlockRepository.save(existingBlock);

                                // Respuesta con breaks incluidos
                                Map<String, Object> blockResponse = new LinkedHashMap<>();
                                blockResponse.put("id", updatedBlock.getId());
                                blockResponse.put("employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId());
                                blockResponse.put("startTime", updatedBlock.getStartTime().toString());
                                blockResponse.put("endTime", updatedBlock.getEndTime().toString());

                                // ✅ INCLUIR BREAKS EN RESPUESTA
                                if (updatedBlock.getBreakStartTime() != null) {
                                    blockResponse.put("breakStartTime", updatedBlock.getBreakStartTime().toString());
                                }
                                if (updatedBlock.getBreakEndTime() != null) {
                                    blockResponse.put("breakEndTime", updatedBlock.getBreakEndTime().toString());
                                }

                                blockResponse.put("numberId", timeBlockDTO.getNumberId());
                                blockResponse.put("action", "UPDATED");
                                processedBlocks.add(blockResponse);

                                System.out.println("✅ BLOQUE ACTUALIZADO: " + blockResponse);
                            }
                        } else {
                            System.out.println("❌ NO SE ENCONTRÓ BLOQUE CON ID: " + timeBlockDTO.getId());
                        }
                    } else if (!isDelete) {
                        // ✅ CREAR NUEVO BLOQUE CON BREAKS
                        System.out.println("➕ CREANDO NUEVO BLOQUE");

                        EmployeeScheduleDay day = employeeScheduleDayRepository
                                .findById(timeBlockDTO.getEmployeeScheduleDayId()).orElse(null);

                        if (day != null) {
                            EmployeeScheduleTimeBlock newBlock = new EmployeeScheduleTimeBlock();
                            newBlock.setEmployeeScheduleDay(day);
                            newBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
                            newBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));

                            // ✅ MANEJAR BREAKS EN CREACIÓN
                            if (timeBlockDTO.getBreakStartTime() != null && !timeBlockDTO.getBreakStartTime().trim().isEmpty()) {
                                newBlock.setBreakStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getBreakStartTime())));
                                System.out.println("☕ Break start asignado: " + timeBlockDTO.getBreakStartTime());
                            }

                            if (timeBlockDTO.getBreakEndTime() != null && !timeBlockDTO.getBreakEndTime().trim().isEmpty()) {
                                newBlock.setBreakEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getBreakEndTime())));
                                System.out.println("☕ Break end asignado: " + timeBlockDTO.getBreakEndTime());
                            }

                            newBlock.setCreatedAt(new Date());
                            EmployeeScheduleTimeBlock savedBlock = employeeScheduleTimeBlockRepository.save(newBlock);

                            // Respuesta con breaks incluidos
                            Map<String, Object> blockResponse = new LinkedHashMap<>();
                            blockResponse.put("id", savedBlock.getId());
                            blockResponse.put("employeeScheduleDayId", savedBlock.getEmployeeScheduleDay().getId());
                            blockResponse.put("startTime", savedBlock.getStartTime().toString());
                            blockResponse.put("endTime", savedBlock.getEndTime().toString());

                            // ✅ INCLUIR BREAKS EN RESPUESTA
                            if (savedBlock.getBreakStartTime() != null) {
                                blockResponse.put("breakStartTime", savedBlock.getBreakStartTime().toString());
                            }
                            if (savedBlock.getBreakEndTime() != null) {
                                blockResponse.put("breakEndTime", savedBlock.getBreakEndTime().toString());
                            }

                            blockResponse.put("numberId", timeBlockDTO.getNumberId());
                            blockResponse.put("action", "CREATED");
                            processedBlocks.add(blockResponse);

                            System.out.println("✅ BLOQUE CREADO: " + blockResponse);
                        } else {
                            System.out.println("❌ NO SE ENCONTRÓ DÍA CON ID: " + timeBlockDTO.getEmployeeScheduleDayId());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR PROCESANDO BLOQUE: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // ✅ RECÁLCULO AUTOMÁTICO PARA EMPLEADOS AFECTADOS
            if (!affectedEmployees.isEmpty()) {
                try {
                    System.out.println("🔄 INICIANDO RECÁLCULO MASIVO PARA " + affectedEmployees.size() + " EMPLEADOS");
                    for (Long employeeId : affectedEmployees) {
                        try {
                            autoRecalculateEmployeeGroups(employeeId);
                            System.out.println("✅ Recálculo completado para empleado: " + employeeId);
                        } catch (Exception e) {
                            System.err.println("⚠️ Error recalculando empleado " + employeeId + ": " + e.getMessage());
                        }
                    }
                    System.out.println("🔄 RECÁLCULO MASIVO COMPLETADO para " + affectedEmployees.size() + " empleados");
                } catch (Exception e) {
                    System.err.println("⚠️ ERROR EN RECÁLCULO MASIVO: " + e.getMessage());
                }
            }

            response.put("success", true);
            response.put("message", "Time blocks processed successfully");
            response.put("processedBlocks", processedBlocks);
            response.put("totalProcessed", processedBlocks.size());
            response.put("recalculatedEmployees", affectedEmployees.size());

            System.out.println("\n✅ PROCESAMIENTO COMPLETADO: " + processedBlocks.size() + " bloques");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ ERROR GENERAL: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error processing time blocks"));
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

            // 📊 OBTENER EMPLOYEE ID ANTES DE ELIMINAR
            Long employeeId = null;
            try {
                employeeId = day.getEmployeeSchedule().getEmployeeId();
                System.out.println("👤 Employee ID identificado antes de eliminar: " + employeeId);
            } catch (Exception e) {
                System.err.println("⚠️ Error obteniendo employeeId: " + e.getMessage());
            }

            // Eliminar todos los timeBlocks de este día
            employeeScheduleTimeBlockRepository.deleteByEmployeeScheduleDayId(dayId);

            // Eliminar el día mismo
            employeeScheduleDayRepository.deleteById(dayId);

            System.out.println("🗑️ DÍA ELIMINADO EXITOSAMENTE: " + dayId);

            // ✅ RECÁLCULO AUTOMÁTICO DESPUÉS DE ELIMINAR
            if (employeeId != null) {
                System.out.println("🔄 INICIANDO RECÁLCULO PARA EMPLEADO: " + employeeId);
                recalculateEmployeeGroupsSync(employeeId);
            } else {
                System.err.println("❌ NO SE PUDO RECALCULAR - employeeId es NULL");
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Día y sus horarios eliminados completamente");
            response.put("dayId", dayId);
            response.put("date", day.getDate());
            response.put("employeeId", employeeId);
            response.put("recalculated", employeeId != null);

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("❌ ERROR ELIMINANDO DÍA: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor: " + e.getMessage()));
        }
    }
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
        System.out.println("🔄 Convirtiendo TimeBlock a Map:");
        System.out.println("  - ID: " + block.getId());
        System.out.println("  - Horario: " + block.getStartTime() + " - " + block.getEndTime());
        System.out.println("  - Breaks: " + block.getBreakStartTime() + " - " + block.getBreakEndTime());

        Map<String, String> blockMap = new HashMap<>();
        blockMap.put("startTime", block.getStartTime().toString());
        blockMap.put("endTime", block.getEndTime().toString());

        // ✅ INCLUIR BREAKS EN EL MAP
        if (block.getBreakStartTime() != null) {
            blockMap.put("breakStartTime", block.getBreakStartTime().toString());
            System.out.println("☕ Break start incluido en map: " + block.getBreakStartTime());
        } else {
            System.out.println("❌ Break start es null, no incluido en map");
        }

        if (block.getBreakEndTime() != null) {
            blockMap.put("breakEndTime", block.getBreakEndTime().toString());
            System.out.println("☕ Break end incluido en map: " + block.getBreakEndTime());
        } else {
            System.out.println("❌ Break end es null, no incluido en map");
        }

        System.out.println("📦 Map final: " + blockMap);
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




    @PutMapping("/time-blocks/{id}")
    public ResponseEntity<Map<String, Object>> updateTimeBlock(
            @PathVariable Long id,
            @RequestBody TimeBlockDTO timeBlockDTO) {
        try {
            System.out.println("\n📄 === ENDPOINT PUT INDIVIDUAL TIMEBLOCK ===");
            System.out.println("📦 ID del bloque: " + id);
            System.out.println("📦 DTO recibido: " + timeBlockDTO);
            System.out.println("  - Start Time: " + timeBlockDTO.getStartTime());
            System.out.println("  - End Time: " + timeBlockDTO.getEndTime());
            System.out.println("  - Break Start Time: " + timeBlockDTO.getBreakStartTime());
            System.out.println("  - Break End Time: " + timeBlockDTO.getBreakEndTime());
            System.out.println("  - Number ID (DTO): " + timeBlockDTO.getNumberId());

            if (timeBlockDTO == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "TimeBlockDTO es requerido"));
            }

            timeBlockDTO.setId(id);

            EmployeeScheduleTimeBlock existingBlock = employeeScheduleTimeBlockRepository
                    .findById(id)
                    .orElse(null);

            if (existingBlock == null) {
                System.out.println("❌ TimeBlock no encontrado con ID: " + id);
                return ResponseEntity.notFound().build();
            }

            System.out.println("✅ TimeBlock encontrado: " + existingBlock.getId());

            // 📊 OBTENER EMPLOYEE ID - MÉTODO MEJORADO CON DOBLE ESTRATEGIA
            Long employeeId = null;

            // ESTRATEGIA 1: Desde el DTO (más confiable)
            if (timeBlockDTO.getNumberId() != null && !timeBlockDTO.getNumberId().isEmpty()) {
                try {
                    employeeId = Long.parseLong(timeBlockDTO.getNumberId());
                    System.out.println("✅ Employee ID obtenido desde DTO: " + employeeId);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ Error parseando numberId del DTO: " + e.getMessage());
                }
            }

            // ESTRATEGIA 2: Desde las relaciones (fallback)
            if (employeeId == null) {
                try {
                    System.out.println("🔍 Intentando obtener employeeId desde relaciones...");
                    if (existingBlock.getEmployeeScheduleDay() != null) {
                        System.out.println("  ✓ EmployeeScheduleDay existe");
                        if (existingBlock.getEmployeeScheduleDay().getEmployeeSchedule() != null) {
                            System.out.println("  ✓ EmployeeSchedule existe");
                            employeeId = existingBlock.getEmployeeScheduleDay()
                                    .getEmployeeSchedule()
                                    .getEmployeeId();
                            System.out.println("✅ Employee ID obtenido desde relaciones: " + employeeId);
                        } else {
                            System.err.println("  ✗ EmployeeSchedule es NULL");
                        }
                    } else {
                        System.err.println("  ✗ EmployeeScheduleDay es NULL");
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Error obteniendo employeeId desde relaciones: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            boolean isDelete = (timeBlockDTO.getStartTime() == null || timeBlockDTO.getStartTime().trim().isEmpty()) &&
                    (timeBlockDTO.getEndTime() == null || timeBlockDTO.getEndTime().trim().isEmpty());

            if (isDelete) {
                System.out.println("🗑️ ELIMINANDO TIMEBLOCK ID: " + id);
                employeeScheduleTimeBlockRepository.delete(existingBlock);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", true);
                response.put("message", "TimeBlock eliminado correctamente");
                response.put("action", "DELETED");
                response.put("id", id);

                // ✅ RECÁLCULO AUTOMÁTICO DESPUÉS DE ELIMINAR
                System.out.println("🔍 VERIFICANDO RECÁLCULO POST-ELIMINACIÓN:");
                System.out.println("  - employeeId: " + employeeId);
                System.out.println("  - employeeId es null?: " + (employeeId == null));

                if (employeeId != null) {
                    System.out.println("🚀 LLAMANDO A recalculateEmployeeGroupsSync...");
                    recalculateEmployeeGroupsSync(employeeId);
                } else {
                    System.err.println("❌ employeeId ES NULL, no se puede recalcular");
                }

                return ResponseEntity.ok(response);

            } else {
                System.out.println("💾 ACTUALIZANDO TIMEBLOCK ID: " + id);

                existingBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
                existingBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));

                if (timeBlockDTO.getBreakStartTime() != null && !timeBlockDTO.getBreakStartTime().trim().isEmpty()) {
                    existingBlock.setBreakStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getBreakStartTime())));
                    System.out.println("☕ Break start actualizado: " + timeBlockDTO.getBreakStartTime());
                } else {
                    existingBlock.setBreakStartTime(null);
                    System.out.println("🧹 Break start limpiado");
                }

                if (timeBlockDTO.getBreakEndTime() != null && !timeBlockDTO.getBreakEndTime().trim().isEmpty()) {
                    existingBlock.setBreakEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getBreakEndTime())));
                    System.out.println("☕ Break end actualizado: " + timeBlockDTO.getBreakEndTime());
                } else {
                    existingBlock.setBreakEndTime(null);
                    System.out.println("🧹 Break end limpiado");
                }

                existingBlock.setUpdatedAt(new Date());
                EmployeeScheduleTimeBlock updatedBlock = employeeScheduleTimeBlockRepository.save(existingBlock);

                // ✅ RECÁLCULO AUTOMÁTICO DESPUÉS DE ACTUALIZAR
                System.out.println("🔍 VERIFICANDO RECÁLCULO POST-ACTUALIZACIÓN:");
                System.out.println("  - employeeId: " + employeeId);
                System.out.println("  - employeeId es null?: " + (employeeId == null));

                if (employeeId != null) {
                    System.out.println("🚀 LLAMANDO A recalculateEmployeeGroupsSync...");
                    recalculateEmployeeGroupsSync(employeeId);
                } else {
                    System.err.println("❌ employeeId ES NULL, no se puede recalcular");
                }

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("success", true);
                response.put("message", "TimeBlock actualizado correctamente");
                response.put("action", "UPDATED");
                response.put("data", Map.of(
                        "id", updatedBlock.getId(),
                        "employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId(),
                        "startTime", updatedBlock.getStartTime().toString(),
                        "endTime", updatedBlock.getEndTime().toString(),
                        "breakStartTime", updatedBlock.getBreakStartTime() != null
                                ? updatedBlock.getBreakStartTime().toString() : null,
                        "breakEndTime", updatedBlock.getBreakEndTime() != null
                                ? updatedBlock.getBreakEndTime().toString() : null,
                        "updatedAt", updatedBlock.getUpdatedAt()
                ));

                System.out.println("✅ RESPUESTA GENERADA: " + response);
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR EN PUT INDIVIDUAL: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error actualizando TimeBlock: " + e.getMessage()));
        }
    }




}
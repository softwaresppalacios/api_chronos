package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;


import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment.ScheduleAssignmentGroupService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule-groups")
@RequiredArgsConstructor
public class ScheduleAssignmentGroupController {

    private final ScheduleAssignmentGroupService groupService;

    /**
     * 1. CREAR/AGRUPAR asignaciones
     * POST /api/schedule-groups/assign
     */
    @PostMapping("/assign")
    public ResponseEntity<?> assignSchedules(@RequestBody AssignmentRequest request) {
        try {
            // Validar request
            if (request.getEmployeeId() == null || request.getScheduleIds() == null || request.getScheduleIds().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Debe proporcionar employeeId y al menos un scheduleId")
                );
            }

            // Procesar asignación
            ScheduleAssignmentGroupDTO result = groupService.processScheduleAssignment(
                    request.getEmployeeId(),
                    request.getScheduleIds()
            );

            // Respuesta exitosa
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Asignación procesada correctamente");
            response.put("data", result);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 2. OBTENER grupos de un empleado
     * GET /api/schedule-groups/employee/{employeeId}
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeGroups(@PathVariable Long employeeId) {
        try {
            List<ScheduleAssignmentGroupDTO> groups = groupService.getEmployeeGroups(employeeId);

            Map<String, Object> response = new HashMap<>();
            response.put("employeeId", employeeId);
            response.put("totalGroups", groups.size());
            response.put("groups", groups);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 3. OBTENER un grupo específico
     * GET /api/schedule-groups/{groupId}
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<?> getGroupById(@PathVariable Long groupId) {
        try {
            ScheduleAssignmentGroupDTO group = groupService.getGroupById(groupId);

            if (group == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(group);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 4. ELIMINAR un grupo (desagrupar)
     * DELETE /api/schedule-groups/{groupId}
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(@PathVariable Long groupId) {
        try {
            groupService.deleteGroup(groupId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Grupo eliminado correctamente"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 5. RECALCULAR horas de un grupo
     * POST /api/schedule-groups/{groupId}/recalculate
     */
    @PostMapping("/{groupId}/recalculate")
    public ResponseEntity<?> recalculateGroup(@PathVariable Long groupId) {
        try {
            ScheduleAssignmentGroupDTO result = groupService.recalculateGroup(groupId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Horas recalculadas correctamente",
                    "data", result
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clase interna para el request
     */
    public static class AssignmentRequest {
        private Long employeeId;
        private List<Long> scheduleIds;

        // Getters y Setters
        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public List<Long> getScheduleIds() {
            return scheduleIds;
        }

        public void setScheduleIds(List<Long> scheduleIds) {
            this.scheduleIds = scheduleIds;
        }
    }


// MODIFICAR el endpoint en ScheduleAssignmentGroupController

    @GetMapping
    public ResponseEntity<?> getAllScheduleGroups(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String shiftName,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        try {
            // Limitar el tamaño máximo para evitar sobrecarga
            if (size > 100) size = 100;

            List<ScheduleAssignmentGroupDTO> allGroups = groupService.getAllScheduleGroupsWithFilters(
                    status, shiftName, employeeId, startDate, endDate);

            // Aplicar paginación manual
            int totalGroups = allGroups.size();
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalGroups);

            List<ScheduleAssignmentGroupDTO> pagedGroups = allGroups.subList(startIndex, endIndex);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalGroups", totalGroups);
            response.put("groups", pagedGroups);
            response.put("page", page);
            response.put("size", size);
            response.put("totalPages", (int) Math.ceil((double) totalGroups / size));
            response.put("hasNext", endIndex < totalGroups);
            response.put("hasPrevious", page > 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/available-statuses")
    public ResponseEntity<List<Map<String, String>>> getAvailableStatuses() {
        try {
            List<Map<String, String>> statuses = groupService.getAvailableStatuses();
            return ResponseEntity.ok(statuses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }
}
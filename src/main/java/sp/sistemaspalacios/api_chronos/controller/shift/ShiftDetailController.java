package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.ShiftDetailDTO;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.service.shift.ShiftDetailService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shift-details")
public class ShiftDetailController {

    private final ShiftDetailService shiftDetailService;

    public ShiftDetailController(ShiftDetailService shiftDetailService) {
        this.shiftDetailService = shiftDetailService;
    }

    @GetMapping
    public ResponseEntity<List<ShiftDetailDTO>> getAllShiftDetails() {
        List<ShiftDetail> shiftDetails = shiftDetailService.getAllShiftDetails();
        List<ShiftDetailDTO> dtos = shiftDetails.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftDetailDTO> getShiftDetailById(@PathVariable Long id) {
        ShiftDetail shiftDetail = shiftDetailService.getShiftDetailById(id);
        ShiftDetailDTO dto = convertToDTO(shiftDetail);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<List<ShiftDetailDTO>> getShiftDetailsByShiftId(@PathVariable Long shiftId) {
        List<ShiftDetail> shiftDetails = shiftDetailService.getShiftDetailsByShiftId(shiftId);
        List<ShiftDetailDTO> dtos = shiftDetails.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createShiftDetail(@RequestBody ShiftDetail shiftDetail) {
        try {
            // Validación inicial mínima solo para evitar NPE
            performBasicNullChecks(shiftDetail);

            // El servicio hará TODA la validación real
            ShiftDetail created = shiftDetailService.createShiftDetail(shiftDetail);
            ShiftDetailDTO responseDTO = convertToDTO(created);
            Map<String, Object> hoursSummary = shiftDetailService.getWeeklyHoursSummary(created.getShift().getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detalle de turno creado exitosamente");
            response.put("shiftDetail", responseDTO);
            response.put("hoursSummary", hoursSummary);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createValidationErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(createConfigurationErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createInternalErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateShiftDetail(@PathVariable Long id, @RequestBody ShiftDetail shiftDetail) {
        try {
            // Validación inicial mínima solo para evitar NPE
            performBasicNullChecks(shiftDetail);

            // El servicio hará TODA la validación real
            ShiftDetail updated = shiftDetailService.updateShiftDetail(id, shiftDetail);
            ShiftDetailDTO responseDTO = convertToDTO(updated);
            Map<String, Object> hoursSummary = shiftDetailService.getWeeklyHoursSummary(updated.getShift().getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detalle de turno actualizado exitosamente");
            response.put("shiftDetail", responseDTO);
            response.put("hoursSummary", hoursSummary);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createValidationErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(createConfigurationErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createInternalErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteShiftDetail(@PathVariable Long id) {
        try {
            shiftDetailService.deleteShiftDetail(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Detalle de turno eliminado exitosamente");
            response.put("success", "true");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(createSimpleErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/break-info")
    public ResponseEntity<Map<String, Object>> getBreakInfo() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Para obtener la configuración de break, use /api/config/BREAK");
            response.put("breakConfigEndpoint", "/api/config/BREAK");
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createInternalErrorResponse("Error obteniendo información de break: " + e.getMessage()));
        }
    }

    // ==========================================
    // MÉTODOS PRIVADOS - VALIDACIÓN BÁSICA
    // ==========================================

    /**
     * Validaciones mínimas para evitar NullPointerException
     * El backend nunca debe crash por datos null del frontend
     */
    private void performBasicNullChecks(ShiftDetail shiftDetail) {
        if (shiftDetail == null) {
            throw new IllegalArgumentException("Los datos del turno son obligatorios");
        }

        if (shiftDetail.getShift() == null) {
            throw new IllegalArgumentException("La referencia al turno es obligatoria");
        }

        if (shiftDetail.getShift().getId() == null) {
            throw new IllegalArgumentException("El ID del turno es obligatorio");
        }

        // No validar más aquí - eso lo hace el service con el validator completo
    }

    // ==========================================
    // MÉTODOS PRIVADOS - CONVERSIÓN DTO
    // ==========================================

    private ShiftDetailDTO convertToDTO(ShiftDetail entity) {
        return new ShiftDetailDTO(
                entity.getId(),
                entity.getShift() != null ? entity.getShift().getId() : null,
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getBreakStartTime(),
                entity.getBreakEndTime(),
                entity.getBreakMinutes(),
                entity.getWeeklyHours(),
                entity.getNightHoursStart(),
                entity.getHoursPerDay()
        );
    }

    // ==========================================
    // MÉTODOS PRIVADOS - RESPUESTAS DE ERROR ESPECÍFICAS
    // ==========================================

    /**
     * Error de validación de datos (cliente envió datos incorrectos)
     */
    private Map<String, Object> createValidationErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorType", "VALIDATION_ERROR");
        response.put("error", message);
        response.put("message", "Los datos enviados no cumplen las reglas de negocio");
        return response;
    }

    /**
     * Error de configuración del sistema (problema del servidor)
     */
    private Map<String, Object> createConfigurationErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorType", "CONFIGURATION_ERROR");
        response.put("error", message);
        response.put("message", "Error en la configuración del sistema");
        return response;
    }

    /**
     * Error interno del servidor
     */
    private Map<String, Object> createInternalErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorType", "INTERNAL_ERROR");
        response.put("error", message);
        response.put("message", "Error interno del servidor");
        return response;
    }

    /**
     * Error simple para métodos que retornan Map<String, String>
     */
    private Map<String, String> createSimpleErrorResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("success", "false");
        response.put("error", message);
        return response;
    }
}
package sp.sistemaspalacios.api_chronos.controller.boundaries.breakConfiguration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.BreakConfigurationDTO;
import sp.sistemaspalacios.api_chronos.entity.boundaries.breakConfiguration.BreakConfiguration;
import sp.sistemaspalacios.api_chronos.service.boundaries.breakConfiguration.BreakConfigurationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/break-config")
public class BreakConfigurationController {

    private final BreakConfigurationService breakConfigurationService;

    public BreakConfigurationController(BreakConfigurationService breakConfigurationService) {
        this.breakConfigurationService = breakConfigurationService;
    }
    /**
     * 🔹 Obtener la configuración actual de break
     */
    @GetMapping
    public ResponseEntity<?> getCurrentBreakConfiguration() {
        try {
            BreakConfiguration config = breakConfigurationService.getCurrentBreakConfiguration();
            BreakConfigurationDTO dto = convertToDTO(config);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return handleError(e.getMessage());
        }
    }

    /**
     * 🔹 Crear o actualizar configuración de break usando DTO
     */
    @PostMapping
    public ResponseEntity<?> saveBreakConfiguration(@RequestBody BreakConfigurationDTO dto) {
        try {
            // 🔧 LOGS PARA DEBUG - ELIMINAR DESPUÉS
            System.out.println("=== DEBUG ===");
            System.out.println("DTO recibido: " + dto);
            System.out.println("Minutes: " + dto.getMinutes());
            System.out.println("============");

            if (dto.getMinutes() == null) {
                return handleError("El campo 'minutes' es obligatorio");
            }

            BreakConfiguration config = breakConfigurationService.saveOrUpdateBreakConfiguration(dto.getMinutes());
            BreakConfigurationDTO responseDTO = convertToDTO(config);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Configuración de descanso guardada exitosamente");
            response.put("config", responseDTO);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        }
    }

    /**
     * 🔹 Actualizar configuración existente usando DTO
     */
    @PutMapping
    public ResponseEntity<?> updateBreakConfiguration(@RequestBody BreakConfigurationDTO dto) {
        try {
            if (dto.getMinutes() == null) {
                return handleError("El campo 'minutes' es obligatorio");
            }

            BreakConfiguration config = breakConfigurationService.saveOrUpdateBreakConfiguration(dto.getMinutes());
            BreakConfigurationDTO responseDTO = convertToDTO(config);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Configuración de descanso actualizada exitosamente");
            response.put("config", responseDTO);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        }
    }

    /**
     * 🔹 Verificar si existe configuración
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkBreakConfigurationExists() {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", breakConfigurationService.hasBreakConfiguration());
        return ResponseEntity.ok(response);
    }

    /**
     * 🔹 Obtener solo los minutos configurados (endpoint helper)
     */
    @GetMapping("/minutes")
    public ResponseEntity<?> getCurrentBreakMinutes() {
        try {
            Integer minutes = breakConfigurationService.getCurrentBreakMinutes();
            Map<String, Integer> response = new HashMap<>();
            response.put("minutes", minutes);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleError(e.getMessage());
        }
    }

    /**
     * 🔹 Obtener todas las configuraciones (para admin/debug) usando DTOs
     */
    @GetMapping("/all")
    public ResponseEntity<List<BreakConfigurationDTO>> getAllBreakConfigurations() {
        List<BreakConfiguration> configs = breakConfigurationService.getAllBreakConfigurations();
        List<BreakConfigurationDTO> dtos = configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * 🔹 Eliminar configuración por ID (uso administrativo)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBreakConfiguration(@PathVariable Long id) {
        try {
            breakConfigurationService.deleteBreakConfiguration(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Configuración de break eliminada exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleError(e.getMessage());
        }
    }

    /**
     * 🔹 Convertir Entity a DTO
     */
    private BreakConfigurationDTO convertToDTO(BreakConfiguration entity) {
        return new BreakConfigurationDTO(
                entity.getId(),
                entity.getMinutes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 🔹 Manejo de errores
     */
    private ResponseEntity<Map<String, String>> handleError(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return ResponseEntity.badRequest().body(response);
    }
}
package sp.sistemaspalacios.api_chronos.controller.attendanceAlert;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AttendanceAlertConfiguration;
import sp.sistemaspalacios.api_chronos.service.attendanceAlert.AttendanceAlertConfigurationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance-alerts/config")
@RequiredArgsConstructor
public class AttendanceAlertConfigurationController {

    private final AttendanceAlertConfigurationService configService;

    @GetMapping
    public ResponseEntity<List<AttendanceAlertConfiguration>> getAllConfigurations() {
        List<AttendanceAlertConfiguration> configs = configService.getAllActiveConfigurations();
        return ResponseEntity.ok(configs);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveConfiguration(
            @RequestBody AttendanceAlertConfiguration config
    ) {
        Map<String, Object> response = new HashMap<>();

        try {
            AttendanceAlertConfiguration saved = configService.saveConfiguration(config);

            response.put("success", true);
            response.put("message", "Configuración guardada exitosamente");
            response.put("configuration", saved);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleActive(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        Map<String, Object> response = new HashMap<>();

        try {
            Boolean isActive = body.get("isActive");
            AttendanceAlertConfiguration updated = configService.toggleActive(id, isActive);

            response.put("success", true);
            response.put("message", isActive ? "Configuración activada" : "Configuración desactivada");
            response.put("configuration", updated);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConfiguration(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            configService.deleteConfiguration(id);

            response.put("success", true);
            response.put("message", "Configuración eliminada");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializeDefaults() {
        Map<String, Object> response = new HashMap<>();

        try {
            configService.initializeDefaultConfigurations();

            response.put("success", true);
            response.put("message", "Configuraciones inicializadas exitosamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
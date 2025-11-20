package sp.sistemaspalacios.api_chronos.controller.boundaries.generalConfiguration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.configuration.GeneralConfigurationDTO;
import sp.sistemaspalacios.api_chronos.entity.boundaries.generalConfiguration.GeneralConfiguration;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class GeneralConfigurationController {

    private final GeneralConfigurationService service;

    /**
     * 🔸 Guarda un valor como texto (PostgreSQL lo convierte a INTERVAL)
     * Ejemplos válidos: "30 minutes", "44:00", "19:00", "8:30"
     */
    @PostMapping("/{type}")
    public ResponseEntity<?> setConfig(@PathVariable String type, @RequestBody GeneralConfigurationDTO dto) {
        try {
            GeneralConfiguration config = service.saveOrUpdate(type, dto.getValue());
            return ResponseEntity.ok(Map.of(
                    "message", "Guardado exitosamente",
                    "type", config.getType(),
                    "value", config.getValue()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * 🔸 Consulta la configuración por tipo
     */
    @GetMapping("/{type}")
    public ResponseEntity<?> getConfig(@PathVariable String type) {
        GeneralConfiguration config = service.getByType(type);
        return ResponseEntity.ok(Map.of(
                "type", config.getType(),
                "value", config.getValue()
        ));
    }
}

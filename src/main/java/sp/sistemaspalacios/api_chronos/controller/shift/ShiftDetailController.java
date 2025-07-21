package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.ShiftDetailDTO;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
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

    /**
     * 🔹 Crear shift detail (retorna el resumen de horas programadas/faltantes)
     */
    @PostMapping
    public ResponseEntity<?> createShiftDetail(@RequestBody ShiftDetail shiftDetail) {
        try {
            ShiftDetail processedEntity = processShiftDetailEntity(shiftDetail);
            ShiftDetail created = shiftDetailService.createShiftDetail(processedEntity);
            ShiftDetailDTO responseDTO = convertToDTO(created);

            // Resumen de horas
            Map<String, Object> hoursSummary = shiftDetailService.getWeeklyHoursSummary(created.getShift().getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detalle de turno creado exitosamente");
            response.put("shiftDetail", responseDTO);
            response.put("hoursSummary", hoursSummary);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        } catch (Exception e) {
            return handleError("Error interno: " + e.getMessage());
        }
    }

    /**
     * 🔹 Actualizar shift detail (también retorna el resumen)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShiftDetail(@PathVariable Long id, @RequestBody ShiftDetail shiftDetail) {
        try {
            ShiftDetail processedEntity = processShiftDetailEntity(shiftDetail);
            ShiftDetail updated = shiftDetailService.updateShiftDetail(id, processedEntity);
            ShiftDetailDTO responseDTO = convertToDTO(updated);

            // Resumen de horas
            Map<String, Object> hoursSummary = shiftDetailService.getWeeklyHoursSummary(updated.getShift().getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detalle de turno actualizado exitosamente");
            response.put("shiftDetail", responseDTO);
            response.put("hoursSummary", hoursSummary);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        } catch (Exception e) {
            return handleError("Error interno: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShiftDetail(@PathVariable Long id) {
        try {
            shiftDetailService.deleteShiftDetail(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Detalle de turno eliminado exitosamente");

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return handleError(e.getMessage());
        }
    }

    // Convertir Entity a DTO
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

    // Convertir DTO/JSON a Entity
    private ShiftDetail convertToEntity(ShiftDetailDTO dto) {
        ShiftDetail entity = new ShiftDetail();
        entity.setId(dto.getId());
        entity.setDayOfWeek(dto.getDayOfWeek());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setBreakStartTime(dto.getBreakStartTime());
        entity.setBreakEndTime(dto.getBreakEndTime());

        if (dto.getShiftId() != null) {
            Shifts shift = new Shifts();
            shift.setId(dto.getShiftId());
            entity.setShift(shift);
        }
        return entity;
    }

    private ShiftDetail processShiftDetailEntity(ShiftDetail shiftDetail) {
        if (shiftDetail.getShift() != null && shiftDetail.getShift().getId() != null) {
            return shiftDetail;
        }
        throw new IllegalArgumentException("El turno (Shift) es obligatorio y debe tener un ID válido.");
    }

    private ResponseEntity<Map<String, String>> handleError(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/break-info")
    public ResponseEntity<?> getBreakInfo() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Para obtener la configuración de break, use /api/break-config");
            response.put("breakConfigEndpoint", "/api/break-config/minutes");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleError("Error obteniendo información de break: " + e.getMessage());
        }
    }
}

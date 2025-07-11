package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ShiftDetailService shiftDetailService;

    /**
     * 🔹 Obtener todos los shift details con DTOs
     */
    @GetMapping
    public ResponseEntity<List<ShiftDetailDTO>> getAllShiftDetails() {
        List<ShiftDetail> shiftDetails = shiftDetailService.getAllShiftDetails();
        List<ShiftDetailDTO> dtos = shiftDetails.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * 🔹 Obtener shift detail por ID con DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShiftDetailDTO> getShiftDetailById(@PathVariable Long id) {
        ShiftDetail shiftDetail = shiftDetailService.getShiftDetailById(id);
        ShiftDetailDTO dto = convertToDTO(shiftDetail);
        return ResponseEntity.ok(dto);
    }

    /**
     * 🔹 Obtener shift details por shift ID con DTOs
     */
    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<List<ShiftDetailDTO>> getShiftDetailsByShiftId(@PathVariable Long shiftId) {
        List<ShiftDetail> shiftDetails = shiftDetailService.getShiftDetailsByShiftId(shiftId);
        List<ShiftDetailDTO> dtos = shiftDetails.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * 🔹 Crear shift detail usando JSON directo (CON BREAK)
     */
    @PostMapping
    public ResponseEntity<?> createShiftDetail(@RequestBody ShiftDetail shiftDetail) {
        try {
            // 🔧 LOGS PARA DEBUG - ELIMINAR DESPUÉS
            System.out.println("=== DEBUG CREATE SHIFT DETAIL ===");
            System.out.println("ShiftDetail recibido: " + shiftDetail);
            System.out.println("Shift ID: " + (shiftDetail.getShift() != null ? shiftDetail.getShift().getId() : "NULL"));
            System.out.println("Break start: " + shiftDetail.getBreakStartTime());
            System.out.println("Break end: " + shiftDetail.getBreakEndTime());
            System.out.println("=================================");

            // Validar que el shift esté presente
            ShiftDetail processedEntity = processShiftDetailEntity(shiftDetail);

            // Crear el shift detail
            ShiftDetail created = shiftDetailService.createShiftDetail(processedEntity);

            // Convertir respuesta a DTO
            ShiftDetailDTO responseDTO = convertToDTO(created);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detalle de turno creado exitosamente");
            response.put("shiftDetail", responseDTO);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        } catch (Exception e) {
            return handleError("Error interno: " + e.getMessage());
        }
    }

    /**
     * 🔹 Actualizar shift detail usando JSON directo (CON BREAK)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShiftDetail(@PathVariable Long id, @RequestBody ShiftDetail shiftDetail) {
        try {
            // 🔧 LOGS PARA DEBUG - ELIMINAR DESPUÉS
            System.out.println("=== DEBUG UPDATE SHIFT DETAIL ===");
            System.out.println("ID: " + id);
            System.out.println("ShiftDetail recibido: " + shiftDetail);
            System.out.println("Shift ID: " + (shiftDetail.getShift() != null ? shiftDetail.getShift().getId() : "NULL"));
            System.out.println("Break start: " + shiftDetail.getBreakStartTime());
            System.out.println("Break end: " + shiftDetail.getBreakEndTime());
            System.out.println("=================================");

            // Validar que el shift esté presente
            ShiftDetail processedEntity = processShiftDetailEntity(shiftDetail);

            // Actualizar el shift detail
            ShiftDetail updated = shiftDetailService.updateShiftDetail(id, processedEntity);

            // Convertir respuesta a DTO
            ShiftDetailDTO responseDTO = convertToDTO(updated);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detalle de turno actualizado exitosamente");
            response.put("shiftDetail", responseDTO);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        } catch (Exception e) {
            return handleError("Error interno: " + e.getMessage());
        }
    }

    /**
     * 🔹 Eliminar shift detail
     */
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

    /**
     * 🔹 Convertir Entity a DTO
     */
    private ShiftDetailDTO convertToDTO(ShiftDetail entity) {
        return new ShiftDetailDTO(
                entity.getId(),
                entity.getShift() != null ? entity.getShift().getId() : null,
                entity.getDayOfWeek(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getBreakStartTime(),  // 🔹 NUEVO
                entity.getBreakEndTime()     // 🔹 NUEVO
        );
    }

    /**
     * 🔹 Convertir DTO/JSON a Entity
     */
    private ShiftDetail convertToEntity(ShiftDetailDTO dto) {
        ShiftDetail entity = new ShiftDetail();

        // Campos básicos
        entity.setId(dto.getId());
        entity.setDayOfWeek(dto.getDayOfWeek());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());

        // 🔹 NUEVOS CAMPOS DE BREAK
        entity.setBreakStartTime(dto.getBreakStartTime());
        entity.setBreakEndTime(dto.getBreakEndTime());

        // Shift (solo ID para evitar problemas de referencia)
        if (dto.getShiftId() != null) {
            Shifts shift = new Shifts();
            shift.setId(dto.getShiftId());
            entity.setShift(shift);
        }

        return entity;
    }

    /**
     * 🔹 Convertir JSON directo a Entity (para requests que vienen como ShiftDetail)
     */
    private ShiftDetail processShiftDetailEntity(ShiftDetail shiftDetail) {
        // Si el shift viene como objeto con ID, asegurarse que esté correctamente referenciado
        if (shiftDetail.getShift() != null && shiftDetail.getShift().getId() != null) {
            // El shift ya viene correctamente, no hay que hacer nada
            return shiftDetail;
        }

        throw new IllegalArgumentException("El turno (Shift) es obligatorio y debe tener un ID válido.");
    }

    /**
     * 🔹 Manejo de errores
     */
    private ResponseEntity<Map<String, String>> handleError(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 🔹 Endpoint para validar configuración de break (helper)
     */
    @GetMapping("/break-info")
    public ResponseEntity<?> getBreakInfo() {
        try {
            // Este endpoint podría mostrar la configuración actual del break
            // para que el frontend sepa cuántos minutos debe usar
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Para obtener la configuración de break, use /api/break-config");
            response.put("breakConfigEndpoint", "/api/break-config/minutes");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleError("Error obteniendo información de break: " + e.getMessage());
        }
    }
}
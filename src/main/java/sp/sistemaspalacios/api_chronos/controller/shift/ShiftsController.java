package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.ShiftsDTO;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.service.shift.ShiftsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController

@RequestMapping("/api/shifts")
public class ShiftsController {

    private final ShiftsService shiftsService;

    public ShiftsController(ShiftsService shiftsService) {
        this.shiftsService = shiftsService;
    }
    // Obtener todos los turnos
    @GetMapping
    public ResponseEntity<List<Shifts>> getAllShifts() {
        return ResponseEntity.ok(shiftsService.findAll());
    }

    // Obtener un turno por ID
    @GetMapping("/{id}")
    public ResponseEntity<Shifts> getShiftById(@PathVariable Long id) {
        return ResponseEntity.ok(shiftsService.findById(id));
    }

    @GetMapping("/dependency/{dependencyId}")
    public ResponseEntity<?> getShiftsByDependencyId(@PathVariable Long dependencyId) {
        if (dependencyId == null || dependencyId <= 0) {
            return ResponseEntity.badRequest().body("El ID de dependencia debe ser un número válido.");
        }

        List<Shifts> shifts = shiftsService.findByDependencyId(dependencyId);
        if (shifts.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "No hay turnos registrados para la dependencia con ID: " + dependencyId);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(shifts);
    }









    // Crear un nuevo turno
    @PostMapping
    public ResponseEntity<?> createShift(@RequestBody Shifts shifts) {
        try {
            Shifts createdShift = shiftsService.save(shifts);
            return ResponseEntity.ok(createdShift);
        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        }
    }

    // Actualizar un turno existente
    @PutMapping("/{id}")
    public ResponseEntity<?> updateShift(@PathVariable Long id, @RequestBody Shifts shifts) {
        try {
            return ResponseEntity.ok(shiftsService.updateShift(id, shifts));
        } catch (IllegalArgumentException e) {
            return handleError(e.getMessage());
        }
    }

    // Eliminar un turno por ID
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShift(@PathVariable Long id) {
        shiftsService.deleteById(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Turno eliminado exitosamente");
        return ResponseEntity.ok(response);
    }

    // Manejo de errores
    private ResponseEntity<Map<String, String>> handleError(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<Shifts> getShiftWithDetails(@PathVariable Long id) {
        Shifts shift = shiftsService.findById(id);
        return ResponseEntity.ok(shift);
    }


    @GetMapping("/shift/{dependencyId}")
    public ResponseEntity<List<Shifts>> findByDependencyaId(@PathVariable Long dependencyId) {
        // Verificar si el ID de la dependencia es válido
        if (dependencyId == null || dependencyId <= 0) {
            return ResponseEntity.badRequest().body(null); // Bad request si el ID de dependencia no es válido
        }

        // Buscar todos los turnos asociados a la dependencia
        List<Shifts> shifts = shiftsService.findByDependencyId(dependencyId);

        // Si no se encuentran turnos, devolver un 404
        if (shifts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Si se encuentran turnos, devolverlos en la respuesta
        return ResponseEntity.ok(shifts);
    }


    @GetMapping("/check-outdated")
    public ResponseEntity<?> checkOutdatedShifts() {
        try {
            Map<String, Object> result = shiftsService.checkOutdatedShifts();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error verificando turnos: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }


}

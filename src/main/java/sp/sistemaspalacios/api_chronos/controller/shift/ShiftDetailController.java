package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.service.shift.ShiftDetailService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController

@RequestMapping("/api/shift-details")
public class ShiftDetailController {

    @Autowired
    private ShiftDetailService shiftDetailService;

    @GetMapping
    public ResponseEntity<List<ShiftDetail>> getAllShiftDetails() {
        return ResponseEntity.ok(shiftDetailService.getAllShiftDetails());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftDetail> getShiftDetailById(@PathVariable Long id) {
        return ResponseEntity.ok(shiftDetailService.getShiftDetailById(id));
    }

    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<List<ShiftDetail>> getShiftDetailsByShiftId(@PathVariable Long shiftId) {
        return ResponseEntity.ok(shiftDetailService.getShiftDetailsByShiftId(shiftId));
    }

    @PostMapping
    public ResponseEntity<?> createShiftDetail(@RequestBody ShiftDetail shiftDetail) {
        try {
            ShiftDetail created = shiftDetailService.createShiftDetail(shiftDetail);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);  // Devuelve el mensaje de error
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateShiftDetail(@PathVariable Long id, @RequestBody ShiftDetail shiftDetail) {
        try {
            ShiftDetail updated = shiftDetailService.updateShiftDetail(id, shiftDetail);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShiftDetail(@PathVariable Long id) {
        try {
            shiftDetailService.deleteShiftDetail(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(404).body(error);
        }
    }
}

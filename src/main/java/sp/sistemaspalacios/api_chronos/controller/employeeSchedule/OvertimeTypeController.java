package sp.sistemaspalacios.api_chronos.controller.employeeSchedule;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sp.sistemaspalacios.api_chronos.dto.overtime.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.overtime.OvertimeTypeService;

import java.util.List;

@RestController
@RequestMapping("/overtime-types")
@RequiredArgsConstructor
public class OvertimeTypeController {

    private final OvertimeTypeService service;

    /**
     * Obtener todos los tipos activos
     */
    @GetMapping
    public ResponseEntity<List<OvertimeTypeDTO>> getAllTypes() {
        return ResponseEntity.ok(service.getAllActiveTypes());
    }

    /**
     * Obtener por código
     */
    @GetMapping("/{code}")
    public ResponseEntity<OvertimeTypeDTO> getByCode(@PathVariable String code) {
        OvertimeTypeDTO type = service.getByCode(code);
        if (type == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(type);
    }
}
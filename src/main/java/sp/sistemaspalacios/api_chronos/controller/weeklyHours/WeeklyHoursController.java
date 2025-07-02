package sp.sistemaspalacios.api_chronos.controller.weeklyHours;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.WeeklyHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.service.weeklyHours.WeeklyHoursService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/weekly-hours")
public class WeeklyHoursController {

    @Autowired
    private WeeklyHoursService weeklyHoursService;

    // Obtener todas las horas semanales
    @GetMapping
    public List<WeeklyHoursDTO> getAllWeeklyHours() {
        return weeklyHoursService.getAllWeeklyHours()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Obtener las horas semanales por ID
    @GetMapping("/{id}")
    public ResponseEntity<WeeklyHours> getWeeklyHoursById(@PathVariable("id") Long id) {
        Optional<WeeklyHours> weeklyHoursDTO = weeklyHoursService.getWeeklyHoursById(id);
        return weeklyHoursDTO.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // Crear un nuevo registro de horas semanales
    @PostMapping
    public ResponseEntity<WeeklyHoursDTO> createWeeklyHours(@RequestBody WeeklyHoursDTO weeklyHoursDTO) {
        WeeklyHoursDTO createdWeeklyHoursDTO = weeklyHoursService.createWeeklyHours(weeklyHoursDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdWeeklyHoursDTO);
    }

    // Actualizar un registro de horas semanales
    @PutMapping("/{id}")
    public ResponseEntity<WeeklyHoursDTO> updateWeeklyHours(@PathVariable("id") Long id, @RequestBody WeeklyHoursDTO weeklyHoursDTO) {
        WeeklyHoursDTO updatedWeeklyHoursDTO = weeklyHoursService.updateWeeklyHours(id, weeklyHoursDTO);
        return ResponseEntity.ok(updatedWeeklyHoursDTO);
    }

    // Eliminar un registro de horas semanales
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWeeklyHours(@PathVariable("id") Long id) {
        weeklyHoursService.deleteWeeklyHours(id);
        return ResponseEntity.noContent().build();
    }

    // Convertir de WeeklyHours a WeeklyHoursDTO
    private WeeklyHoursDTO convertToDTO(WeeklyHours weeklyHours) {
        WeeklyHoursDTO dto = new WeeklyHoursDTO();
        dto.setId(weeklyHours.getId());
        dto.setHours(String.valueOf(Duration.parse(weeklyHours.getHours().toString())));  // Convertir Duration a String
        dto.setCreatedAt(weeklyHours.getCreatedAt());
        dto.setUpdatedAt(weeklyHours.getUpdatedAt());
        return dto;
    }
}

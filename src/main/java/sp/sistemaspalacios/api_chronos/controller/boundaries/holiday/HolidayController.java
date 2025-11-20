package sp.sistemaspalacios.api_chronos.controller.boundaries.holiday;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.entity.holiday.Holiday;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }
    // Obtener todos los festivos
    @GetMapping
    public List<Holiday> getAllHolidays() {
        return holidayService.getAllHolidays();
    }

    // Obtener un festivo por su ID
    @GetMapping("/{id}")
    public ResponseEntity<Holiday> getHolidayById(@PathVariable("id") Long id) {
        Optional<Holiday> holiday = holidayService.getHolidayById(id);
        return holiday.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // Crear un nuevo festivo
    @PostMapping
    public ResponseEntity<Holiday> createHoliday(@RequestBody Holiday holiday) {
        Holiday createdHoliday = holidayService.createHoliday(holiday);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdHoliday);
    }

    // Actualizar un festivo
    @PutMapping("/{id}")
    public ResponseEntity<Holiday> updateHoliday(@PathVariable("id") Long id, @RequestBody Holiday holiday) {
        Holiday updatedHoliday = holidayService.updateHoliday(id, holiday);
        return ResponseEntity.ok(updatedHoliday);
    }

    // Eliminar un festivo
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable("id") Long id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
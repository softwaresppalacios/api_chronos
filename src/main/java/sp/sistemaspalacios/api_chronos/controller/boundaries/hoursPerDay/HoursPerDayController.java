package sp.sistemaspalacios.api_chronos.controller.boundaries.hoursPerDay;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.HoursPerDayDTO;
import sp.sistemaspalacios.api_chronos.service.boundaries.hoursPerDay.HoursPerDayService;

@RestController
@RequestMapping("/api/hours-per-day")
public class HoursPerDayController {
    private final HoursPerDayService service;

    public HoursPerDayController(HoursPerDayService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<HoursPerDayDTO> getCurrent() {
        return ResponseEntity.ok(service.getCurrentHoursPerDay());
    }

    @PostMapping
    public ResponseEntity<HoursPerDayDTO> setHours(@RequestBody HoursPerDayDTO dto) {
        return ResponseEntity.ok(service.setHoursPerDay(dto.getHoursPerDay()));
    }

}
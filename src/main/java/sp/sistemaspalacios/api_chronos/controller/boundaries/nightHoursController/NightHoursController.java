package sp.sistemaspalacios.api_chronos.controller.boundaries.nightHoursController;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.NightHoursDTO;
import sp.sistemaspalacios.api_chronos.service.boundaries.nightHours.NightHoursService;

@RestController
@RequestMapping("/api/night-hours")
public class NightHoursController {
    private final NightHoursService nightHoursService;

    public NightHoursController(NightHoursService nightHoursService) {
        this.nightHoursService = nightHoursService;
    }

    @GetMapping
    public ResponseEntity<NightHoursDTO> getCurrent() {
        return ResponseEntity.ok(nightHoursService.getCurrentNightHours());
    }

    @PostMapping
    public ResponseEntity<NightHoursDTO> setNightHours(@RequestBody NightHoursDTO dto) {
        return ResponseEntity.ok(nightHoursService.setNightHours(dto));
    }
}
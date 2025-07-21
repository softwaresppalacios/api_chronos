package sp.sistemaspalacios.api_chronos.service.boundaries.nightHours;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.NightHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.boundaries.nightHours.NightHours;
import sp.sistemaspalacios.api_chronos.repository.NightHours.NightHoursRepository;

import java.time.LocalDateTime;

@Service
public class NightHoursService {

    private final NightHoursRepository nightHoursRepository;

    public NightHoursService(NightHoursRepository nightHoursRepository) {
        this.nightHoursRepository = nightHoursRepository;
    }

    public NightHoursDTO getCurrentNightHours() {
        NightHours entity = nightHoursRepository.findTopByOrderByCreatedAtDesc();
        if (entity == null)
            throw new IllegalStateException("No existe configuración de horas nocturnas");
        return toDTO(entity);
    }

    public NightHoursDTO setNightHours(NightHoursDTO dto) {
        // Opcional: Elimina la anterior configuración, si solo permites una
        nightHoursRepository.deleteAll();

        NightHours entity = NightHours.builder()
                .startNight(dto.getStartNight())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        NightHours saved = nightHoursRepository.save(entity);
        return toDTO(saved);
    }

    private NightHoursDTO toDTO(NightHours entity) {
        return NightHoursDTO.builder()
                .id(entity.getId())
                .startNight(entity.getStartNight())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
package sp.sistemaspalacios.api_chronos.service.boundaries.hoursPerDay;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.HoursPerDayDTO;
import sp.sistemaspalacios.api_chronos.entity.boundaries.hoursPerDay.HoursPerDay;
import sp.sistemaspalacios.api_chronos.repository.boundaries.HoursPerDay.HoursPerDayRepository;

import java.time.LocalDateTime;

@Service
public class HoursPerDayService {
    private final HoursPerDayRepository hoursPerDayRepository;

    public HoursPerDayService(HoursPerDayRepository hoursPerDayRepository) {
        this.hoursPerDayRepository = hoursPerDayRepository;
    }

    public HoursPerDayDTO getCurrentHoursPerDay() {
        HoursPerDay entity = hoursPerDayRepository.findLatest()
                .orElseThrow(() -> new IllegalStateException("No existe configuración de horas por día"));
        return toDTO(entity);
    }

    @Transactional
    public HoursPerDayDTO setHoursPerDay(Integer hours) {  // <-- Cambia a Integer
        if (hours == null || hours <= 0 || hours > 24) {
            throw new IllegalArgumentException("Las horas por día deben estar entre 1 y 24");
        }
        // Elimina todas las anteriores (opcional)
        hoursPerDayRepository.deleteAll();

        HoursPerDay entity = HoursPerDay.builder()
                .hoursPerDay(hours)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        HoursPerDay saved = hoursPerDayRepository.save(entity);
        return toDTO(saved);
    }

    private HoursPerDayDTO toDTO(HoursPerDay entity) {
        return HoursPerDayDTO.builder()
                .id(entity.getId())
                .hoursPerDay(entity.getHoursPerDay())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

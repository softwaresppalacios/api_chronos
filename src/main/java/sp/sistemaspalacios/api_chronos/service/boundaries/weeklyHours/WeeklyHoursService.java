package sp.sistemaspalacios.api_chronos.service.boundaries.weeklyHours;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.WeeklyHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.boundaries.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.repository.boundaries.weeklyHours.WeeklyHoursRepository;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WeeklyHoursService {

    private final WeeklyHoursRepository weeklyHoursRepository;

    public WeeklyHoursService(WeeklyHoursRepository weeklyHoursRepository) {
        this.weeklyHoursRepository = weeklyHoursRepository;
    }

    public List<WeeklyHours> getAllWeeklyHours() {
        return weeklyHoursRepository.findAll();
    }

    public Optional<WeeklyHours> getWeeklyHoursById(Long id) {
        return weeklyHoursRepository.findById(id);
    }

    // Crear nuevas configuraciones de horas (semanales, diarias, descanso)
    @Transactional
    public WeeklyHoursDTO createWeeklyHours(WeeklyHoursDTO weeklyHoursDTO) {
        // ✅ Validaciones obligatorias
        validateMinimumWeeklyHours(weeklyHoursDTO.getHours());
        validateMinimumDailyHours(weeklyHoursDTO.getHours());
        validateMinimumBreakMinutes(weeklyHoursDTO.getHours());

        Optional<WeeklyHours> existingRecord = weeklyHoursRepository.findFirstByOrderByIdAsc();
        WeeklyHours weeklyHours;

        if (existingRecord.isPresent()) {
            weeklyHours = existingRecord.get();
            weeklyHours.setHours(weeklyHoursDTO.getHours());
            weeklyHours.setUpdatedAt(LocalDateTime.now());
        } else {
            weeklyHours = new WeeklyHours();
            weeklyHours.setHours(weeklyHoursDTO.getHours());
            weeklyHours.setCreatedAt(LocalDateTime.now());
            weeklyHours.setUpdatedAt(LocalDateTime.now());
        }

        weeklyHours = weeklyHoursRepository.save(weeklyHours);
        return convertToDTO(weeklyHours);
    }

    private WeeklyHoursDTO convertToDTO(WeeklyHours weeklyHours) {
        WeeklyHoursDTO dto = new WeeklyHoursDTO();
        dto.setHours(weeklyHours.getHours());
        dto.setCreatedAt(weeklyHours.getCreatedAt());
        dto.setUpdatedAt(weeklyHours.getUpdatedAt());
        dto.id = weeklyHours.getId();
        return dto;
    }

    public WeeklyHoursDTO updateWeeklyHours(Long id, WeeklyHoursDTO weeklyHoursDTO) {
        // ✅ Validaciones obligatorias
        validateMinimumWeeklyHours(weeklyHoursDTO.getHours());
        validateMinimumDailyHours(weeklyHoursDTO.getHours());
        validateMinimumBreakMinutes(weeklyHoursDTO.getHours());

        WeeklyHours weeklyHours = weeklyHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyHours not found with id " + id));

        weeklyHours.setHours(weeklyHoursDTO.getHours());
        weeklyHours.setCreatedAt(weeklyHoursDTO.getCreatedAt());
        weeklyHours.setUpdatedAt(weeklyHoursDTO.getUpdatedAt());

        weeklyHours = weeklyHoursRepository.save(weeklyHours);
        return convertToDTO(weeklyHours);
    }

    public void deleteWeeklyHours(Long id) {
        WeeklyHours weeklyHours = weeklyHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyHours not found with id " + id));
        weeklyHoursRepository.delete(weeklyHours);
    }

    public int getCurrentWeeklyHours() {
        Optional<WeeklyHours> weeklyHoursOpt = weeklyHoursRepository.findFirstByOrderByIdAsc();
        if (weeklyHoursOpt.isPresent()) {
            WeeklyHours weeklyHours = weeklyHoursOpt.get();
            String value = weeklyHours.getHours(); // Ej: "44:00"
            String[] partes = value.split(":");
            int hours = Integer.parseInt(partes[0]);
            return hours;
        } else {
            throw new ResourceNotFoundException("No hay configuración de horas semanales. Debe configurarlas primero.");
        }
    }

    // 🔒 Validación para horas semanales (mínimo 30:00)
    public void validateMinimumWeeklyHours(String value) {
        try {
            String[] parts = value.split(":");
            int hours = Integer.parseInt(parts[0]);

            if (hours < 30) {
                throw new IllegalArgumentException("El valor mínimo permitido para las horas semanales es 30:00");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de horas inválido. Debe ser HH:mm, por ejemplo '30:00'");
        }
    }

    // 🔒 Validación para horas diarias (mínimo 5:00)
    public void validateMinimumDailyHours(String value) {
        try {
            String[] parts = value.split(":");
            int hours = Integer.parseInt(parts[0]);

            if (hours < 5) {
                throw new IllegalArgumentException("El valor mínimo permitido para las horas diarias es 5:00");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de horas inválido. Debe ser HH:mm, por ejemplo '5:00'");
        }
    }


    // Validación para descanso (mínimo 30 minutos)
    public void validateMinimumBreakMinutes(String value) {
        try {
            int minutes = Integer.parseInt(value);
            if (minutes < 30) {
                throw new IllegalArgumentException("El descanso mínimo permitido es de 30 minutos.");
            }
            if (minutes > 45) {
                throw new IllegalArgumentException("El descanso máximo permitido es de 45 minutos.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El valor del descanso debe ser un número en minutos. Ej: '30'");
        }
    }



}

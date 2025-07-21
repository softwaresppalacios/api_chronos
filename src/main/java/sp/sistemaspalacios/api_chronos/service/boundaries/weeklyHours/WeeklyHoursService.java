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

    // Crear nuevas horas semanales
    @Transactional
    public WeeklyHoursDTO createWeeklyHours(WeeklyHoursDTO weeklyHoursDTO) {
        validateMinimumWeeklyHours(weeklyHoursDTO.getHours());

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



    // Método de conversión de WeeklyHours a WeeklyHoursDTO
    private WeeklyHoursDTO convertToDTO(WeeklyHours weeklyHours) {
        WeeklyHoursDTO dto = new WeeklyHoursDTO();
        dto.setHours(weeklyHours.getHours());
        dto.setCreatedAt(weeklyHours.getCreatedAt());
        dto.setUpdatedAt(weeklyHours.getUpdatedAt());

        // Asignar el id a dto sin setId
        dto.id = weeklyHours.getId(); // Esto asigna el id directamente, sin necesidad de setId

        return dto;
    }


    // Actualizar horas semanales
    public WeeklyHoursDTO updateWeeklyHours(Long id, WeeklyHoursDTO weeklyHoursDTO) {
        validateMinimumWeeklyHours(weeklyHoursDTO.getHours());

        WeeklyHours weeklyHours = weeklyHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyHours not found with id " + id));

        weeklyHours.setHours(weeklyHoursDTO.getHours());
        weeklyHours.setCreatedAt(weeklyHoursDTO.getCreatedAt());
        weeklyHours.setUpdatedAt(weeklyHoursDTO.getUpdatedAt());

        weeklyHours = weeklyHoursRepository.save(weeklyHours);
        return convertToDTO(weeklyHours);
    }


    // Eliminar horas semanales
    public void deleteWeeklyHours(Long id) {
        WeeklyHours weeklyHours = weeklyHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyHours not found with id " + id));
        weeklyHoursRepository.delete(weeklyHours);
    }


    public int getCurrentWeeklyHours() {
        Optional<WeeklyHours> weeklyHoursOpt = weeklyHoursRepository.findFirstByOrderByIdAsc();
        if (weeklyHoursOpt.isPresent()) {
            WeeklyHours weeklyHours = weeklyHoursOpt.get();
            String value = weeklyHours.getHours(); // "44:00" por ejemplo
            String[] partes = value.split(":");
            int hours = Integer.parseInt(partes[0]);
            // Si quieres sumar minutos:
            // int minutos = Integer.parseInt(partes[1]);
            // return horas * 60 + minutos; // Total en minutos
            return hours;
        } else {
            throw new ResourceNotFoundException("No hay configuración de horas semanales. Debe configurarlas primero.");
        }
    }

    private void validateMinimumWeeklyHours(String hoursStr) {
        try {
            String[] parts = hoursStr.split(":");
            int hours = Integer.parseInt(parts[0]);

            if (hours < 30) {
                throw new IllegalArgumentException("El valor mínimo permitido para las horas semanales es 30:00");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de horas inválido. Debe ser HH:mm, por ejemplo '30:00'");
        }
    }



}

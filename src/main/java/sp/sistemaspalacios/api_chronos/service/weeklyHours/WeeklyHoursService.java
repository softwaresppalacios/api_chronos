package sp.sistemaspalacios.api_chronos.service.weeklyHours;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.WeeklyHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.repository.weeklyHours.WeeklyHoursRepository;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

@Service
public class WeeklyHoursService {

    @Autowired
    private WeeklyHoursRepository weeklyHoursRepository;

    public List<WeeklyHours> getAllWeeklyHours() {
        return weeklyHoursRepository.findAll();
    }

    public Optional<WeeklyHours> getWeeklyHoursById(Long id) {
        return weeklyHoursRepository.findById(id);
    }

    // Crear nuevas horas semanales
    public WeeklyHoursDTO createWeeklyHours(WeeklyHoursDTO weeklyHoursDTO) {
        // Guardar directamente el String de horas
        WeeklyHours weeklyHours = new WeeklyHours();
        weeklyHours.setHours(weeklyHoursDTO.getHours());  // Guardar como String
        weeklyHours.setCreatedAt(weeklyHoursDTO.getCreatedAt());
        weeklyHours.setUpdatedAt(weeklyHoursDTO.getUpdatedAt());

        // Guardar en la base de datos
        weeklyHours = weeklyHoursRepository.save(weeklyHours);

        // Convertir la entidad a DTO antes de devolverla
        return convertToDTO(weeklyHours);
    }

    // Método de conversión de WeeklyHours a WeeklyHoursDTO
    private WeeklyHoursDTO convertToDTO(WeeklyHours weeklyHours) {
        WeeklyHoursDTO dto = new WeeklyHoursDTO();
        dto.setId(weeklyHours.getId());
        dto.setHours(weeklyHours.getHours());  // Simplemente copiar el String de horas
        dto.setCreatedAt(weeklyHours.getCreatedAt());
        dto.setUpdatedAt(weeklyHours.getUpdatedAt());
        return dto;
    }

    // Actualizar horas semanales
    public WeeklyHoursDTO updateWeeklyHours(Long id, WeeklyHoursDTO weeklyHoursDTO) {
        WeeklyHours weeklyHours = weeklyHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyHours not found with id " + id));

        weeklyHours.setHours(weeklyHoursDTO.getHours());  // Establecer horas como String
        weeklyHours.setCreatedAt(weeklyHoursDTO.getCreatedAt());
        weeklyHours.setUpdatedAt(weeklyHoursDTO.getUpdatedAt());

        // Guardar y devolver el DTO actualizado
        weeklyHours = weeklyHoursRepository.save(weeklyHours);
        return convertToDTO(weeklyHours);
    }

    // Eliminar horas semanales
    public void deleteWeeklyHours(Long id) {
        WeeklyHours weeklyHours = weeklyHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WeeklyHours not found with id " + id));
        weeklyHoursRepository.delete(weeklyHours);
    }
}

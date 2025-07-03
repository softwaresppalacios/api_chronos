package sp.sistemaspalacios.api_chronos.service.weeklyHours;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.WeeklyHoursDTO;
import sp.sistemaspalacios.api_chronos.entity.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.repository.weeklyHours.WeeklyHoursRepository;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
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
    @Transactional
    public WeeklyHoursDTO createWeeklyHours(WeeklyHoursDTO weeklyHoursDTO) {
        // 1. Buscar si existe algún registro
        Optional<WeeklyHours> existingRecord = weeklyHoursRepository.findFirstByOrderByIdAsc();

        WeeklyHours weeklyHours;

        if (existingRecord.isPresent()) {
            // 2. Si existe, actualizar el registro existente
            weeklyHours = existingRecord.get();
            weeklyHours.setHours(weeklyHoursDTO.getHours());
            weeklyHours.setUpdatedAt(LocalDateTime.now());
        } else {
            // 3. Si no existe, crear uno nuevo
            weeklyHours = new WeeklyHours();
            weeklyHours.setHours(weeklyHoursDTO.getHours());
            weeklyHours.setCreatedAt(LocalDateTime.now());
            weeklyHours.setUpdatedAt(LocalDateTime.now());
        }

        // 4. Guardar el registro
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

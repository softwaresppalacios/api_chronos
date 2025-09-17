package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;

import java.sql.Time;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeBlockService {

    private final EmployeeScheduleTimeBlockRepository timeBlockRepository;

    @Transactional
    public EmployeeScheduleTimeBlock updateTimeBlock(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO == null || timeBlockDTO.getId() == null) {
            throw new IllegalArgumentException("TimeBlockDTO y su ID son requeridos");
        }

        EmployeeScheduleTimeBlock timeBlock = timeBlockRepository.findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TimeBlock no encontrado con ID: " + timeBlockDTO.getId()));

        // Actualizar campos si están presentes
        if (timeBlockDTO.getStartTime() != null) {
            timeBlock.setStartTime(Time.valueOf(timeBlockDTO.getStartTime()));
        }
        if (timeBlockDTO.getEndTime() != null) {
            timeBlock.setEndTime(Time.valueOf(timeBlockDTO.getEndTime()));
        }

        timeBlock.setUpdatedAt(new Date());

        return timeBlockRepository.save(timeBlock);
    }

    public EmployeeScheduleTimeBlock getTimeBlockById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID no puede ser nulo");
        }

        return timeBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TimeBlock no encontrado con ID: " + id));
    }

    @Transactional
    public void deleteTimeBlock(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID no puede ser nulo");
        }

        if (!timeBlockRepository.existsById(id)) {
            throw new ResourceNotFoundException("TimeBlock no encontrado con ID: " + id);
        }

        timeBlockRepository.deleteById(id);
    }
}
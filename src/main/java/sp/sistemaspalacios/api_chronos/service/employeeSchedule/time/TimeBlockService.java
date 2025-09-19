package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.service.common.TimeService;
import sp.sistemaspalacios.api_chronos.service.common.WorkingTimeCalculatorService;
import sp.sistemaspalacios.api_chronos.service.common.WorkingTimeValidatorService;

import java.sql.Time;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeBlockService {

    private final EmployeeScheduleTimeBlockRepository timeBlockRepository;
    private final EmployeeScheduleDayRepository dayRepo;
    private final EmployeeScheduleTimeBlockRepository blockRepo;
    private final TimeService timeService;
    private final WorkingTimeCalculatorService calculator;
    private final WorkingTimeValidatorService validator;

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



}
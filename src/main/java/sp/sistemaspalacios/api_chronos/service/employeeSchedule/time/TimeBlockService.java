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
        System.out.println("\n💾 === SERVICIO: ACTUALIZANDO TIMEBLOCK INDIVIDUAL ===");
        System.out.println("📦 DTO recibido: " + timeBlockDTO);

        if (timeBlockDTO == null || timeBlockDTO.getId() == null) {
            throw new IllegalArgumentException("TimeBlockDTO y su ID son requeridos");
        }

        EmployeeScheduleTimeBlock timeBlock = timeBlockRepository
                .findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TimeBlock no encontrado con ID: " + timeBlockDTO.getId()));

        System.out.println("🔍 TimeBlock encontrado: " + timeBlock.getId());
        System.out.println("  - Horario actual: " + timeBlock.getStartTime() + " - " + timeBlock.getEndTime());
        System.out.println("  - Breaks actuales: " + timeBlock.getBreakStartTime() + " - " + timeBlock.getBreakEndTime());

        // ✅ ACTUALIZAR HORARIOS PRINCIPALES
        if (timeBlockDTO.getStartTime() != null) {
            Time newStartTime = Time.valueOf(timeBlockDTO.getStartTime());
            System.out.println("🕐 Actualizando start time: " + timeBlock.getStartTime() + " -> " + newStartTime);
            timeBlock.setStartTime(newStartTime);
        }

        if (timeBlockDTO.getEndTime() != null) {
            Time newEndTime = Time.valueOf(timeBlockDTO.getEndTime());
            System.out.println("🕐 Actualizando end time: " + timeBlock.getEndTime() + " -> " + newEndTime);
            timeBlock.setEndTime(newEndTime);
        }

        // ✅ ACTUALIZAR BREAKS
        if (timeBlockDTO.getBreakStartTime() != null && !timeBlockDTO.getBreakStartTime().trim().isEmpty()) {
            Time newBreakStart = Time.valueOf(timeBlockDTO.getBreakStartTime());
            System.out.println("☕ Actualizando break start: " + timeBlock.getBreakStartTime() + " -> " + newBreakStart);
            timeBlock.setBreakStartTime(newBreakStart);
        } else {
            if (timeBlock.getBreakStartTime() != null) {
                System.out.println("🧹 Limpiando break start time");
            }
            timeBlock.setBreakStartTime(null);
        }

        if (timeBlockDTO.getBreakEndTime() != null && !timeBlockDTO.getBreakEndTime().trim().isEmpty()) {
            Time newBreakEnd = Time.valueOf(timeBlockDTO.getBreakEndTime());
            System.out.println("☕ Actualizando break end: " + timeBlock.getBreakEndTime() + " -> " + newBreakEnd);
            timeBlock.setBreakEndTime(newBreakEnd);
        } else {
            if (timeBlock.getBreakEndTime() != null) {
                System.out.println("🧹 Limpiando break end time");
            }
            timeBlock.setBreakEndTime(null);
        }

        timeBlock.setUpdatedAt(new Date());
        EmployeeScheduleTimeBlock savedBlock = timeBlockRepository.save(timeBlock);

        System.out.println("✅ TimeBlock actualizado en servicio:");
        System.out.println("  - ID: " + savedBlock.getId());
        System.out.println("  - Nuevo horario: " + savedBlock.getStartTime() + " - " + savedBlock.getEndTime());
        System.out.println("  - Nuevos breaks: " + savedBlock.getBreakStartTime() + " - " + savedBlock.getBreakEndTime());
        System.out.println("  - Updated at: " + savedBlock.getUpdatedAt());

        return savedBlock;
    }


}
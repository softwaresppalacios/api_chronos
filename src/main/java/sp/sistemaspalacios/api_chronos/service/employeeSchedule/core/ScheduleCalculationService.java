package sp.sistemaspalacios.api_chronos.service.employeeSchedule.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO; // Usar tu DTO existente
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleCalculationService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final EmployeeDataService employeeDataService;

    public EmployeeHoursSummaryDTO calculateEmployeeHoursSummary(Long employeeId) {
        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID no puede ser nulo");
        }

        try {
            List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);

            if (schedules.isEmpty()) {
                return createEmptyHoursSummary(employeeId);
            }

            // Lógica básica de cálculo - puedes expandir después
            double totalHours = schedules.stream()
                    .mapToDouble(this::calculateScheduleHours)
                    .sum();

            EmployeeHoursSummaryDTO summary = new EmployeeHoursSummaryDTO();
            summary.setEmployeeId(employeeId);
            summary.setEmployeeName(employeeDataService.getEmployeeName(employeeId));
            summary.setTotalHours(totalHours);
            summary.setRegularHours(totalHours); // Simplificado por ahora
            summary.setOvertimeHours(0.0);
            summary.setFestivoHours(0.0);
            summary.setAssignedHours(totalHours);
            summary.setOvertimeType("Normal");
            summary.setOvertimeBreakdown(new HashMap<>());
            summary.setLastUpdated(new Date());

            return summary;

        } catch (Exception e) {
            log.error("Error calculando resumen para empleado {}: {}", employeeId, e.getMessage());
            return createEmptyHoursSummary(employeeId);
        }
    }

    @Transactional
    public void cleanupEmptyDaysForEmployee(Long employeeId) {
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);

        schedules.forEach(schedule -> {
            if (schedule.getDays() != null) {
                schedule.getDays().removeIf(day ->
                        day.getTimeBlocks() == null || day.getTimeBlocks().isEmpty());
                employeeScheduleRepository.save(schedule);
            }
        });

        log.info("Limpieza completada para empleado: {}", employeeId);
    }

    private EmployeeHoursSummaryDTO createEmptyHoursSummary(Long employeeId) {
        EmployeeHoursSummaryDTO summary = new EmployeeHoursSummaryDTO();
        summary.setEmployeeId(employeeId);
        summary.setEmployeeName(employeeDataService.getEmployeeName(employeeId));
        summary.setTotalHours(0.0);
        summary.setAssignedHours(0.0);
        summary.setRegularHours(0.0);
        summary.setOvertimeHours(0.0);
        summary.setFestivoHours(0.0);
        summary.setOvertimeType("Normal");
        summary.setOvertimeBreakdown(new HashMap<>());
        summary.setLastUpdated(new Date());
        return summary;
    }

    private double calculateScheduleHours(EmployeeSchedule schedule) {
        if (schedule.getDays() == null) return 0.0;

        return schedule.getDays().stream()
                .mapToDouble(day -> {
                    if (day.getTimeBlocks() == null) return 0.0;
                    return day.getTimeBlocks().stream()
                            .mapToDouble(tb -> calculateBlockHours(tb.getStartTime().toString(), tb.getEndTime().toString()))
                            .sum();
                })
                .sum();
    }

    private double calculateBlockHours(String startTime, String endTime) {
        try {
            String[] start = startTime.split(":");
            String[] end = endTime.split(":");

            double startHours = Integer.parseInt(start[0]) + Integer.parseInt(start[1]) / 60.0;
            double endHours = Integer.parseInt(end[0]) + Integer.parseInt(end[1]) / 60.0;

            return Math.max(0, endHours - startHours);
        } catch (Exception e) {
            return 0.0;
        }
    }
}


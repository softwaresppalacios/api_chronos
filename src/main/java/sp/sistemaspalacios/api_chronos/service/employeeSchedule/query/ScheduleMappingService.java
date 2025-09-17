package sp.sistemaspalacios.api_chronos.service.employeeSchedule.query;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeResponse;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.shift.ShiftsDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeDataService;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleMappingService {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final EmployeeDataService employeeDataService;




    public EmployeeScheduleDTO convertToCompleteDTO(EmployeeSchedule schedule) {
        if (schedule == null) return null;

        System.out.println("Convirtiendo schedule ID: " + schedule.getId() + " para empleado: " + schedule.getEmployeeId());

        // Obtener datos del empleado desde la API externa
        EmployeeResponse response = employeeDataService.getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        EmployeeScheduleDTO dto = new EmployeeScheduleDTO();
        dto.setId(schedule.getId());
        dto.setNumberId(schedule.getEmployeeId());

        // Datos del empleado (con valores por defecto si no se encuentran)
        dto.setFirstName(getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, "Desconocido"));
        dto.setSecondName(getEmployeeField(employee, EmployeeResponse.Employee::getSecondName, ""));
        dto.setSurName(getEmployeeField(employee, EmployeeResponse.Employee::getSurName, "Desconocido"));
        dto.setSecondSurname(getEmployeeField(employee, EmployeeResponse.Employee::getSecondSurname, ""));
        dto.setDependency(getEmployeeDependency(employee));
        dto.setPosition(getEmployeePosition(employee));

        // Fechas
        dto.setStartDate(formatDate(schedule.getStartDate()));
        dto.setEndDate(formatDate(schedule.getEndDate()));
        dto.setDaysParentId(schedule.getDaysParentId());

        // Shift info
        dto.setShift(buildShiftMap(schedule.getShift()));

        // Days structure COMPLETA con timeBlocks
        dto.setDays(buildDaysStructure(schedule));

        System.out.println("DTO convertido exitosamente");
        return dto;
    }

    // MÉTODO para construir la estructura de días con timeBlocks
    private Map<String, Object> buildDaysStructure(EmployeeSchedule schedule) {
        Map<String, Object> daysMap = new HashMap<>();
        daysMap.put("id", schedule.getDaysParentId());

        if (schedule.getDays() != null && !schedule.getDays().isEmpty()) {
            List<Map<String, Object>> dayItems = schedule.getDays().stream()
                    .sorted(Comparator.comparing(EmployeeScheduleDay::getDate))
                    .map(this::convertDayToMap)
                    .collect(Collectors.toList());
            daysMap.put("items", dayItems);
        } else {
            daysMap.put("items", new ArrayList<>());
        }

        return daysMap;
    }

    // MÉTODO para convertir un día individual
    private Map<String, Object> convertDayToMap(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new HashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", formatDate(day.getDate()));
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        // TimeBlocks - CRÍTICO para el frontend
        if (day.getTimeBlocks() != null && !day.getTimeBlocks().isEmpty()) {
            List<Map<String, Object>> timeBlocks = day.getTimeBlocks().stream()
                    .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                    .map(this::convertTimeBlockToMap)
                    .collect(Collectors.toList());
            dayMap.put("timeBlocks", timeBlocks);
        } else {
            dayMap.put("timeBlocks", new ArrayList<>());
        }

        return dayMap;
    }

    // MÉTODO para convertir timeBlocks
    private Map<String, Object> convertTimeBlockToMap(EmployeeScheduleTimeBlock block) {
        Map<String, Object> blockMap = new HashMap<>();
        blockMap.put("id", block.getId());
        blockMap.put("startTime", block.getStartTime().toString());
        blockMap.put("endTime", block.getEndTime().toString());
        blockMap.put("numberId", block.getEmployeeScheduleDay().getEmployeeSchedule().getEmployeeId());
        return blockMap;
    }

    // MÉTODO para construir shift info
    private ShiftsDTO buildShiftMap(Shifts shift) {
        if (shift == null) return null;

        ShiftsDTO shiftDTO = new ShiftsDTO();
        shiftDTO.setId(shift.getId());
        shiftDTO.setName(shift.getName());
        shiftDTO.setDescription(shift.getDescription());
        shiftDTO.setTimeBreak(null); // o shift.getTimeBreak() si existe
        shiftDTO.setShiftDetails(new ArrayList<>()); // lista vacía por defecto

        return shiftDTO;
    }




    // MÉTODOS auxiliares para datos del empleado
    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter,
                                   T defaultValue) {
        try {
            return employee != null ? getter.apply(employee) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getEmployeeDependency(EmployeeResponse.Employee employee) {
        try {
            return employee != null &&
                    employee.getPosition() != null &&
                    employee.getPosition().getDependency() != null
                    ? employee.getPosition().getDependency().getName()
                    : "Sin dependencia";
        } catch (Exception e) {
            return "Sin dependencia";
        }
    }

    private String getEmployeePosition(EmployeeResponse.Employee employee) {
        try {
            return employee != null && employee.getPosition() != null
                    ? employee.getPosition().getName()
                    : "Sin posición";
        } catch (Exception e) {
            return "Sin posición";
        }
    }

    private String formatDate(Date date) {
        return date != null ? dateFormat.format(date) : null;
    }

    // Tu método convertToDTO existente (básico) puede quedarse igual
    public EmployeeScheduleDTO convertToDTO(EmployeeSchedule schedule) {
        if (schedule == null) return null;

        EmployeeScheduleDTO dto = new EmployeeScheduleDTO();
        dto.setId(schedule.getId());
        dto.setNumberId(schedule.getEmployeeId());

        if (schedule.getShift() != null) {
            dto.setShiftName(schedule.getShift().getName());
        }

        dto.setStartDate(formatDate(schedule.getStartDate()));
        dto.setEndDate(formatDate(schedule.getEndDate()));
        dto.setDaysParentId(schedule.getDaysParentId());

        // Estructura de días vacía para listados básicos
        Map<String, Object> daysMap = new HashMap<>();
        daysMap.put("items", new ArrayList<>());
        dto.setDays(daysMap);

        return dto;
    }
}
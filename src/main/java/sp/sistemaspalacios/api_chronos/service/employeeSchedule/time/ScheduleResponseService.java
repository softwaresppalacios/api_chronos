package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeScheduleService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleResponseService {

    private final EmployeeScheduleService employeeScheduleService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public List<Map<String, Object>> processScheduleCreation(List<Map<String, Object>> scheduleRequests) {
        if (scheduleRequests == null || scheduleRequests.isEmpty()) {
            throw new IllegalArgumentException("No se proporcionaron solicitudes de horarios");
        }

        log.info("Procesando {} solicitudes de creación de horarios", scheduleRequests.size());

        List<EmployeeSchedule> schedules = scheduleRequests.stream()
                .map(this::parseScheduleRequest)
                .collect(Collectors.toList());

        List<EmployeeSchedule> createdSchedules = employeeScheduleService.createMultipleSchedules(schedules);

        return createdSchedules.stream()
                .map(this::createScheduleResponse)
                .collect(Collectors.toList());
    }

    public Map<String, Object> createScheduleResponse(EmployeeSchedule schedule) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", schedule.getId());
        response.put("employeeId", schedule.getEmployeeId());
        response.put("shift", convertShiftToResponse(schedule.getShift()));
        response.put("startDate", schedule.getStartDate());
        response.put("endDate", schedule.getEndDate());
        response.put("createdAt", schedule.getCreatedAt());
        response.put("updatedAt", schedule.getUpdatedAt());
        response.put("daysParentId", schedule.getDaysParentId());
        response.put("days", createDaysStructure(schedule));

        return response;
    }

    // =================== MÉTODOS PRIVADOS ===================

    private EmployeeSchedule parseScheduleRequest(Map<String, Object> request) {
        try {
            EmployeeSchedule schedule = new EmployeeSchedule();

            // Employee ID
            schedule.setEmployeeId(extractLong(request, "employeeId"));

            // Shift
            schedule.setShift(extractShift(request));

            // Dates
            schedule.setStartDate(parseDate(extractString(request, "startDate")));

            String endDateStr = (String) request.get("endDate");
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                schedule.setEndDate(parseDate(endDateStr));
            }

            return schedule;

        } catch (Exception e) {
            log.error("Error parseando solicitud de horario: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error en formato de solicitud: " + e.getMessage());
        }
    }

    private Shifts extractShift(Map<String, Object> request) {
        Object shiftObj = request.get("shift");
        if (shiftObj == null) {
            throw new IllegalArgumentException("Información del turno es requerida");
        }

        if (!(shiftObj instanceof Map)) {
            throw new IllegalArgumentException("Formato inválido para el turno");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> shiftMap = (Map<String, Object>) shiftObj;

        Shifts shift = new Shifts();
        shift.setId(extractLong(shiftMap, "id"));

        return shift;
    }

    private Date parseDate(String dateStr) {
        try {
            return dateFormat.parse(dateStr);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de fecha inválido (use yyyy-MM-dd): " + dateStr);
        }
    }

    private Map<String, Object> convertShiftToResponse(Shifts shift) {
        if (shift == null) return null;

        Map<String, Object> shiftMap = new LinkedHashMap<>();
        shiftMap.put("id", shift.getId());
        shiftMap.put("name", shift.getName());
        shiftMap.put("description", shift.getDescription());
        shiftMap.put("timeBreak", shift.getTimeBreak());
        shiftMap.put("dependencyId", shift.getDependencyId());
        shiftMap.put("createdAt", shift.getCreatedAt());
        shiftMap.put("updatedAt", shift.getUpdatedAt());

        if (shift.getShiftDetails() != null) {
            shiftMap.put("shiftDetails", shift.getShiftDetails().stream()
                    .map(this::convertShiftDetailToResponse)
                    .collect(Collectors.toList()));
        }

        return shiftMap;
    }

    private Map<String, Object> convertShiftDetailToResponse(ShiftDetail detail) {
        Map<String, Object> detailMap = new LinkedHashMap<>();
        detailMap.put("id", detail.getId());
        detailMap.put("dayOfWeek", detail.getDayOfWeek());
        detailMap.put("startTime", detail.getStartTime());
        detailMap.put("endTime", detail.getEndTime());
        detailMap.put("breakStartTime", detail.getBreakStartTime());
        detailMap.put("breakEndTime", detail.getBreakEndTime());
        detailMap.put("breakMinutes", detail.getBreakMinutes());
        detailMap.put("createdAt", detail.getCreatedAt());
        detailMap.put("updatedAt", detail.getUpdatedAt());
        return detailMap;
    }

    private Map<String, Object> createDaysStructure(EmployeeSchedule schedule) {
        Map<String, Object> daysStructure = new LinkedHashMap<>();
        daysStructure.put("id", schedule.getDaysParentId() != null ? schedule.getDaysParentId() : schedule.getId());

        if (schedule.getDays() != null) {
            daysStructure.put("items", schedule.getDays().stream()
                    .map(this::convertDayToResponse)
                    .collect(Collectors.toList()));
        } else {
            daysStructure.put("items", Collections.emptyList());
        }

        return daysStructure;
    }

    private Map<String, Object> convertDayToResponse(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new LinkedHashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", day.getDate());
        dayMap.put("dayOfWeek", day.getDayOfWeek());
        dayMap.put("createdAt", day.getCreatedAt());
        dayMap.put("updatedAt", day.getUpdatedAt());

        if (day.getTimeBlocks() != null) {
            dayMap.put("timeBlocks", day.getTimeBlocks().stream()
                    .map(this::convertTimeBlockToResponse)
                    .collect(Collectors.toList()));
        } else {
            dayMap.put("timeBlocks", Collections.emptyList());
        }

        return dayMap;
    }

    private Map<String, Object> convertTimeBlockToResponse(EmployeeScheduleTimeBlock timeBlock) {
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("id", timeBlock.getId());
        blockMap.put("startTime", timeBlock.getStartTime() != null ? timeBlock.getStartTime().toString() : null);
        blockMap.put("endTime", timeBlock.getEndTime() != null ? timeBlock.getEndTime().toString() : null);
        blockMap.put("createdAt", timeBlock.getCreatedAt());
        blockMap.put("updatedAt", timeBlock.getUpdatedAt());
        return blockMap;
    }

    // =================== UTILIDADES ===================

    private Long extractLong(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Campo requerido: " + key);
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido para " + key + ": " + value);
        }
    }

    private String extractString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Campo requerido: " + key);
        }
        return value.toString().trim();
    }

}
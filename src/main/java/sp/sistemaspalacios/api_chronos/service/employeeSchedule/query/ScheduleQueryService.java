package sp.sistemaspalacios.api_chronos.service.employeeSchedule.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeResponse;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeDataService;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final ScheduleMappingService scheduleMappingService;
    private final EmployeeDataService employeeDataService;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Acepta LocalDate o java.util.Date
    private String fmtDate(Object date) {
        if (date == null) return null;
        if (date instanceof LocalDate ld) return ld.format(ISO_DATE);
        if (date instanceof Date d) return new SimpleDateFormat("yyyy-MM-dd").format(d);
        // fallback: no reventar si llega otro tipo
        return String.valueOf(date);
    }

    // Acepta LocalTime o java.sql.Time
    private String fmtTime(Object time) {
        if (time == null) return null;
        if (time instanceof LocalTime lt) return lt.format(ISO_TIME);
        if (time instanceof java.sql.Time st) return st.toLocalTime().format(ISO_TIME);
        return String.valueOf(time);
    }

    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(List<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }

        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeIdIn(employeeIds);
        return schedules.stream()
                .map(scheduleMappingService::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeScheduleDTO> getCompleteSchedulesByEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }

        try {
            List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);
            if (schedules.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> scheduleIds = schedules.stream()
                    .map(EmployeeSchedule::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!scheduleIds.isEmpty()) {
                try {
                    List<EmployeeScheduleDay> daysWithBlocks =
                            employeeScheduleRepository.findDaysWithTimeBlocksByScheduleIds(scheduleIds);

                    Map<Long, List<EmployeeScheduleDay>> daysByScheduleId = daysWithBlocks.stream()
                            .collect(Collectors.groupingBy(
                                    day -> day.getEmployeeSchedule().getId(),
                                    Collectors.toList()
                            ));

                    for (EmployeeSchedule schedule : schedules) {
                        List<EmployeeScheduleDay> days = daysByScheduleId.get(schedule.getId());
                        if (days != null) {
                            if (schedule.getDays() == null) {
                                schedule.setDays(new ArrayList<>());
                            }
                            schedule.getDays().clear();
                            schedule.getDays().addAll(days);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error cargando timeBlocks: " + e.getMessage());
                }
            }

            return schedules.stream()
                    .map(scheduleMappingService::convertToCompleteDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error en consulta BD: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error cargando horarios del empleado", e);
        }
    }

    public List<Map<String, Object>> getSchedulesByDependencyId(
            Long dependencyId, LocalDate startDate, LocalDate endDate,
            java.time.LocalTime startTime, Long shiftId) {

        if (dependencyId == null) return Collections.emptyList();

        try {
            List<EmployeeSchedule> schedules;

            if (shiftId != null) {
                schedules = employeeScheduleRepository.findByShiftId(shiftId);

            } else {
                // Obtener todos los shifts que pertenecen a esta dependencia
                List<Shifts> dependencyShifts = shiftsRepository.findByDependencyId(dependencyId);
                if (dependencyShifts.isEmpty()) {
                    return Collections.emptyList();
                }
                List<Long> shiftIds = dependencyShifts.stream()
                        .map(Shifts::getId)
                        .collect(Collectors.toList());
                schedules = employeeScheduleRepository.findByShiftIdIn(shiftIds);
            }

            if (startDate != null || endDate != null || startTime != null) {
                schedules = applyAdditionalFilters(schedules, startDate, endDate, startTime);
            }

            return groupSchedulesByShift(schedules);

        } catch (Exception e) {
            System.err.println("Error en consulta de dependencia: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<EmployeeSchedule> applyAdditionalFilters(List<EmployeeSchedule> schedules,
                                                          LocalDate startDate,
                                                          LocalDate endDate,
                                                          java.time.LocalTime startTime) {
        return schedules.stream()
                .filter(schedule -> {
                    // Filtro por rango de fechas
                    if (startDate != null || endDate != null) {
                        LocalDate scheduleStart = schedule.getStartDate();
                        LocalDate scheduleEnd = (schedule.getEndDate() != null) ? schedule.getEndDate() : scheduleStart;

                        if (scheduleStart == null) return false;

                        if (startDate != null && scheduleEnd.isBefore(startDate)) return false;
                        if (endDate != null && scheduleStart.isAfter(endDate)) return false;
                    }

                    // Filtro por hora de inicio (si lo necesitas, aquí)
                    if (startTime != null) {
                        // Ejemplo: podrías revisar si algún timeBlock comienza a cierta hora
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> groupSchedulesByShift(List<EmployeeSchedule> schedules) {
        // Agrupar schedules por shift ID
        Map<Long, List<EmployeeSchedule>> schedulesByShift = schedules.stream()
                .collect(Collectors.groupingBy(
                        schedule -> schedule.getShift() != null ? schedule.getShift().getId() : 0L
                ));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<Long, List<EmployeeSchedule>> entry : schedulesByShift.entrySet()) {
            Long shiftId = entry.getKey();
            List<EmployeeSchedule> shiftSchedules = entry.getValue();

            if (shiftSchedules.isEmpty()) continue;

            // Crear grupo
            Map<String, Object> group = new HashMap<>();

            // Información del shift
            EmployeeSchedule firstSchedule = shiftSchedules.get(0);
            if (firstSchedule.getShift() != null) {
                Map<String, Object> shiftInfo = new HashMap<>();
                shiftInfo.put("id", firstSchedule.getShift().getId());
                shiftInfo.put("name", firstSchedule.getShift().getName());
                shiftInfo.put("description", firstSchedule.getShift().getDescription());
                group.put("shift", shiftInfo);
            }

            // Obtener dependency del primer empleado (para display)
            String dependencyName = "Sin dependencia";
            try {
                EmployeeResponse response = employeeDataService.getEmployeeData(firstSchedule.getEmployeeId());
                if (response != null && response.getEmployee() != null &&
                        response.getEmployee().getPosition() != null &&
                        response.getEmployee().getPosition().getDependency() != null) {
                    dependencyName = extractDependencyName(response.getEmployee().getPosition().getDependency());
                }
            } catch (Exception e) {
                System.err.println("Error obteniendo dependency: " + e.getMessage());
            }
            group.put("dependency", dependencyName);

            // Crear lista de empleados únicos
            Set<Long> uniqueEmployeeIds = new HashSet<>();
            List<Map<String, Object>> employees = new ArrayList<>();

            for (EmployeeSchedule schedule : shiftSchedules) {
                if (!uniqueEmployeeIds.contains(schedule.getEmployeeId())) {
                    uniqueEmployeeIds.add(schedule.getEmployeeId());

                    Map<String, Object> employeeData = createEmployeeData(schedule);
                    employees.add(employeeData);
                }
            }

            group.put("employees", employees);
            group.put("employeeCount", employees.size());

            result.add(group);
        }
        return result;
    }

    private Map<String, Object> createEmployeeData(EmployeeSchedule schedule) {
        Map<String, Object> employeeData = new HashMap<>();
        employeeData.put("id", schedule.getEmployeeId());
        employeeData.put("numberId", schedule.getEmployeeId());

        // Datos del empleado
        try {
            EmployeeResponse response = employeeDataService.getEmployeeData(schedule.getEmployeeId());
            if (response != null && response.getEmployee() != null) {
                EmployeeResponse.Employee emp = response.getEmployee();
                employeeData.put("firstName", emp.getFirstName() != null ? emp.getFirstName() : "");
                employeeData.put("surName", emp.getSurName() != null ? emp.getSurName() : "");
            } else {
                employeeData.put("firstName", "Desconocido");
                employeeData.put("surName", "");
            }
        } catch (Exception e) {
            employeeData.put("firstName", "Error");
            employeeData.put("surName", "");
        }

        // Información del shift
        if (schedule.getShift() != null) {
            Map<String, Object> shiftInfo = new HashMap<>();
            shiftInfo.put("id", schedule.getShift().getId());
            shiftInfo.put("name", schedule.getShift().getName());
            shiftInfo.put("description", schedule.getShift().getDescription());
            employeeData.put("shift", shiftInfo);
        }

        // Fechas (usar helper que soporta LocalDate y Date)
        employeeData.put("startDate", fmtDate(schedule.getStartDate()));
        employeeData.put("endDate", fmtDate(schedule.getEndDate()));

        // ✅ Días con timeblocks reales
        Map<String, Object> daysStructure = buildDaysStructureForEmployee(schedule);
        employeeData.put("days", daysStructure);

        return employeeData;
    }

    // Construir días con timeBlocks
    private Map<String, Object> buildDaysStructureForEmployee(EmployeeSchedule schedule) {
        Map<String, Object> daysStructure = new HashMap<>();
        daysStructure.put("id", schedule.getDaysParentId());

        try {
            // Cargar días con timeBlocks para este schedule específico
            List<EmployeeScheduleDay> daysWithBlocks =
                    employeeScheduleRepository.findDaysWithTimeBlocksByScheduleIds(Arrays.asList(schedule.getId()));

            if (!daysWithBlocks.isEmpty()) {
                List<Map<String, Object>> dayItems = daysWithBlocks.stream()
                        .sorted(Comparator.comparing(EmployeeScheduleDay::getDate))
                        .map(this::convertDayToMap)
                        .collect(Collectors.toList());

                daysStructure.put("items", dayItems);
            } else {
                daysStructure.put("items", new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("Error cargando días para empleado " + schedule.getEmployeeId() + ": " + e.getMessage());
            daysStructure.put("items", new ArrayList<>());
        }

        return daysStructure;
    }

    // Día → Map (fecha usando fmtDate)
    private Map<String, Object> convertDayToMap(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new HashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", fmtDate(day.getDate()));
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        if (day.getTimeBlocks() != null && !day.getTimeBlocks().isEmpty()) {
            List<Map<String, Object>> timeBlocks = day.getTimeBlocks().stream()
                    .map(block -> {
                        Map<String, Object> blockMap = new HashMap<>();
                        blockMap.put("id", block.getId());
                        blockMap.put("startTime", fmtTime(block.getStartTime()));
                        blockMap.put("endTime", fmtTime(block.getEndTime()));

                        // ✅ AGREGAR BREAKS:
                        if (block.getBreakStartTime() != null) {
                            blockMap.put("breakStartTime", fmtTime(block.getBreakStartTime()));
                        }
                        if (block.getBreakEndTime() != null) {
                            blockMap.put("breakEndTime", fmtTime(block.getBreakEndTime()));
                        }

                        return blockMap;
                    })
                    .collect(Collectors.toList());
            dayMap.put("timeBlocks", timeBlocks);
        } else {
            dayMap.put("timeBlocks", new ArrayList<>());
        }

        return dayMap;
    }
    private String extractDependencyName(Object dependency) {
        if (dependency == null) return null;

        try {
            java.lang.reflect.Field nameField = dependency.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            Object value = nameField.get(dependency);
            return value != null ? value.toString().trim() : null;
        } catch (Exception e) {
            System.err.println("Error extrayendo nombre de dependency: " + e.getMessage());
            return null;
        }
    }

    public List<EmployeeScheduleDTO> getSchedulesByShiftId(Long shiftId) {
        if (shiftId == null) return List.of();

        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByShiftId(shiftId);
        return schedules.stream()
                .map(scheduleMappingService::convertToDTO)
                .collect(Collectors.toList());
    }

    // import java.time.LocalDate;

    public List<EmployeeScheduleDTO> getSchedulesByDateRange(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) return List.of();

        LocalDate s = (startDate instanceof java.sql.Date)
                ? ((java.sql.Date) startDate).toLocalDate()
                : startDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        LocalDate e = (endDate instanceof java.sql.Date)
                ? ((java.sql.Date) endDate).toLocalDate()
                : endDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByDateRange(s, e);
        return schedules.stream()
                .map(scheduleMappingService::convertToDTO)
                .collect(Collectors.toList());
    }

}

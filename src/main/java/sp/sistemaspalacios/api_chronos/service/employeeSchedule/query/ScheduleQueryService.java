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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final ScheduleMappingService scheduleMappingService;
    private final EmployeeDataService employeeDataService;

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
        System.out.println("Buscando horarios para empleado: " + employeeId);

        if (employeeId == null) {
            System.out.println("Employee ID es null");
            return List.of();
        }

        try {
            List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);
            System.out.println("Encontrados " + schedules.size() + " horarios en BD");

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

                    System.out.println("TimeBlocks cargados exitosamente");
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
            System.out.println("Filtrando schedules por shifts de dependencyId: " + dependencyId);

            List<EmployeeSchedule> schedules;

            if (shiftId != null) {
                // Si viene un shiftId específico, usar solo ese
                schedules = employeeScheduleRepository.findByShiftId(shiftId);
                System.out.println("Filtrado por shiftId específico: " + shiftId);
            } else {
                // Obtener todos los shifts que pertenecen a esta dependencia
                List<Shifts> dependencyShifts = shiftsRepository.findByDependencyId(dependencyId);
                System.out.println("Shifts encontrados para dependencyId " + dependencyId + ": " + dependencyShifts.size());

                if (dependencyShifts.isEmpty()) {
                    System.out.println("No hay shifts para dependencyId: " + dependencyId);
                    return Collections.emptyList();
                }

                List<Long> shiftIds = dependencyShifts.stream()
                        .map(Shifts::getId)
                        .collect(Collectors.toList());

                System.out.println("Shift IDs a buscar: " + shiftIds);
                schedules = employeeScheduleRepository.findByShiftIdIn(shiftIds);
            }

            System.out.println("Schedules encontrados: " + schedules.size());

            // Aplicar filtros adicionales si vienen
            if (startDate != null || endDate != null || startTime != null) {
                schedules = applyAdditionalFilters(schedules, startDate, endDate, startTime);
                System.out.println("Schedules después de filtros adicionales: " + schedules.size());
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
                        LocalDate scheduleStart = convertToLocalDate(schedule.getStartDate());
                        LocalDate scheduleEnd = schedule.getEndDate() != null ?
                                convertToLocalDate(schedule.getEndDate()) : scheduleStart;

                        if (scheduleStart == null) return false;

                        if (startDate != null && scheduleEnd.isBefore(startDate)) return false;
                        if (endDate != null && scheduleStart.isAfter(endDate)) return false;
                    }

                    // Filtro por hora de inicio (implementar si es necesario)
                    if (startTime != null) {
                        // Aquí puedes agregar lógica para filtrar por startTime
                        // Por ejemplo, verificar si algún timeBlock del schedule comienza a esta hora
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

        System.out.println("Grupos finales generados: " + result.size());
        return result;
    }

    private Map<String, Object> createEmployeeData(EmployeeSchedule schedule) {
        Map<String, Object> employeeData = new HashMap<>();
        employeeData.put("id", schedule.getEmployeeId());
        employeeData.put("numberId", schedule.getEmployeeId());

        // Obtener datos del empleado
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

        // Fechas
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        employeeData.put("startDate", schedule.getStartDate() != null ? dateFormat.format(schedule.getStartDate()) : null);
        employeeData.put("endDate", schedule.getEndDate() != null ? dateFormat.format(schedule.getEndDate()) : null);

        // ✅ CARGAR DÍAS CON TIMEBLOCKS REALES
        Map<String, Object> daysStructure = buildDaysStructureForEmployee(schedule);
        employeeData.put("days", daysStructure);

        return employeeData;
    }

    // ✅ NUEVO MÉTODO para construir días con timeBlocks
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
                System.out.println("Días cargados para empleado " + schedule.getEmployeeId() + ": " + dayItems.size());
            } else {
                daysStructure.put("items", new ArrayList<>());
                System.out.println("No se encontraron días para empleado " + schedule.getEmployeeId());
            }
        } catch (Exception e) {
            System.err.println("Error cargando días para empleado " + schedule.getEmployeeId() + ": " + e.getMessage());
            daysStructure.put("items", new ArrayList<>());
        }

        return daysStructure;
    }

    // ✅ MÉTODO para convertir día a Map
    private Map<String, Object> convertDayToMap(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new HashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", new SimpleDateFormat("yyyy-MM-dd").format(day.getDate()));
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        // TimeBlocks
        if (day.getTimeBlocks() != null && !day.getTimeBlocks().isEmpty()) {
            List<Map<String, Object>> timeBlocks = day.getTimeBlocks().stream()
                    .map(block -> {
                        Map<String, Object> blockMap = new HashMap<>();
                        blockMap.put("id", block.getId());
                        blockMap.put("startTime", block.getStartTime().toString());
                        blockMap.put("endTime", block.getEndTime().toString());
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

    public List<EmployeeScheduleDTO> getSchedulesByDateRange(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) return List.of();

        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByDateRange(startDate, endDate);
        return schedules.stream()
                .map(scheduleMappingService::convertToDTO)
                .collect(Collectors.toList());
    }

    private LocalDate convertToLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}
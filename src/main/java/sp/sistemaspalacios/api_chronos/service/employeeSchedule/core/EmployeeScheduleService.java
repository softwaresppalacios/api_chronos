package sp.sistemaspalacios.api_chronos.service.employeeSchedule.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.AssignmentRequest;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.AssignmentResult;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.HolidayConfirmationRequest;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.ValidationResult;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment.ScheduleAssignmentGroupService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment.ScheduleAssignmentService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayExemptionService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.overtime.HourClassificationService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.query.ScheduleMappingService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.query.ScheduleQueryService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.time.TimeBlockService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeeScheduleService {

    // === DEPENDENCIAS ===
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final EmployeeDataService employeeDataService;
    private final ScheduleAssignmentService scheduleAssignmentService;
    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleMappingService scheduleMappingService;
    private final ScheduleCalculationService scheduleCalculationService;
    private final TimeBlockService timeBlockService;

    private final HolidayService holidayService;
    private final HolidayExemptionService holidayExemptionService;
    private final HourClassificationService hourClassificationService;

    private final ScheduleAssignmentGroupService groupService;

    public EmployeeScheduleService(
            EmployeeScheduleRepository employeeScheduleRepository,
            ShiftsRepository shiftsRepository,
            EmployeeDataService employeeDataService,
            ScheduleAssignmentService scheduleAssignmentService,
            ScheduleQueryService scheduleQueryService,
            ScheduleMappingService scheduleMappingService,
            ScheduleCalculationService scheduleCalculationService,
            TimeBlockService timeBlockService, HolidayService holidayService,
            HolidayExemptionService holidayExemptionService,
            HourClassificationService hourClassificationService,
            ScheduleAssignmentGroupService groupService
    ) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.employeeDataService = employeeDataService;
        this.scheduleAssignmentService = scheduleAssignmentService;
        this.scheduleQueryService = scheduleQueryService;
        this.scheduleMappingService = scheduleMappingService;
        this.scheduleCalculationService = scheduleCalculationService;
        this.timeBlockService = timeBlockService;
        this.holidayService = holidayService;
        this.holidayExemptionService = holidayExemptionService;
        this.hourClassificationService = hourClassificationService;
        this.groupService = groupService;
    }

    public AssignmentResult processMultipleAssignments(AssignmentRequest request) {
        return scheduleAssignmentService.processMultipleAssignments(request);
    }

    @Transactional
    public AssignmentResult processHolidayAssignment(HolidayConfirmationRequest request) {
        return scheduleAssignmentService.processHolidayAssignment(request);
    }

    public ValidationResult validateAssignmentOnly(AssignmentRequest request) {
        return scheduleAssignmentService.validateAssignmentOnly(request);
    }

    // =================== CONSULTAS (DELEGADAS) ===================

    public List<EmployeeScheduleDTO> getAllEmployeeSchedules() {
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findAll();
        return schedules.stream()
                .map(scheduleMappingService::convertToDTO)
                .collect(Collectors.toList());
    }

    public EmployeeScheduleDTO getEmployeeScheduleById(Long id) {
        EmployeeSchedule schedule = employeeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));
        return scheduleMappingService.convertToCompleteDTO(schedule);
    }

    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(List<Long> employeeIds) {
        return scheduleQueryService.getSchedulesByEmployeeIds(employeeIds);
    }

    public List<EmployeeScheduleDTO> getCompleteSchedulesByEmployeeId(Long employeeId) {
        return scheduleQueryService.getCompleteSchedulesByEmployeeId(employeeId);
    }


    public List<Map<String, Object>> getSchedulesByDependencyId(
            Long dependencyId,
            LocalDate startDate,
            LocalDate endDate,
            java.time.LocalTime startTime,
            Long shiftId
    ) {
        try {
            return scheduleQueryService.getSchedulesByDependencyId(dependencyId, startDate, endDate, startTime, shiftId);

        } catch (Exception e) {
            System.err.println("Error en getSchedulesByDependencyId: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


    public List<EmployeeScheduleDTO> getSchedulesByShiftId(Long shiftId) {
        return scheduleQueryService.getSchedulesByShiftId(shiftId);
    }

    public List<EmployeeScheduleDTO> getSchedulesByDateRange(Date startDate, Date endDate) {
        return scheduleQueryService.getSchedulesByDateRange(startDate, endDate);
    }

    // =================== CÁLCULOS (DELEGADOS) ===================
    public EmployeeHoursSummaryDTO calculateEmployeeHoursSummary(Long employeeId) {  // ← CAMBIO AQUÍ
        return scheduleCalculationService.calculateEmployeeHoursSummary(employeeId);
    }


    @Transactional
    public void cleanupEmptyDaysForEmployee(Long employeeId) {
        scheduleCalculationService.cleanupEmptyDaysForEmployee(employeeId);
    }

    // =================== TIME BLOCKS (DELEGADOS) ===================

    public EmployeeScheduleTimeBlock updateTimeBlock(TimeBlockDTO timeBlockDTO) {
        return timeBlockService.updateTimeBlock(timeBlockDTO);
    }

    // =================== CRUD BÁSICO ===================

    @Transactional
    public void deleteEmployeeSchedule(Long id) {
        if (!employeeScheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("EmployeeSchedule not found with id: " + id);
        }
        employeeScheduleRepository.deleteById(id);
    }

    @Transactional
    public EmployeeSchedule updateEmployeeSchedule(Long id, EmployeeSchedule schedule) {
        EmployeeSchedule existing = employeeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));

        validateSchedule(schedule);

        existing.setEmployeeId(schedule.getEmployeeId());
        if (schedule.getShift() != null) existing.setShift(schedule.getShift());
        existing.setStartDate(schedule.getStartDate());
        existing.setEndDate(schedule.getEndDate());
        existing.setUpdatedAt(new Date());

        return employeeScheduleRepository.save(existing);
    }

    @Transactional
    public List<EmployeeSchedule> createMultipleSchedules(List<EmployeeSchedule> schedules) {
        return scheduleAssignmentService.createMultipleSchedules(schedules);
    }


    private void validateSchedule(EmployeeSchedule schedule) {
        if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0)
            throw new IllegalArgumentException("Employee ID es obligatorio y debe ser un número válido.");
        if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0)
            throw new IllegalArgumentException("Shift ID es obligatorio y debe ser un número válido.");
        if (schedule.getStartDate() == null)
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        if (schedule.getEndDate() != null && schedule.getStartDate().isAfter(schedule.getEndDate()))
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
    }


    private boolean isSpecialHourType(String type) {
        return type.startsWith("EXTRA_") ||
                type.startsWith("FESTIVO_") ||
                type.startsWith("DOMINICAL_") ||
                type.equals("EXEMPT") ||
                // NO incluir tipos REGULAR_
                (!type.startsWith("REGULAR_") && !type.equals("REGULAR"));
    }

    // Métodos helper en el servicio
    private double calculateHours(String startTime, String endTime) {
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

    public void testDiagnostic(Long employeeId) {
        List<EmployeeScheduleDTO> schedules = getCompleteSchedulesByEmployeeId(employeeId);

        for (EmployeeScheduleDTO schedule : schedules) {
            EmployeeSchedule scheduleEntity = employeeScheduleRepository.findById(schedule.getId()).orElse(null);
            if (scheduleEntity == null) {
                continue;
            }
            Map<String, BigDecimal> fullClassification = hourClassificationService.classifyScheduleHours(Arrays.asList(scheduleEntity));
            if (fullClassification.isEmpty()) {
            } else {
                fullClassification.forEach((type, hours) -> {
                    if (hours.compareTo(BigDecimal.ZERO) > 0) {
                    }
                });
            }
            LocalDate testDate = LocalDate.of(2025, 9, 7); // Domingo
            Map<String, BigDecimal> dayClassification = hourClassificationService.classifyDayHours(scheduleEntity, testDate);
            if (dayClassification.isEmpty()) {

            } else {
                dayClassification.forEach((type, hours) ->
                        System.out.println("  " + type + ": " + hours + "h"));
            }
            boolean hasSpecial = hourClassificationService.hasSpecialHours(dayClassification);
        }

    }
    public Map<String, Object> getDailyBreakdown(Long employeeId) {
        try {
            // 1. Obtener resumen consolidado
            EmployeeHoursSummaryDTO summary = calculateEmployeeHoursSummary(employeeId);

            // 2. Obtener schedules
            List<EmployeeScheduleDTO> schedules = getCompleteSchedulesByEmployeeId(employeeId);

            // 3. NUEVA ESTRATEGIA: Clasificar todos los schedules juntos
            List<EmployeeSchedule> allScheduleEntities = schedules.stream()
                    .map(dto -> employeeScheduleRepository.findById(dto.getId()).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Obtener clasificación completa de TODOS los schedules juntos
            Map<String, BigDecimal> completeClassification = hourClassificationService.classifyScheduleHours(allScheduleEntities);

            completeClassification.forEach((type, hours) -> {
                if (hours.compareTo(BigDecimal.ZERO) > 0) {
                    System.out.println("  " + type + ": " + hours + "h");
                }
            });

            // 4. Identificar días que contribuyen a las horas especiales
            List<Map<String, Object>> dailyDetails = new ArrayList<>();

            // Solo buscar tipos especiales del resultado completo
            Map<String, BigDecimal> specialHours = completeClassification.entrySet().stream()
                    .filter(entry -> isSpecialHourType(entry.getKey()) && entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (!specialHours.isEmpty()) {
                // LÓGICA CORREGIDA: Encontrar días que tienen horas especiales REALES
                for (EmployeeScheduleDTO schedule : schedules) {
                    Map<String, Object> days = (Map<String, Object>) schedule.getDays();
                    List<Map<String, Object>> dayItems = (List<Map<String, Object>>) days.get("items");

                    for (Map<String, Object> day : dayItems) {
                        String dateStr = (String) day.get("date");
                        LocalDate date = LocalDate.parse(dateStr);

                        // Verificar si este día tiene bloques de tiempo
                        List<Map<String, Object>> timeBlocks = (List<Map<String, Object>>) day.get("timeBlocks");
                        if (timeBlocks != null && !timeBlocks.isEmpty()) {

                            // Calcular horas del día y verificar si hay horas especiales REALES
                            double totalDayHours = 0.0;
                            List<Map<String, Object>> blockDetails = new ArrayList<>();
                            boolean hasDaySpecialHours = false;  // Flag para horas especiales reales
                            String dayType = "REGULAR";

                            for (Map<String, Object> block : timeBlocks) {
                                String startTime = (String) block.get("startTime");
                                String endTime = (String) block.get("endTime");
                                double blockHours = calculateHours(startTime, endTime);
                                totalDayHours += blockHours;

                                // VERIFICAR si este bloque específico tiene horas especiales
                                boolean isBlockSpecial = isTimeBlockSpecial(startTime, endTime, date, employeeId);

                                if (isBlockSpecial) {
                                    hasDaySpecialHours = true;
                                    // Determinar el tipo específico del bloque especial
                                    dayType = determineBlockType(startTime, endTime, date, employeeId);
                                }

                                Map<String, Object> blockDetail = new HashMap<>();
                                blockDetail.put("startTime", startTime.substring(0, 5));
                                blockDetail.put("endTime", endTime.substring(0, 5));
                                blockDetail.put("hours", blockHours);
                                blockDetails.add(blockDetail);
                            }

                            // FILTRO CRÍTICO: Solo agregar si el día REALMENTE tiene horas especiales
                            if (hasDaySpecialHours && totalDayHours > 0) {
                                Map<String, Object> dayDetail = new HashMap<>();
                                dayDetail.put("date", dateStr);
                                dayDetail.put("dayOfWeek", date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")));
                                dayDetail.put("shiftName", schedule.getShiftName());
                                dayDetail.put("hourType", dayType);
                                dayDetail.put("totalHours", totalDayHours);
                                dayDetail.put("timeBlocks", blockDetails);

                                // Información adicional
                                if (holidayService.isHoliday(date)) {
                                    dayDetail.put("isHoliday", true);
                                    dayDetail.put("holidayName", holidayService.getHolidayName(date));
                                }

                                if (holidayExemptionService.hasExemption(employeeId, date)) {
                                    dayDetail.put("hasExemption", true);
                                    dayDetail.put("exemptionReason", holidayExemptionService.getExemptionReason(employeeId, date));
                                }

                                dailyDetails.add(dayDetail);
                            } else {
                            }
                        }
                    }
                }
            }

            // Respuesta completa
            Map<String, Object> response = new HashMap<>();
            response.put("employeeId", employeeId);
            response.put("employeeName", employeeDataService.getEmployeeName(employeeId));
            response.put("totalHours", summary.getTotalHours());
            response.put("regularHours", summary.getRegularHours());
            response.put("overtimeHours", summary.getOvertimeHours());
            response.put("festivoHours", summary.getFestivoHours());
            response.put("overtimeType", summary.getOvertimeType());
            response.put("festivoType", summary.getFestivoType());
            response.put("dailyDetails", dailyDetails);

            return response;

        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo breakdown diario", e);
        }
    }



    public Map<String, Object> getTurnBreakdown(Long employeeId, Long groupId) {
        try {
            // Obtener grupo específico
            ScheduleAssignmentGroupDTO group = groupService.getGroupById(groupId);

            if (group == null) {
                return new HashMap<>();
            }

            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("groupId", groupId);
            breakdown.put("shiftName", group.getShiftName());
            breakdown.put("regularHours", group.getRegularHours());
            breakdown.put("overtimeHours", group.getOvertimeHours());
            breakdown.put("festivoHours", group.getFestivoHours());
            breakdown.put("totalHours", group.getTotalHours());
            breakdown.put("overtimeBreakdown", group.getOvertimeBreakdown());

            return breakdown;

        } catch (Exception e) {
            System.err.println("Error obteniendo breakdown de turno: " + e.getMessage());
            return new HashMap<>();
        }
    }



    public Map<String, Object> getDailyBreakdownFiltered(Long employeeId) {
        try {
            // Obtener resumen consolidado
            EmployeeHoursSummaryDTO summary = calculateEmployeeHoursSummary(employeeId);

            // Obtener schedules
            List<EmployeeScheduleDTO> schedules = getCompleteSchedulesByEmployeeId(employeeId);

            // Clasificar todos los schedules juntos
            List<EmployeeSchedule> allScheduleEntities = schedules.stream()
                    .map(dto -> employeeScheduleRepository.findById(dto.getId()).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Map<String, BigDecimal> completeClassification = hourClassificationService.classifyScheduleHours(allScheduleEntities);

            List<Map<String, Object>> dailyDetails = new ArrayList<>();

            // NUEVO: Solo procesar días que REALMENTE tienen timeBlocks válidos
            for (EmployeeScheduleDTO schedule : schedules) {
                Map<String, Object> days = (Map<String, Object>) schedule.getDays();
                List<Map<String, Object>> dayItems = (List<Map<String, Object>>) days.get("items");

                for (Map<String, Object> day : dayItems) {
                    String dateStr = (String) day.get("date");
                    LocalDate date = LocalDate.parse(dateStr);

                    // FILTRO 1: Solo días con timeBlocks válidos
                    List<Map<String, Object>> timeBlocks = (List<Map<String, Object>>) day.get("timeBlocks");
                    if (timeBlocks == null || timeBlocks.isEmpty()) {
                        continue; // Saltar días sin horarios
                    }

                    // FILTRO 2: Calcular horas reales del día
                    double totalDayHours = 0.0;
                    List<Map<String, Object>> blockDetails = new ArrayList<>();

                    for (Map<String, Object> block : timeBlocks) {
                        String startTime = (String) block.get("startTime");
                        String endTime = (String) block.get("endTime");
                        double blockHours = calculateHours(startTime, endTime);
                        totalDayHours += blockHours;

                        Map<String, Object> blockDetail = new HashMap<>();
                        blockDetail.put("startTime", startTime.substring(0, 5));
                        blockDetail.put("endTime", endTime.substring(0, 5));
                        blockDetail.put("hours", blockHours);
                        blockDetails.add(blockDetail);
                    }

                    // FILTRO 3: Solo incluir si tiene horas > 0
                    if (totalDayHours > 0) {
                        // Determinar tipo de día
                        String dayType = determineBlockType(
                                timeBlocks.get(0).get("startTime").toString(),
                                timeBlocks.get(0).get("endTime").toString(),
                                date,
                                employeeId
                        );

                        Map<String, Object> dayDetail = new HashMap<>();
                        dayDetail.put("date", dateStr);
                        dayDetail.put("dayOfWeek", date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")));
                        dayDetail.put("shiftName", schedule.getShiftName());
                        dayDetail.put("hourType", dayType);
                        dayDetail.put("totalHours", totalDayHours);
                        dayDetail.put("timeBlocks", blockDetails);

                        // Información adicional
                        if (holidayService.isHoliday(date)) {
                            dayDetail.put("isHoliday", true);
                            dayDetail.put("holidayName", holidayService.getHolidayName(date));
                        }

                        dailyDetails.add(dayDetail);
                    }
                }
            }

            // Respuesta completa
            Map<String, Object> response = new HashMap<>();
            response.put("employeeId", employeeId);
            response.put("employeeName", employeeDataService.getEmployeeName(employeeId));
            response.put("totalHours", summary.getTotalHours());
            response.put("regularHours", summary.getRegularHours());
            response.put("overtimeHours", summary.getOvertimeHours());
            response.put("festivoHours", summary.getFestivoHours());
            response.put("overtimeType", summary.getOvertimeType());
            response.put("festivoType", summary.getFestivoType());
            response.put("dailyDetails", dailyDetails);

            return response;

        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo breakdown diario filtrado", e);
        }
    }

        private boolean isTimeBlockSpecial(String startTime, String endTime, LocalDate date, Long employeeId) {

        // 1. Verificar si es festivo
        if (holidayService.isHoliday(date)) {
            return true;
        }

        // 2. Verificar si tiene exención
        if (holidayExemptionService.hasExemption(employeeId, date)) {
            return true;
        }

        // 3. Verificar si es horario nocturno (19:00 o posterior)
        try {
            int startHour = Integer.parseInt(startTime.split(":")[0]);
            if (startHour >= 19) {
                return true; // Horario nocturno = especial (segundo turno)
            }
        } catch (Exception e) {
            // Si hay error parsing, asumir que no es especial
        }

        // 4. Verificar si es domingo
        if (date.getDayOfWeek().getValue() == 7) {
            return true;
        }

        // 5. Horarios diurnos regulares (06:00-18:59) NO son especiales
        return false;
    }

    /**
     * Determina el tipo específico del bloque especial
     */
    private String determineBlockType(String startTime, String endTime, LocalDate date, Long employeeId) {

        // Verificar exenciones primero
        if (holidayExemptionService.hasExemption(employeeId, date)) {
            return "EXEMPT";
        }

        // Verificar si es festivo
        if (holidayService.isHoliday(date)) {
            // También verificar si el festivo es en horario nocturno
            try {
                int startHour = Integer.parseInt(startTime.split(":")[0]);
                int endHour = Integer.parseInt(endTime.split(":")[0]);

                // Si empieza a las 19:00 o después, O termina a las 19:00 o después
                if (startHour >= 19 || endHour >= 19) {
                    return "FESTIVO_NOCTURNA";
                }
            } catch (Exception e) {
                // Error parsing, asumir diurno
            }
            return "FESTIVO_DIURNA";
        }

        // Verificar si es horario nocturno (19:00 a 23:59)
        try {
            int startHour = Integer.parseInt(startTime.split(":")[0]);
            int endHour = Integer.parseInt(endTime.split(":")[0]);

            // Si empieza a las 19:00 o después, O termina a las 19:00 o después
            if (startHour >= 19 || endHour >= 19) {
                return "EXTRA_NOCTURNA";
            }
        } catch (Exception e) {
            // Error parsing
        }

        // Verificar si es domingo (para horarios diurnos en domingo)
        if (date.getDayOfWeek().getValue() == 7) {
            return "DOMINICAL_DIURNA";
        }

        return "REGULAR_DIURNA"; // Por defecto
    }
}
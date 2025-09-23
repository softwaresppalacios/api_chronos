package sp.sistemaspalacios.api_chronos.service.employeeSchedule.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeScheduleDTO;
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
            HourClassificationService hourClassificationService
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
    }

    // =================== ASIGNACIONES (DELEGADAS) ===================


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
            System.out.println("Buscando schedules con parámetros:");
            System.out.println("- dependencyId: " + (dependencyId != null ? dependencyId : "TODAS"));
            System.out.println("- startDate: " + startDate);
            System.out.println("- endDate: " + endDate);
            System.out.println("- startTime: " + startTime);
            System.out.println("- shiftId: " + shiftId);

            // Delegar al ScheduleQueryService
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





    private String determineDayTypeFromCompleteResult(LocalDate date, Map<String, BigDecimal> specialHours, Long employeeId) {

        // Verificar exenciones
        if (holidayExemptionService.hasExemption(employeeId, date)) {
            return "EXEMPT";
        }

        int dayOfWeek = date.getDayOfWeek().getValue();
        boolean isHoliday = holidayService.isHoliday(date);
        boolean isSunday = (dayOfWeek == 7);

        // DEVOLVER EL CÓDIGO REAL DE LA BASE DE DATOS, no tipos simplificados

        // Verificar festivos + dominicales
        if (isHoliday && isSunday) {
            // Buscar FESTIVO_DOMINICAL_DIURNA o FESTIVO_DOMINICAL_NOCTURNA
            return specialHours.keySet().stream()
                    .filter(type -> type.startsWith("FESTIVO_DOMINICAL_"))
                    .findFirst()
                    .orElse("REGULAR");
        }

        // Verificar solo festivos
        if (isHoliday) {
            // Buscar FESTIVO_DIURNA, FESTIVO_NOCTURNA, EXTRA_FESTIVO_DIURNA, etc.
            return specialHours.keySet().stream()
                    .filter(type -> type.contains("FESTIVO") && !type.contains("DOMINICAL"))
                    .findFirst()
                    .orElse("REGULAR");
        }

        // Verificar solo domingos
        if (isSunday) {
            // Buscar DOMINICAL_DIURNA, DOMINICAL_NOCTURNA, EXTRA_DOMINICAL_DIURNA, etc.
            return specialHours.keySet().stream()
                    .filter(type -> type.contains("DOMINICAL") && !type.contains("FESTIVO"))
                    .findFirst()
                    .orElse("REGULAR");
        }

        // Verificar horas extras regulares
        if (specialHours.keySet().stream().anyMatch(type -> type.startsWith("EXTRA_") && !type.contains("FESTIVO") && !type.contains("DOMINICAL"))) {
            // Buscar EXTRA_DIURNA, EXTRA_NOCTURNA
            return specialHours.keySet().stream()
                    .filter(type -> type.startsWith("EXTRA_") && !type.contains("FESTIVO") && !type.contains("DOMINICAL"))
                    .findFirst()
                    .orElse("REGULAR");
        }

        return "REGULAR";
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
        System.out.println("=== DIAGNÓSTICO SIMPLE ===");

        List<EmployeeScheduleDTO> schedules = getCompleteSchedulesByEmployeeId(employeeId);
        System.out.println("Total schedules encontrados: " + schedules.size());

        for (EmployeeScheduleDTO schedule : schedules) {
            System.out.println("\n*** SCHEDULE ID: " + schedule.getId() + " ***");

            EmployeeSchedule scheduleEntity = employeeScheduleRepository.findById(schedule.getId()).orElse(null);
            if (scheduleEntity == null) {
                System.out.println("ERROR: No se pudo cargar schedule entity");
                continue;
            }

            // 2. Clasificación completa
            Map<String, BigDecimal> fullClassification = hourClassificationService.classifyScheduleHours(Arrays.asList(scheduleEntity));
            System.out.println("CLASIFICACIÓN COMPLETA:");
            if (fullClassification.isEmpty()) {
                System.out.println("  (Vacía)");
            } else {
                fullClassification.forEach((type, hours) -> {
                    if (hours.compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println("  " + type + ": " + hours + "h");
                    }
                });
            }

            // 3. Probar solo un día como muestra
            LocalDate testDate = LocalDate.of(2025, 9, 7); // Domingo
            Map<String, BigDecimal> dayClassification = hourClassificationService.classifyDayHours(scheduleEntity, testDate);
            System.out.println("DÍA " + testDate + " (DOMINGO):");
            if (dayClassification.isEmpty()) {
                System.out.println("  (Vacía)");
            } else {
                dayClassification.forEach((type, hours) ->
                        System.out.println("  " + type + ": " + hours + "h"));
            }
            boolean hasSpecial = hourClassificationService.hasSpecialHours(dayClassification);
            System.out.println("  ¿Especiales? " + hasSpecial);

            System.out.println("*** FIN SCHEDULE " + schedule.getId() + " ***\n");
        }

        System.out.println("=== FIN DIAGNÓSTICO ===");
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

            System.out.println("CLASIFICACIÓN COMPLETA DE TODOS LOS SCHEDULES:");
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
                                System.out.println("✅ Día agregado al reporte: " + dateStr + " - Tipo: " + dayType);
                            } else {
                                System.out.println("🔇 Día FILTRADO (solo horas regulares): " + dateStr);
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

// AGREGAR ESTOS MÉTODOS AUXILIARES AL FINAL DE LA CLASE EmployeeScheduleService

    /**
     * Determina si un bloque de tiempo específico contiene horas especiales
     */
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
            return "FESTIVO_DIURNA";
        }

        // Verificar si es horario nocturno (19:00+)
        try {
            int startHour = Integer.parseInt(startTime.split(":")[0]);
            if (startHour >= 19) {
                return "EXTRA_NOCTURNA"; // Segundo turno nocturno
            }
        } catch (Exception e) {
            // Error parsing
        }

        // Verificar si es domingo (para horarios diurnos en domingo)
        if (date.getDayOfWeek().getValue() == 7) {
            return "DOMINICAL_DIURNA";
        }

        return "REGULAR_DIURNA"; // Por defecto (no debería llegar aquí)
    }
}
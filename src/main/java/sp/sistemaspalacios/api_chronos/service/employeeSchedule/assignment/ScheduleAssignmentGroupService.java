package sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.overtime.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDetailDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.ScheduleAssignmentGroup;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.ScheduleAssignmentGroupRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.overtime.HourClassificationService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.overtime.OvertimeTypeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAssignmentGroupService {

    private final ScheduleAssignmentGroupRepository groupRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final OvertimeTypeService overtimeTypeService;
    private final HourClassificationService hourClassificationService;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    // ===== MÉTODOS PÚBLICOS PRINCIPALES =====

    @Transactional
    public ScheduleAssignmentGroupDTO processScheduleAssignment(Long employeeId, List<Long> scheduleIds) {
        // 1. Validar inputs
        if (employeeId == null) throw new IllegalArgumentException("Employee ID no puede ser nulo");
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new IllegalArgumentException("Lista de schedule IDs no puede estar vacía");
        }

        // 2. Obtener schedules
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(scheduleIds);
        if (schedules.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron schedules con los IDs proporcionados");
        }

        // 3. Calcular período directamente
        Date startDate = schedules.stream()
                .map(EmployeeSchedule::getStartDate)
                .filter(Objects::nonNull)
                .min(Date::compareTo)
                .orElse(new Date());

        Date endDate = schedules.stream()
                .map(s -> s.getEndDate() != null ? s.getEndDate() : s.getStartDate())
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(new Date());

        // 4. Encontrar o crear grupo
        ScheduleAssignmentGroup group = findOrCreateGroupSimple(employeeId, scheduleIds, startDate, endDate);

        // 5. Calcular horas directamente
        List<EmployeeSchedule> allSchedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        Map<String, BigDecimal> hoursByType = hourClassificationService.classifyScheduleHours(allSchedules);

        // 6. Actualizar totales del grupo directamente
        updateGroupTotalsSimple(group, hoursByType);
        syncStatusWithDates(group);
        group = groupRepository.save(group);

        return convertToDTO(group, schedules, hoursByType);
    }




    public List<ScheduleAssignmentGroupDTO> getEmployeeGroups(Long employeeId) {
        List<ScheduleAssignmentGroup> groups = groupRepository.findByEmployeeId(employeeId);
        return groups.stream()
                .peek(this::syncStatusWithDates)
                .map(this::convertGroupToDTO)
                .collect(Collectors.toList());
    }


    @Transactional
    public ScheduleAssignmentGroupDTO getGroupById(Long groupId) {
        ScheduleAssignmentGroup group = getGroupOrThrow(groupId);
        syncStatusWithDates(group);

        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        Map<String, BigDecimal> hoursByType = hourClassificationService.classifyScheduleHours(schedules);

        return convertToDTO(group, schedules, hoursByType);
    }

    @Transactional
    public ScheduleAssignmentGroupDTO recalculateGroup(Long groupId) {
        ScheduleAssignmentGroup group = getGroupOrThrow(groupId);
        List<EmployeeSchedule> schedules = scheduleRepository.findAllByIdWithShift(group.getEmployeeScheduleIds());

        Map<String, BigDecimal> hoursByType = hourClassificationService.classifyScheduleHours(schedules);
        updateGroupTotalsSimple(group, hoursByType);
        syncStatusWithDates(group);
        group = groupRepository.save(group);

        return convertToDTO(group, schedules, hoursByType);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("Grupo no encontrado con ID: " + groupId);
        }
        groupRepository.deleteById(groupId);
    }

    // REEMPLAZAR el método getAllScheduleGroupsWithFilters en ScheduleAssignmentGroupService

    public List<ScheduleAssignmentGroupDTO> getAllScheduleGroupsWithFilters(
            String status, String shiftName, Long employeeId,
            LocalDate startDate, LocalDate endDate) {

        // 🔹 NO SINCRONIZAR TODOS - solo filtrar por status actual
        List<ScheduleAssignmentGroup> allGroups = groupRepository.findAll();

        // 🔹 FILTRADO RÁPIDO SIN CÁLCULOS PESADOS
        List<ScheduleAssignmentGroup> filteredGroups = allGroups.stream()
                .filter(group -> filterByStatusFast(group, status))
                .filter(group -> filterByEmployee(group, employeeId))
                .filter(group -> filterByDateRange(group, startDate, endDate))
                .collect(Collectors.toList());

        // 🔹 LIMITAR RESULTADOS PARA EVITAR SOBRECARGA
        if (filteredGroups.size() > 100) {
            filteredGroups = filteredGroups.stream()
                    .limit(100)
                    .collect(Collectors.toList());
        }

        // 🔹 PROCESAMIENTO OPTIMIZADO
        return filteredGroups.stream()
                .map(group -> {
                    try {
                        return convertToFastDTO(group, shiftName);
                    } catch (Exception e) {
                        log.error("Error procesando grupo {}: {}", group.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 🔹 MÉTODO RÁPIDO PARA DETERMINAR STATUS SIN ACTUALIZAR BD
    private boolean filterByStatusFast(ScheduleAssignmentGroup group, String status) {
        if (status == null || status.trim().isEmpty() || "TODOS".equalsIgnoreCase(status)) {
            return true;
        }

        // Calcular status efectivo SIN guardar en BD
        String effectiveStatus = calculateEffectiveStatus(group);
        return status.equalsIgnoreCase(effectiveStatus);
    }

    private String calculateEffectiveStatus(ScheduleAssignmentGroup group) {
        if (group.getPeriodEnd() == null) return "ACTIVE";

        LocalDate today = LocalDate.now();
        LocalDate endDate = convertToLocalDate(group.getPeriodEnd());

        return today.isAfter(endDate) ? "INACTIVE" : "ACTIVE";
    }

    // 🔹 CONVERSIÓN RÁPIDA SIN CÁLCULOS DE HORAS COMPLEJOS
    private ScheduleAssignmentGroupDTO convertToFastDTO(ScheduleAssignmentGroup group, String shiftNameFilter) {
        // Usar datos existentes del grupo (pueden estar un poco desactualizados pero son rápidos)
        ScheduleAssignmentGroupDTO dto = new ScheduleAssignmentGroupDTO();

        dto.setId(group.getId());
        dto.setEmployeeId(group.getEmployeeId());
        dto.setPeriodStart(dateFormat.format(group.getPeriodStart()));
        dto.setPeriodEnd(dateFormat.format(group.getPeriodEnd()));
        dto.setEmployeeScheduleIds(group.getEmployeeScheduleIds());
        dto.setStatus(calculateEffectiveStatus(group));

        // Usar totales ya calculados de la BD (rápido)
        dto.setTotalHours(group.getTotalHours() != null ? group.getTotalHours() : BigDecimal.ZERO);
        dto.setRegularHours(group.getRegularHours() != null ? group.getRegularHours() : BigDecimal.ZERO);
        dto.setOvertimeHours(group.getOvertimeHours() != null ? group.getOvertimeHours() : BigDecimal.ZERO);
        dto.setFestivoHours(group.getFestivoHours() != null ? group.getFestivoHours() : BigDecimal.ZERO);
        dto.setAssignedHours(dto.getRegularHours().add(dto.getFestivoHours()));

        dto.setOvertimeType(group.getOvertimeType());
        dto.setFestivoType(group.getFestivoType());

        // Crear breakdown básico
        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("total_diurna", dto.getRegularHours().doubleValue());
        breakdown.put("total_nocturna", dto.getOvertimeHours().doubleValue());
        dto.setOvertimeBreakdown(breakdown);

        // AGREGAR NOMBRE DEL TURNO
        List<EmployeeSchedule> schedules = scheduleRepository.findAllByIdWithShift(group.getEmployeeScheduleIds());
        if (!schedules.isEmpty() && schedules.get(0).getShift() != null) {
            String shiftName = getShiftDisplayName(schedules.get(0).getShift());
            dto.setShiftName(shiftName != null ? shiftName : "Sin turno");
        } else {
            dto.setShiftName("Sin turno");
        }

        // SOLO cargar schedule details si realmente se necesitan (para filtro de turno)
        if (needsScheduleDetails(shiftNameFilter)) {
            // Aplicar filtro de turno
            if (shiftNameFilter != null && !shiftNameFilter.trim().isEmpty() && !"TODOS".equalsIgnoreCase(shiftNameFilter)) {
                schedules = schedules.stream()
                        .filter(s -> matchesShiftName(s, shiftNameFilter.trim()))
                        .collect(Collectors.toList());

                if (schedules.isEmpty()) return null; // Filtrar este grupo
            }

            // Crear detalles básicos
            List<ScheduleDetailDTO> details = schedules.stream()
                    .map(this::createBasicScheduleDetail)
                    .collect(Collectors.toList());
            dto.setScheduleDetails(details);
        } else {
            dto.setScheduleDetails(Collections.emptyList());
        }

        return dto;
    }
    private boolean needsScheduleDetails(String shiftNameFilter) {
        return shiftNameFilter != null && !shiftNameFilter.trim().isEmpty() && !"TODOS".equalsIgnoreCase(shiftNameFilter);
    }

    // 🔹 CREACIÓN BÁSICA DE DETALLES SIN CÁLCULOS COMPLEJOS
    private ScheduleDetailDTO createBasicScheduleDetail(EmployeeSchedule schedule) {
        ScheduleDetailDTO detail = new ScheduleDetailDTO();

        detail.setScheduleId(schedule.getId());
        detail.setStartDate(dateFormat.format(schedule.getStartDate()));
        detail.setEndDate(dateFormat.format(schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate()));

        if (schedule.getShift() != null) {
            detail.setShiftId(schedule.getShift().getId());
            String name = getShiftDisplayName(schedule.getShift());
            detail.setShiftName((name != null && !name.isBlank()) ? name : "Turno #" + schedule.getShift().getId());
        } else {
            detail.setShiftName("Sin turno");
        }

        // Valores por defecto - se calcularán bajo demanda si es necesario
        detail.setHoursInPeriod(0.0);
        detail.setRegularHours(0.0);
        detail.setOvertimeHours(0.0);
        detail.setFestivoHours(0.0);
        detail.setOvertimeType(null);
        detail.setFestivoType(null);
        detail.setOvertimeBreakdown(new HashMap<>());

        return detail;
    }

    public List<Map<String, String>> getAvailableStatuses() {
        List<String> uniqueStatuses = groupRepository.findAll().stream()
                .map(this::getEffectiveStatus)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<Map<String, String>> statusOptions = new ArrayList<>();
        statusOptions.add(Map.of("label", "Todos", "value", "TODOS"));

        Map<String, String> statusLabels = Map.of(
                "ACTIVE", "Activos",
                "INACTIVE", "Inactivos"
        );

        uniqueStatuses.forEach(status -> {
            statusOptions.add(Map.of(
                    "label", statusLabels.getOrDefault(status, status),
                    "value", status
            ));
        });

        return statusOptions;
    }

    public ScheduleDetailDTO createScheduleDetailWithCalculation(EmployeeSchedule schedule) {
        ScheduleDetailDTO detail = new ScheduleDetailDTO();

        detail.setScheduleId(schedule.getId());
        detail.setStartDate(dateFormat.format(schedule.getStartDate()));
        detail.setEndDate(dateFormat.format(schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate()));

        // Configurar información del turno
        if (schedule.getShift() != null) {
            detail.setShiftId(schedule.getShift().getId());
            String name = getShiftDisplayName(schedule.getShift());
            detail.setShiftName((name != null && !name.isBlank()) ? name : "Turno #" + schedule.getShift().getId());
        } else {
            detail.setShiftName("Sin turno");
        }

        // Calcular horas para este schedule individual
        Map<String, BigDecimal> hoursByType = hourClassificationService.classifyScheduleHours(Collections.singletonList(schedule));

        BigDecimal regularHours = sumHoursByPrefix(hoursByType, "REGULAR_");
        BigDecimal overtimeHours = sumHoursByPrefix(hoursByType, "EXTRA_").add(sumHoursByPrefix(hoursByType, "DOMINICAL_"));
        BigDecimal festivoHours = sumHoursByPrefix(hoursByType, "FESTIVO_");
        BigDecimal totalHours = regularHours.add(overtimeHours);

        detail.setHoursInPeriod(totalHours.doubleValue());
        detail.setRegularHours(regularHours.doubleValue());
        detail.setOvertimeHours(overtimeHours.doubleValue());
        detail.setFestivoHours(festivoHours.doubleValue());
        detail.setOvertimeType(findPredominantType(hoursByType, Arrays.asList("EXTRA_", "DOMINICAL_")));
        detail.setFestivoType(findPredominantType(hoursByType, Arrays.asList("FESTIVO_")));
        detail.setOvertimeBreakdown(createBreakdown(hoursByType));

        return detail;
    }

    // ===== MÉTODOS PRIVADOS DE APOYO =====

    private ScheduleAssignmentGroup findOrCreateGroupSimple(Long employeeId, List<Long> scheduleIds, Date startDate, Date endDate) {
        // Buscar grupo existente que se solape con el período
        Optional<ScheduleAssignmentGroup> existing = groupRepository.findByEmployeeId(employeeId).stream()
                .filter(group -> hasDateOverlap(startDate, endDate, group.getPeriodStart(), group.getPeriodEnd()))
                .findFirst();

        if (existing.isPresent()) {
            return updateExistingGroupSimple(existing.get(), scheduleIds, startDate, endDate);
        } else {
            return createNewGroupSimple(employeeId, scheduleIds, startDate, endDate);
        }
    }

    private boolean hasDateOverlap(Date start1, Date end1, Date start2, Date end2) {
        LocalDate s1 = convertToLocalDate(start1);
        LocalDate e1 = convertToLocalDate(end1);
        LocalDate s2 = convertToLocalDate(start2);
        LocalDate e2 = convertToLocalDate(end2);

        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }

    private ScheduleAssignmentGroup updateExistingGroupSimple(ScheduleAssignmentGroup group, List<Long> scheduleIds, Date startDate, Date endDate) {
        // Agregar nuevos schedule IDs
        scheduleIds.forEach(id -> {
            if (!group.getEmployeeScheduleIds().contains(id)) {
                group.getEmployeeScheduleIds().add(id);
            }
        });

        // Expandir rango de fechas si es necesario
        LocalDate groupStart = convertToLocalDate(group.getPeriodStart());
        LocalDate groupEnd = convertToLocalDate(group.getPeriodEnd());
        LocalDate newStart = convertToLocalDate(startDate);
        LocalDate newEnd = convertToLocalDate(endDate);

        if (newStart.isBefore(groupStart)) {
            group.setPeriodStart(startDate);
        }
        if (newEnd.isAfter(groupEnd)) {
            group.setPeriodEnd(endDate);
        }

        return group;
    }

    private ScheduleAssignmentGroup createNewGroupSimple(Long employeeId, List<Long> scheduleIds, Date startDate, Date endDate) {
        ScheduleAssignmentGroup group = new ScheduleAssignmentGroup();
        group.setEmployeeId(employeeId);
        group.setPeriodStart(startDate);
        group.setPeriodEnd(endDate);
        group.setEmployeeScheduleIds(new ArrayList<>(scheduleIds));
        return group;
    }

    private void updateGroupTotalsSimple(ScheduleAssignmentGroup group, Map<String, BigDecimal> hoursByType) {
        BigDecimal regularHours = sumHoursByPrefix(hoursByType, "REGULAR_");
        BigDecimal overtimeHours = sumHoursByPrefix(hoursByType, "EXTRA_").add(sumHoursByPrefix(hoursByType, "DOMINICAL_"));
        BigDecimal festivoHours = sumHoursByPrefix(hoursByType, "FESTIVO_");
        BigDecimal totalHours = regularHours.add(overtimeHours);

        group.setRegularHours(regularHours.setScale(2, RoundingMode.HALF_UP));
        group.setOvertimeHours(overtimeHours.setScale(2, RoundingMode.HALF_UP));
        group.setFestivoHours(festivoHours.setScale(2, RoundingMode.HALF_UP));
        group.setTotalHours(totalHours.setScale(2, RoundingMode.HALF_UP));
        group.setOvertimeType(findPredominantType(hoursByType, Arrays.asList("EXTRA_", "DOMINICAL_")));
        group.setFestivoType(findPredominantType(hoursByType, Arrays.asList("FESTIVO_")));
    }

    private BigDecimal sumHoursByPrefix(Map<String, BigDecimal> hoursByType, String prefix) {
        return hoursByType.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String findPredominantType(Map<String, BigDecimal> hoursByType, List<String> prefixes) {
        Map<String, String> codeToName = overtimeTypeService.getAllActiveTypes().stream()
                .collect(Collectors.toMap(OvertimeTypeDTO::getCode, OvertimeTypeDTO::getDisplayName));

        return hoursByType.entrySet().stream()
                .filter(e -> prefixes.stream().anyMatch(p -> e.getKey().startsWith(p)))
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> codeToName.getOrDefault(e.getKey(), e.getKey()))
                .orElse(null);
    }

    private Map<String, Object> createBreakdown(Map<String, BigDecimal> hoursByType) {
        Map<String, Object> breakdown = new HashMap<>();

        hoursByType.forEach((k, v) -> {
            if (v.compareTo(BigDecimal.ZERO) > 0) {
                breakdown.put(k, v.doubleValue());
            }
        });

        BigDecimal totalDiurna = hoursByType.entrySet().stream()
                .filter(e -> e.getKey().contains("_DIURNA"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNocturna = hoursByType.entrySet().stream()
                .filter(e -> e.getKey().contains("_NOCTURNA"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        breakdown.put("total_diurna", totalDiurna.doubleValue());
        breakdown.put("total_nocturna", totalNocturna.doubleValue());

        return breakdown;
    }

    // ===== CONVERSIÓN A DTO =====

    private ScheduleAssignmentGroupDTO convertGroupToDTO(ScheduleAssignmentGroup group) {
        List<EmployeeSchedule> schedules = scheduleRepository.findAllByIdWithShift(group.getEmployeeScheduleIds());
        Map<String, BigDecimal> hoursByType = hourClassificationService.classifyScheduleHours(schedules);
        return convertToDTO(group, schedules, hoursByType);
    }

    private ScheduleAssignmentGroupDTO convertToDTO(ScheduleAssignmentGroup group, List<EmployeeSchedule> schedules, Map<String, BigDecimal> hoursByType) {
        ScheduleAssignmentGroupDTO dto = new ScheduleAssignmentGroupDTO();

        dto.setId(group.getId());
        dto.setEmployeeId(group.getEmployeeId());
        dto.setPeriodStart(dateFormat.format(group.getPeriodStart()));
        dto.setPeriodEnd(dateFormat.format(group.getPeriodEnd()));
        dto.setEmployeeScheduleIds(group.getEmployeeScheduleIds());
        dto.setStatus(getEffectiveStatus(group));

        // Calcular totales directamente
        BigDecimal regularHours = sumHoursByPrefix(hoursByType, "REGULAR_");
        BigDecimal overtimeHours = sumHoursByPrefix(hoursByType, "EXTRA_").add(sumHoursByPrefix(hoursByType, "DOMINICAL_"));
        BigDecimal festivoHours = sumHoursByPrefix(hoursByType, "FESTIVO_");
        BigDecimal totalHours = regularHours.add(overtimeHours);
        BigDecimal assignedHours = regularHours.add(festivoHours);

        dto.setTotalHours(totalHours.setScale(2, RoundingMode.HALF_UP));
        dto.setRegularHours(regularHours.setScale(2, RoundingMode.HALF_UP));
        dto.setOvertimeHours(overtimeHours.setScale(2, RoundingMode.HALF_UP));
        dto.setFestivoHours(festivoHours.setScale(2, RoundingMode.HALF_UP));
        dto.setAssignedHours(assignedHours.setScale(2, RoundingMode.HALF_UP));
        dto.setOvertimeType(findPredominantType(hoursByType, Arrays.asList("EXTRA_", "DOMINICAL_")));
        dto.setFestivoType(findPredominantType(hoursByType, Arrays.asList("FESTIVO_")));

        dto.setOvertimeBreakdown(createBreakdown(hoursByType));

        List<ScheduleDetailDTO> details = schedules.stream()
                .map(this::createScheduleDetailWithCalculation)
                .collect(Collectors.toList());
        dto.setScheduleDetails(details);

        return dto;
    }


    private boolean filterByEmployee(ScheduleAssignmentGroup group, Long employeeId) {
        return employeeId == null || Objects.equals(group.getEmployeeId(), employeeId);
    }

    private boolean filterByDateRange(ScheduleAssignmentGroup group, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return true;

        LocalDate groupStart = convertToLocalDate(group.getPeriodStart());
        LocalDate groupEnd = convertToLocalDate(group.getPeriodEnd());

        if (startDate != null && endDate == null) {
            return !groupEnd.isBefore(startDate);
        }
        if (startDate == null) {
            return !groupStart.isAfter(endDate);
        }
        return !groupStart.isAfter(endDate) && !groupEnd.isBefore(startDate);
    }

    private boolean matchesShiftName(EmployeeSchedule schedule, String shiftName) {
        String displayName = getShiftDisplayName(schedule.getShift());
        return displayName != null && shiftName.equalsIgnoreCase(displayName.trim());
    }

    // ===== MÉTODOS DE UTILIDAD =====

    private ScheduleAssignmentGroup getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado con ID: " + groupId));
    }

    @Transactional
    private void syncStatusWithDates(ScheduleAssignmentGroup group) {
        String effective = getEffectiveStatus(group);
        if (!Objects.equals(group.getStatus(), effective)) {
            group.setStatus(effective);
            groupRepository.save(group);
        }
    }

    private String getEffectiveStatus(ScheduleAssignmentGroup group) {
        if (group.getPeriodEnd() == null) return "ACTIVE";

        LocalDate today = LocalDate.now();
        LocalDate endDate = convertToLocalDate(group.getPeriodEnd());

        return today.isAfter(endDate) ? "INACTIVE" : "ACTIVE";
    }

    private String getShiftDisplayName(sp.sistemaspalacios.api_chronos.entity.shift.Shifts shift) {
        if (shift == null) return null;
        try {
            String name = shift.getName();
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {}
        return null;
    }

    private LocalDate convertToLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

}
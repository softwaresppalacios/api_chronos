package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.util.*;

@Service
public class ShiftsService {

    private final ShiftsRepository shiftsRepository;
    private final GeneralConfigurationService generalConfigurationService;

    public ShiftsService(ShiftsRepository shiftsRepository,
                         GeneralConfigurationService generalConfigurationService) {
        this.shiftsRepository = shiftsRepository;
        this.generalConfigurationService = generalConfigurationService;
    }

    // ==========================================
    // OPERACIONES CRUD BÁSICAS
    // ==========================================

    public List<Shifts> findAll() {
        return shiftsRepository.findAll();
    }

    public Shifts findById(Long id) {
        return shiftsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno con ID " + id + " no encontrado"));
    }

    public List<Shifts> findByDependencyId(Long dependencyId) {
        validateDependencyId(dependencyId);
        return shiftsRepository.findByDependencyId(dependencyId);
    }

    public Shifts save(Shifts shifts) {
        validateShift(shifts);
        assignShiftToDetails(shifts);
        return shiftsRepository.save(shifts);
    }

    public Shifts updateShift(Long id, Shifts shiftDetails) {
        Shifts existingShift = findById(id);

        validateShift(shiftDetails);
        updateShiftProperties(existingShift, shiftDetails);
        assignShiftToDetails(existingShift);

        return shiftsRepository.save(existingShift);
    }

    public void deleteById(Long id) {
        Shifts shift = findById(id);
        shiftsRepository.delete(shift);
    }

    // ==========================================
    // VERIFICACIÓN DE TURNOS DESACTUALIZADOS
    // ==========================================

    public Map<String, Object> checkOutdatedShifts() {
        try {
            SystemConfiguration currentConfig = loadCurrentSystemConfiguration();
            List<OutdatedShiftInfo> outdatedShifts = findOutdatedShifts(currentConfig);

            return buildOutdatedShiftsResponse(outdatedShifts, currentConfig);

        } catch (Exception e) {
            throw new RuntimeException("Error verificando turnos: " + e.getMessage(), e);
        }
    }

    // ==========================================
    // MÉTODOS PRIVADOS - VALIDACIONES
    // ==========================================

    private void validateShift(Shifts shift) {
        if (shift.getName() == null || shift.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del turno es obligatorio");
        }
        if (shift.getDependencyId() == null) {
            throw new IllegalArgumentException("El ID de dependencia es obligatorio");
        }
    }

    private void validateDependencyId(Long dependencyId) {
        if (dependencyId == null || dependencyId <= 0) {
            throw new IllegalArgumentException("El ID de dependencia debe ser un número válido.");
        }
    }

    // ==========================================
    // MÉTODOS PRIVADOS - OPERACIONES CON DETALLES
    // ==========================================

    private void assignShiftToDetails(Shifts shifts) {
        if (shifts.getShiftDetails() != null) {
            for (ShiftDetail detail : shifts.getShiftDetails()) {
                detail.setShift(shifts);
            }
        }
    }

    private void updateShiftProperties(Shifts existingShift, Shifts newShiftData) {
        existingShift.setName(newShiftData.getName());
        existingShift.setDescription(newShiftData.getDescription());
        existingShift.setDependencyId(newShiftData.getDependencyId());

        if (newShiftData.getShiftDetails() != null) {
            existingShift.setShiftDetails(newShiftData.getShiftDetails());
        }
    }

    // ==========================================
    // MÉTODOS PRIVADOS - VERIFICACIÓN DE TURNOS DESACTUALIZADOS
    // ==========================================

    private SystemConfiguration loadCurrentSystemConfiguration() {
        try {
            String daily = generalConfigurationService.getByType("DAILY_HOURS").getValue();
            int breakMin = Integer.parseInt(generalConfigurationService.getByType("BREAK").getValue());
            String night = generalConfigurationService.getByType("NIGHT_START").getValue();
            String weekly = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();

            return new SystemConfiguration(daily, breakMin, night, weekly);

        } catch (Exception e) {
            throw new RuntimeException("Error cargando configuración del sistema", e);
        }
    }

    private List<OutdatedShiftInfo> findOutdatedShifts(SystemConfiguration currentConfig) {
        List<Object[]> outdatedShiftsData = shiftsRepository.findOutdatedMultipleJornadaShifts(
                currentConfig.daily,
                currentConfig.breakMin,
                currentConfig.night,
                currentConfig.weekly
        );

        List<OutdatedShiftInfo> outdatedList = new ArrayList<>();

        for (Object[] row : outdatedShiftsData) {
            OutdatedShiftInfo shiftInfo = parseOutdatedShiftRow(row);
            outdatedList.add(shiftInfo);
        }

        return outdatedList;
    }

    private OutdatedShiftInfo parseOutdatedShiftRow(Object[] row) {
        Long shiftId = ((Number) row[0]).longValue();
        String name = (String) row[1];
        String description = row[2] != null ? (String) row[2] : "";
        Long dependencyId = row[3] != null ? ((Number) row[3]).longValue() : null;

        return new OutdatedShiftInfo(
                shiftId,
                name,
                description,
                dependencyId,
                "Turno con múltiples jornadas generado con configuración anterior"
        );
    }

    private Map<String, Object> buildOutdatedShiftsResponse(
            List<OutdatedShiftInfo> outdatedShifts,
            SystemConfiguration currentConfig) {

        long totalShifts = shiftsRepository.count();

        List<Map<String, Object>> outdatedList = outdatedShifts.stream()
                .map(this::convertOutdatedShiftToMap)
                .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);

        Map<String, Object> response = new HashMap<>();
        response.put("totalShifts", totalShifts);
        response.put("outdatedCount", outdatedShifts.size());
        response.put("outdatedShifts", outdatedList);
        response.put("systemConfig", buildSystemConfigMap(currentConfig));
        response.put("note", "Solo se marcan como desactualizados los turnos con múltiples jornadas " +
                "que fueron creados con una configuración diferente a la actual");

        return response;
    }

    private Map<String, Object> convertOutdatedShiftToMap(OutdatedShiftInfo shiftInfo) {
        Map<String, Object> shiftMap = new HashMap<>();
        shiftMap.put("id", shiftInfo.id);
        shiftMap.put("name", shiftInfo.name);
        shiftMap.put("description", shiftInfo.description);
        shiftMap.put("dependencyId", shiftInfo.dependencyId != null ? shiftInfo.dependencyId : 0L);
        shiftMap.put("dependencyName", "Dependencia ID: " + (shiftInfo.dependencyId != null ? shiftInfo.dependencyId : "N/A"));
        shiftMap.put("reason", shiftInfo.reason);
        return shiftMap;
    }

    private Map<String, Object> buildSystemConfigMap(SystemConfiguration config) {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("dailyHours", config.daily);
        configMap.put("breakMinutes", config.breakMin);
        configMap.put("nightStart", config.night);
        configMap.put("weeklyHours", config.weekly);
        return configMap;
    }

    // ==========================================
    // CLASES INTERNAS PARA DATOS
    // ==========================================

    private static class SystemConfiguration {
        final String daily;
        final int breakMin;
        final String night;
        final String weekly;

        SystemConfiguration(String daily, int breakMin, String night, String weekly) {
            this.daily = daily;
            this.breakMin = breakMin;
            this.night = night;
            this.weekly = weekly;
        }
    }

    private static class OutdatedShiftInfo {
        final Long id;
        final String name;
        final String description;
        final Long dependencyId;
        final String reason;

        OutdatedShiftInfo(Long id, String name, String description, Long dependencyId, String reason) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.dependencyId = dependencyId;
            this.reason = reason;
        }
    }

    // ==========================================
    // MÉTODO DEPRECATED - MANTENER POR COMPATIBILIDAD
    // ==========================================

    @Deprecated
    public List<Shifts> findByDependencyaId(Long dependencyId) {
        // Método con typo en el nombre, redirigir al correcto
        return findByDependencyId(dependencyId);
    }
}
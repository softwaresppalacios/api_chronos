package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class ShiftsService {

    private final ShiftsRepository shiftsRepository;
    private final GeneralConfigurationService generalConfigurationService; // ✅ AGREGAR

    public ShiftsService(ShiftsRepository shiftsRepository,
                         GeneralConfigurationService generalConfigurationService) { // ✅ AGREGAR parámetro
        this.shiftsRepository = shiftsRepository;
        this.generalConfigurationService = generalConfigurationService; // ✅ AGREGAR
    }


    // 🔹 Obtener todos los turnos
    public List<Shifts> findAll() {
        return shiftsRepository.findAll();
    }

    // 🔹 Obtener un turno por ID
    public Shifts findById(Long id) {
        return shiftsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno con ID " + id + " no encontrado"));
    }

    // 🔹 Obtener turnos por dependencia
    public List<Shifts> findByDependencyId(Long dependencyId) {
        if (dependencyId == null || dependencyId <= 0) {
            throw new IllegalArgumentException("El ID de dependencia debe ser un número válido.");
        }
        return shiftsRepository.findByDependencyId(dependencyId);
    }

    // 🔹 Crear un nuevo turno con validaciones y asignación correcta de ShiftDetails
    public Shifts save(Shifts shifts) {
        validateShift(shifts); // Validaciones

        // 🔸 Asegurar que cada ShiftDetail tenga correctamente asignado el Shift antes de guardar
        if (shifts.getShiftDetails() != null) {
            for (ShiftDetail detail : shifts.getShiftDetails()) {
                detail.setShift(shifts); // Asigna el shift a cada detalle
            }
        }

        return shiftsRepository.save(shifts);
    }

    // 🔹 Actualizar un turno existente
    public Shifts updateShift(Long id, Shifts shiftDetails) {
        Shifts shift = findById(id); // Lanza excepción si no existe

        validateShift(shiftDetails); // Validaciones
        shift.setName(shiftDetails.getName());
        shift.setDescription(shiftDetails.getDescription());
        shift.setDependencyId(shiftDetails.getDependencyId());

        // 🔸 Actualizar detalles del turno si es necesario
        if (shiftDetails.getShiftDetails() != null) {
            for (ShiftDetail detail : shiftDetails.getShiftDetails()) {
                detail.setShift(shift); // Reasignar shift para evitar errores de persistencia
            }
            shift.setShiftDetails(shiftDetails.getShiftDetails());
        }

        return shiftsRepository.save(shift);
    }

    // 🔹 Eliminar un turno por ID
    public void deleteById(Long id) {
        Shifts shift = findById(id); // Lanza excepción si no existe
        shiftsRepository.delete(shift);
    }

    // 🔹 Validaciones de negocio
    private void validateShift(Shifts shift) {
        if (shift.getName() == null || shift.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del turno es obligatorio");
        }
        if (shift.getDependencyId() == null) {
            throw new IllegalArgumentException("El ID de dependencia es obligatorio");
        }
    }



    public List<Shifts> findByDependencyaId(Long dependencyId) {
        return shiftsRepository.findByDependencyId(dependencyId);
    }




    public Map<String, Object> checkOutdatedShifts() {
        try {
            // 1) Cargo la configuración actual del sistema
            String daily = generalConfigurationService.getByType("DAILY_HOURS").getValue();
            int breakMin = Integer.parseInt(generalConfigurationService.getByType("BREAK").getValue());
            String night = generalConfigurationService.getByType("NIGHT_START").getValue();
            String weekly = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();

            // 2) Busco SOLO los turnos con múltiples jornadas cuya configuración
            // NO coincide con la configuración actual
            // Estos son los que fueron creados con una configuración diferente
            List<Object[]> outdatedShifts = shiftsRepository.findOutdatedMultipleJornadaShifts(
                    daily, breakMin, night, weekly
            );

            // 3) Construyo la lista de turnos desactualizados
            List<Map<String, Object>> outdatedList = new ArrayList<>();

            for (Object[] row : outdatedShifts) {
                Long shiftId = ((Number) row[0]).longValue();
                String name = (String) row[1];
                String description = row[2] != null ? (String) row[2] : "";
                Long dependencyId = row[3] != null ? ((Number) row[3]).longValue() : null;

                outdatedList.add(Map.of(
                        "id", shiftId,
                        "name", name,
                        "description", description,
                        "dependencyId", dependencyId != null ? dependencyId : 0L,
                        "dependencyName", "Dependencia ID: " + (dependencyId != null ? dependencyId : "N/A"),
                        "reason", "Turno con múltiples jornadas generado con configuración anterior"
                ));
            }

            // 4) Empaquetar respuesta
            long total = shiftsRepository.count();

            return Map.of(
                    "totalShifts", total,
                    "outdatedCount", outdatedList.size(),
                    "outdatedShifts", outdatedList,
                    "systemConfig", Map.of(
                            "dailyHours", daily,
                            "breakMinutes", breakMin,
                            "nightStart", night,
                            "weeklyHours", weekly
                    ),
                    "note", "Solo se marcan como desactualizados los turnos con múltiples jornadas " +
                            "que fueron creados con una configuración diferente a la actual"
            );

        } catch (Exception e) {
            throw new RuntimeException("Error verificando turnos: " + e.getMessage(), e);
        }
    }
}

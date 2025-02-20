package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;

import java.util.List;

@Service
public class ShiftsService {

    @Autowired
    private ShiftsRepository shiftsRepository;

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
}

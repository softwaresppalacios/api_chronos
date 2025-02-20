package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeShiftDetailRepository;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeShiftDetailService {

    private final EmployeeShiftDetailRepository employeeShiftDetailRepository;
    private final EmployeeScheduleRepository employeeScheduleRepository;

    public EmployeeShiftDetailService(EmployeeShiftDetailRepository employeeShiftDetailRepository,
                                      EmployeeScheduleRepository employeeScheduleRepository) {
        this.employeeShiftDetailRepository = employeeShiftDetailRepository;
        this.employeeScheduleRepository = employeeScheduleRepository;
    }

    // Obtener todos los registros
    public List<EmployeeShiftDetail> getAllShiftDetails() {
        return employeeShiftDetailRepository.findAll();
    }

    // Obtener un registro por ID
    public EmployeeShiftDetail getShiftDetailById(Long id) {
        validateId(id);
        return employeeShiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeShiftDetail no encontrado con id: " + id));
    }

    // Obtener todos los registros de un empleado
    public List<EmployeeShiftDetail> getShiftDetailsByEmployeeScheduleId(Long employeeScheduleId) {
        validateId(employeeScheduleId);
        return employeeShiftDetailRepository.findByEmployeeScheduleId(employeeScheduleId);
    }

    // Crear un nuevo registro para un solo empleado
    @Transactional
    public EmployeeShiftDetail createShiftDetail(EmployeeShiftDetail shiftDetail) {
        validateShiftDetail(shiftDetail);
        shiftDetail.setCreatedAt(new Date());

        // Si es un día exento, eliminar horas de inicio y fin
        if (Boolean.TRUE.equals(shiftDetail.getIsExempt())) {
            shiftDetail.setStartTime(null);
            shiftDetail.setEndTime(null);
        }

        return employeeShiftDetailRepository.save(shiftDetail);
    }

    // Crear turnos para múltiples empleados con validación de rango horario
    @Transactional
    public List<EmployeeShiftDetail> createShiftDetailsForMultipleEmployees(List<Long> employeeScheduleIds, EmployeeShiftDetail shiftDetail, Time referenceStartTime, Time referenceEndTime) {
        if (employeeScheduleIds == null || employeeScheduleIds.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar al menos un EmployeeScheduleId.");
        }

        validateShiftDetail(shiftDetail);

        // Obtener empleados que cumplen con el rango horario
        List<EmployeeShiftDetail> employeesInRange = employeeShiftDetailRepository.findAllById(employeeScheduleIds)
                .stream()
                .filter(e -> e.getStartTime() != null && e.getEndTime() != null)
                .filter(e -> e.getStartTime().compareTo(referenceStartTime) >= 0 && e.getEndTime().compareTo(referenceEndTime) <= 0)
                .collect(Collectors.toList());

        List<EmployeeShiftDetail> savedDetails = new ArrayList<>();
        for (EmployeeShiftDetail employee : employeesInRange) {
            EmployeeShiftDetail newShiftDetail = new EmployeeShiftDetail();
            newShiftDetail.setEmployeeScheduleId(employee.getEmployeeScheduleId());
            newShiftDetail.setDayOfWeek(shiftDetail.getDayOfWeek());
            newShiftDetail.setIsExempt(shiftDetail.getIsExempt());

            if (!Boolean.TRUE.equals(shiftDetail.getIsExempt())) {
                newShiftDetail.setStartTime(shiftDetail.getStartTime());
                newShiftDetail.setEndTime(shiftDetail.getEndTime());
            }

            newShiftDetail.setCreatedAt(new Date());
            savedDetails.add(employeeShiftDetailRepository.save(newShiftDetail));
        }
        return savedDetails;
    }

    // Eliminar un registro
    @Transactional
    public void deleteShiftDetail(Long id) {
        validateId(id);
        if (!employeeShiftDetailRepository.existsById(id)) {
            throw new ResourceNotFoundException("EmployeeShiftDetail no encontrado con id: " + id);
        }
        employeeShiftDetailRepository.deleteById(id);
    }

    // Validar los datos antes de guardar o actualizar
    private void validateShiftDetail(EmployeeShiftDetail shiftDetail) {
        if (shiftDetail.getEmployeeScheduleId() == null || shiftDetail.getEmployeeScheduleId() <= 0) {
            throw new IllegalArgumentException("El Employee Schedule ID es obligatorio y debe ser un número válido.");
        }
        if (shiftDetail.getDayOfWeek() == null || shiftDetail.getDayOfWeek() < 1 || shiftDetail.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("El día de la semana debe estar entre 1 (Lunes) y 7 (Domingo).");
        }
    }

    // Validar que el ID sea correcto
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID inválido. Debe ser un número positivo.");
        }
    }
}

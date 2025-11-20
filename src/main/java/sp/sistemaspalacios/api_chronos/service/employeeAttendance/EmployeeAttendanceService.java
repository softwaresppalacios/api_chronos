package sp.sistemaspalacios.api_chronos.service.employeeAttendance;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.attendance.AttendanceValidationResult;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.repository.employeeAttendance.EmployeeAttendanceRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class EmployeeAttendanceService {

    private final EmployeeAttendanceRepository repository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final AttendanceValidationService validationService; // ← AGREGAR

    // ✅ MODIFICAR ESTE MÉTODO
    @Transactional
    public EmployeeAttendance registerAttendance(Long scheduleId, AttendanceType type) {
        EmployeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró el horario del empleado"
                ));

        if (schedule.getShift() == null) {
            throw new IllegalArgumentException(
                    "El horario del empleado no tiene un turno asignado"
            );
        }

        EmployeeAttendance attendance = new EmployeeAttendance();
        attendance.setEmployeeSchedule(schedule);
        attendance.setType(type);
        attendance.setTimestamp(new Date());

        // ✅ AGREGAR VALIDACIÓN
        AttendanceValidationResult validation = validationService
                .validateAttendance(attendance);

        if (type == AttendanceType.CLOCK_IN) {
            attendance.setIsLate(validation.getMinutesLate() > 0);
            attendance.setMessage(validation.getMessage());

            if (!validation.isValid()) {
                throw new IllegalArgumentException(validation.getMessage());
            }
        } else {
            attendance.setIsLate(false);
            attendance.setMessage("Marcación registrada correctamente");
        }

        return repository.save(attendance);
    }

    // ✅ AGREGAR ESTE MÉTODO
    @Transactional
    public EmployeeAttendance registerManualAttendance(
            Long scheduleId,
            AttendanceType type,
            Date timestamp,
            String message
    ) {
        EmployeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Horario no encontrado"));

        if (timestamp.after(new Date())) {
            throw new IllegalArgumentException(
                    "La fecha de marcación no puede ser en el futuro."
            );
        }

        EmployeeAttendance attendance = new EmployeeAttendance();
        attendance.setEmployeeSchedule(schedule);
        attendance.setType(type);
        attendance.setTimestamp(timestamp);
        attendance.setIsLate(true);
        attendance.setMessage(message != null ? message :
                "Marcación manual realizada por Gestión Humana");

        return repository.save(attendance);
    }
}
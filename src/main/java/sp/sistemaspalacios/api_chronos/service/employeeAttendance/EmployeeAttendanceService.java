package sp.sistemaspalacios.api_chronos.service.employeeAttendance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import sp.sistemaspalacios.api_chronos.repository.employeeAttendance.EmployeeAttendanceRepository;



import java.util.Date;
import java.util.List;

@Service
public class EmployeeAttendanceService {
    @Autowired
    private EmployeeAttendanceRepository repository;

    public EmployeeAttendance registerAttendance(EmployeeSchedule schedule, AttendanceType type) {
        EmployeeAttendance lastAttendance = repository.findTopByEmployeeScheduleOrderByTimestampDesc(schedule).orElse(null);

        // Validar que no se repita la misma marcación consecutiva
        if (lastAttendance != null && lastAttendance.getType() == type) {
            throw new IllegalArgumentException("No se puede registrar la misma marcación consecutiva.");
        }

        // Determinar si la marcación es puntual o con retraso
        boolean isLate = determineIfLate(schedule, type);
        String message = isLate ? "Marcación con retraso" : "Marcación puntual";

        // Registrar la marcación
        EmployeeAttendance attendance = new EmployeeAttendance();
        attendance.setEmployeeSchedule(schedule);
        attendance.setType(type);
        attendance.setTimestamp(new Date());
        attendance.setIsLate(isLate);
        attendance.setMessage(message);
        return repository.save(attendance);
    }

    public List<EmployeeAttendance> getAttendancesBySchedule(EmployeeSchedule schedule) {
        return repository.findByEmployeeSchedule(schedule);
    }

    private boolean determineIfLate(EmployeeSchedule schedule, AttendanceType type) {
        // Implementar la lógica para verificar si la hora actual está dentro del horario del turno.
        return false; // Simulación: cambiar por la lógica real.
    }
}

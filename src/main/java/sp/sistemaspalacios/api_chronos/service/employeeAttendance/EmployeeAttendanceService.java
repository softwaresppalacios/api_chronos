package sp.sistemaspalacios.api_chronos.service.employeeAttendance;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.AttendanceType;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.repository.employeeAttendance.EmployeeAttendanceRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeAttendanceService {

    @Autowired
    private EmployeeAttendanceRepository repository;

    @Autowired
    private EmployeeScheduleRepository scheduleRepository;

    @Transactional
    public EmployeeAttendance registerAttendance(Long scheduleId, AttendanceType type) {
        // Buscar el EmployeeSchedule en la base de datos
        EmployeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el horario del empleado"));

        // Validar que el horario tenga un turno asignado
        if (schedule.getShift() == null) {
            throw new IllegalArgumentException("El horario del empleado no tiene un turno asignado");
        }

        // Obtener la fecha y hora actual
        Date now = new Date();

        // Verificar si la marcación es válida y no supera el límite de tardanza
        boolean isLate;
        String message;

        Optional<ShiftDetail> applicableShift = getShiftForCurrentTime(schedule);
        if (applicableShift.isEmpty()) {
            throw new IllegalArgumentException("No hay turno registrado para esta hora");
        }

        ShiftDetail shiftDetails = applicableShift.get();
        Date shiftStartTime = getDateTimeForShift(shiftDetails.getStartTime());

        // Calcular diferencia en minutos entre la hora de inicio y la marcación
        long differenceInMinutes = (now.getTime() - shiftStartTime.getTime()) / (60 * 1000);

        if (differenceInMinutes > 20) {
            throw new IllegalArgumentException("La marcación no fue registrada. Debe presentarse en la oficina de gestión humana.");
        } else if (differenceInMinutes > 0) {
            isLate = true;
            message = "Marcación con retraso";
        } else {
            isLate = false;
            message = "Marcación puntual";
        }

        // Verificar si la marcación es consecutiva
        EmployeeAttendance lastAttendance = repository.findTopByEmployeeScheduleOrderByTimestampDesc(schedule).orElse(null);
        if (lastAttendance != null && lastAttendance.getType() == type) {
            throw new IllegalArgumentException("No se puede registrar la misma marcación consecutiva.");
        }

        // Crear y guardar el registro de asistencia
        EmployeeAttendance attendance = new EmployeeAttendance();
        attendance.setEmployeeSchedule(schedule);
        attendance.setType(type);
        attendance.setTimestamp(now);
        attendance.setIsLate(isLate);
        attendance.setMessage(message);

        return repository.save(attendance);
    }

    public EmployeeAttendance registerManualAttendance(Long scheduleId, AttendanceType type, Date timestamp, String message) {
        // Buscar el horario del empleado
        EmployeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Horario no encontrado"));

        // Validar que la marcación manual no sea en el futuro
        if (timestamp.after(new Date())) {
            throw new IllegalArgumentException("La fecha de marcación no puede ser en el futuro.");
        }

        // Crear y registrar la asistencia manual
        EmployeeAttendance attendance = new EmployeeAttendance();
        attendance.setEmployeeSchedule(schedule);
        attendance.setType(type);
        attendance.setTimestamp(timestamp);
        attendance.setIsLate(true); // Se asume que si se registra manualmente, es con retraso
        attendance.setMessage(message != null ? message : "Marcación manual realizada por Gestión Humana");

        return repository.save(attendance);
    }


    public List<EmployeeAttendance> getAttendancesBySchedule(EmployeeSchedule schedule) {
        return repository.findByEmployeeSchedule(schedule);
    }

    // Método para obtener el turno del empleado en función de la hora actual
    private Optional<ShiftDetail> getShiftForCurrentTime(EmployeeSchedule schedule) {
        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1; // Ajustar para que Lunes sea 1

        return schedule.getShift().getShiftDetails().stream()
                .filter(shift -> shift.getDayOfWeek() == currentDayOfWeek)
                .findFirst();
    }

    // Método para convertir una hora de turno (HH:mm) en un objeto Date con la fecha actual
    private Date getDateTimeForShift(String shiftTime) {
        String[] timeParts = shiftTime.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }
}
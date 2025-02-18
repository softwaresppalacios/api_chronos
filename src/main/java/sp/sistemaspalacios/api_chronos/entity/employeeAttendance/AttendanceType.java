package sp.sistemaspalacios.api_chronos.entity.employeeAttendance;

public enum AttendanceType {
    CLOCK_IN,  // Entrada al turno
    BREAK_OUT, // Salida a descanso
    BREAK_IN,  // Regreso del descanso
    CLOCK_OUT  // Salida definitiva del turno
}

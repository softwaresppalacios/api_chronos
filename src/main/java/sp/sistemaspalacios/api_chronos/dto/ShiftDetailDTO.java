package sp.sistemaspalacios.api_chronos.dto;

public class ShiftDetailDTO {
    private Long id;
    private Long shiftId;  // Para referencias
    private Integer dayOfWeek;
    private String startTime;
    private String endTime;

    // 🔹 NUEVOS CAMPOS PARA EL BREAK
    private String breakStartTime;  // Hora de inicio del descanso
    private String breakEndTime;    // Hora de fin del descanso

    // Constructor por defecto
    public ShiftDetailDTO() {}

    // Constructor completo (para respuestas)
    public ShiftDetailDTO(Long id, Long shiftId, Integer dayOfWeek, String startTime, String endTime,
                          String breakStartTime, String breakEndTime) {
        this.id = id;
        this.shiftId = shiftId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStartTime = breakStartTime;
        this.breakEndTime = breakEndTime;
    }

    // Constructor para crear (sin id)
    public ShiftDetailDTO(Long shiftId, Integer dayOfWeek, String startTime, String endTime,
                          String breakStartTime, String breakEndTime) {
        this.shiftId = shiftId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStartTime = breakStartTime;
        this.breakEndTime = breakEndTime;
    }

    // Constructor legacy (para compatibilidad con código existente)
    public ShiftDetailDTO(Integer dayOfWeek, String startTime, String endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    // 🔹 GETTERS Y SETTERS PARA BREAK
    public String getBreakStartTime() {
        return breakStartTime;
    }

    public void setBreakStartTime(String breakStartTime) {
        this.breakStartTime = breakStartTime;
    }

    public String getBreakEndTime() {
        return breakEndTime;
    }

    public void setBreakEndTime(String breakEndTime) {
        this.breakEndTime = breakEndTime;
    }

    @Override
    public String toString() {
        return "ShiftDetailDTO{" +
                "id=" + id +
                ", shiftId=" + shiftId +
                ", dayOfWeek=" + dayOfWeek +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", breakStartTime='" + breakStartTime + '\'' +
                ", breakEndTime='" + breakEndTime + '\'' +
                '}';
    }
}
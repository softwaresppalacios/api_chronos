package sp.sistemaspalacios.api_chronos.dto;

public class ShiftDetailDTO {
    private Long id;
    private Long shiftId;  // Para referencias
    private Integer dayOfWeek;
    private String startTime;
    private String endTime;

    // 🔹 Nuevos campos para configuración global
    private Integer breakMinutes;     // Descanso usado cuando se creó
    private String weeklyHours;       // Horas semanales usadas cuando se creó
    private String nightHoursStart;   // Hora inicio nocturna usada cuando se creó
    private String hoursPerDay;       // Horas máximas al día usadas cuando se creó

    // 🔹 Nuevos campos para break
    private String breakStartTime;    // Hora de inicio del descanso
    private String breakEndTime;      // Hora de fin del descanso

    // Constructor por defecto
    public ShiftDetailDTO() {}

    // Constructor completo
    public ShiftDetailDTO(Long id, Long shiftId, Integer dayOfWeek, String startTime, String endTime,
                          String breakStartTime, String breakEndTime, Integer breakMinutes,
                          String weeklyHours, String nightHoursStart, String hoursPerDay) {
        this.id = id;
        this.shiftId = shiftId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStartTime = breakStartTime;
        this.breakEndTime = breakEndTime;
        this.breakMinutes = breakMinutes;
        this.weeklyHours = weeklyHours;
        this.nightHoursStart = nightHoursStart;
        this.hoursPerDay = hoursPerDay;
    }

    // Constructor básico (crear sin ID)
    public ShiftDetailDTO(Long shiftId, Integer dayOfWeek, String startTime, String endTime,
                          String breakStartTime, String breakEndTime) {
        this.shiftId = shiftId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStartTime = breakStartTime;
        this.breakEndTime = breakEndTime;
    }

    public ShiftDetailDTO(Integer dayOfWeek, String startTime, String endTime) {
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getBreakStartTime() { return breakStartTime; }
    public void setBreakStartTime(String breakStartTime) { this.breakStartTime = breakStartTime; }

    public String getBreakEndTime() { return breakEndTime; }
    public void setBreakEndTime(String breakEndTime) { this.breakEndTime = breakEndTime; }

    public Integer getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(Integer breakMinutes) { this.breakMinutes = breakMinutes; }

    public String getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(String weeklyHours) { this.weeklyHours = weeklyHours; }

    public String getNightHoursStart() { return nightHoursStart; }
    public void setNightHoursStart(String nightHoursStart) { this.nightHoursStart = nightHoursStart; }

    public String getHoursPerDay() { return hoursPerDay; }
    public void setHoursPerDay(String hoursPerDay) { this.hoursPerDay = hoursPerDay; }

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
                ", breakMinutes=" + breakMinutes +
                ", weeklyHours='" + weeklyHours + '\'' +
                ", nightHoursStart='" + nightHoursStart + '\'' +
                ", hoursPerDay='" + hoursPerDay + '\'' +
                '}';
    }
}

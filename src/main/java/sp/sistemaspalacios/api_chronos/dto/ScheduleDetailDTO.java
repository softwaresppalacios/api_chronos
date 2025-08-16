package sp.sistemaspalacios.api_chronos.dto;

import lombok.Data;

@Data
public class ScheduleDetailDTO {

    private Long scheduleId;      // ID del employee_schedule
    private String shiftName;     // Nombre del turno (Mañana, Tarde, etc)
    private String startDate;     // Fecha inicio
    private String endDate;       // Fecha fin
    private Double hoursInPeriod; // Horas de este turno específico

    // Constructor vacío
    public ScheduleDetailDTO() {}

    // Constructor completo
    public ScheduleDetailDTO(Long scheduleId, String shiftName,
                             String startDate, String endDate,
                             Double hoursInPeriod) {
        this.scheduleId = scheduleId;
        this.shiftName = shiftName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.hoursInPeriod = hoursInPeriod;
    }
}

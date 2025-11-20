package sp.sistemaspalacios.api_chronos.dto.schedule;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleDetailDTO {

    private Long   scheduleId;
    private String shiftName;
    private String startDate;
    private String endDate;
    private Long   shiftId;
    // Total del turno (regular + festivo + extra)
    private Double hoursInPeriod;
    private Map<String, Object> overtimeBreakdown;

    // 🔹 Nuevos: para que el detalle muestre correctamente las columnas
    private Double regularHours;   // REGULAR_*
    private Double overtimeHours;  // EXTRA_* + DOMINICAL_*
    private Double festivoHours;   // FESTIVO_*

    private String overtimeType;   // etiqueta predominante extra
    private String festivoType;    // etiqueta predominante festivo

    // Constructor antiguo (si lo usabas en algún lado)
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

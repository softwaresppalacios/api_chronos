package sp.sistemaspalacios.api_chronos.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ScheduleAssignmentGroupDTO {

    private Long id;
    private Long employeeId;
    private String periodStart;   // "yyyy-MM-dd"
    private String periodEnd;     // "yyyy-MM-dd"
    private List<Long> employeeScheduleIds;  // IDs de los schedules agrupados

    private BigDecimal totalHours;     // total efectivo (regular + extra) - SIN festivos
    private BigDecimal regularHours;   // horas regulares calculadas

    // HORAS NO FESTIVAS
    private BigDecimal overtimeHours;  // horas extra NO festivas (dominical, extra, nocturno)
    private String overtimeType;       // tipo predominante no festivo

    // HORAS FESTIVAS
    private BigDecimal festivoHours;   // horas festivas (festivo diurno + nocturno)
    private String festivoType;        // tipo predominante festivo ("Festivo" o "Festivo Nocturno")

    private String status;
    private String shiftName;
    // NUEVO: "Horas asignadas" = regularHours (se llena en el Service)
    private BigDecimal assignedHours;

    // Desglose detallado para debugging/reportes
    private Map<String, Object> overtimeBreakdown;

    // Lista de los turnos incluidos (info resumida)
    private List<ScheduleDetailDTO> scheduleDetails;

    // Constructor vacío
    public ScheduleAssignmentGroupDTO() {}


    // Getter y setter


    // Constructor con parámetros básicos
    public ScheduleAssignmentGroupDTO(Long employeeId, String periodStart, String periodEnd) {
        this.employeeId = employeeId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalHours = BigDecimal.ZERO;
        this.regularHours = BigDecimal.ZERO;
        this.overtimeHours = BigDecimal.ZERO;
        this.festivoHours = BigDecimal.ZERO;
        this.assignedHours = BigDecimal.ZERO;
        this.status = "ACTIVE";
    }
}
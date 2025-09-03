package sp.sistemaspalacios.api_chronos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class EmployeeScheduleDTO {
    private Long id;
    private Long numberId;
    private String firstName;
    private String secondName;
    private String surName;
    private String secondSurname;
    private String dependency;
    private String position;
    private String startDate;
    private String endDate;
    private ShiftsDTO shift;

    // 🆕 NUEVO (versión nueva)
    private String shiftName;
    // común a ambas
    private Long daysParentId;
    private Map<String, Object> days;
    // 🆕 NUEVO (versión nueva)
    private Double hoursInPeriod;

    // Constructor vacío (Jackson/Lombok)
    public EmployeeScheduleDTO() {}

    // === Constructor NUEVO (15 parámetros) ===
    public EmployeeScheduleDTO(Long id, Long numberId, String firstName, String secondName,
                               String surName, String secondSurname, String dependency,
                               String position, String startDate, String endDate,
                               ShiftsDTO shift, String shiftName, Long daysParentId,
                               Map<String, Object> days, Double hoursInPeriod) {
        this.id = id;
        this.numberId = numberId;
        this.firstName = firstName;
        this.secondName = secondName;
        this.surName = surName;
        this.secondSurname = secondSurname;
        this.dependency = dependency;
        this.position = position;
        this.startDate = startDate;
        this.endDate = endDate;
        this.shift = shift;
        this.shiftName = shiftName;
        this.daysParentId = daysParentId;
        this.days = days;
        this.hoursInPeriod = hoursInPeriod;
    }

    // === Constructor VIEJO (13 parámetros) — retrocompatible ===
    // (sin shiftName ni hoursInPeriod)
    public EmployeeScheduleDTO(Long id, Long numberId, String firstName, String secondName,
                               String surName, String secondSurname, String dependency,
                               String position, String startDate, String endDate,
                               ShiftsDTO shift, Long daysParentId, Map<String, Object> days) {
        this(id, numberId, firstName, secondName, surName, secondSurname, dependency, position,
                startDate, endDate, shift, null, daysParentId, days, null);
    }
}

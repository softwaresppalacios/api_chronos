package sp.sistemaspalacios.api_chronos.dto.employee;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeHoursSummaryDTO {
    private Long employeeId;
    private String employeeName;
    private Double totalHours;
    private Double assignedHours;
    private Double regularHours;
    private Double overtimeHours;
    private Double festivoHours;
    private String overtimeType;
    private String festivoType;
    private Map<String, Object> overtimeBreakdown;
    private Date lastUpdated;
}
package sp.sistemaspalacios.api_chronos.dto.shift;

import lombok.Data;

@Data
public class EmployeeShiftDetailDTO {
    private Long id;
    private Integer dayOfWeek;
    private String startTime;
    private String endTime;

    public EmployeeShiftDetailDTO(Long id, Integer dayOfWeek, String startTime, String endTime) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters y Setters
}

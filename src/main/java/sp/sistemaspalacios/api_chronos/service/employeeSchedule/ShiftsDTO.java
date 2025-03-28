package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import java.util.List;

public class ShiftsDTO {
    private Long id;
    private String name;
    private String description;
    private Long timeBreak;
    private List<ShiftDetailDTO> shiftDetails;

    public ShiftsDTO(Long id, String name, String description, Long timeBreak, List<ShiftDetailDTO> shiftDetails) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.timeBreak = timeBreak;
        this.shiftDetails = shiftDetails;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTimeBreak() {
        return timeBreak;
    }

    public void setTimeBreak(Long timeBreak) {
        this.timeBreak = timeBreak;
    }

    public List<ShiftDetailDTO> getShiftDetails() {
        return shiftDetails;
    }

    public void setShiftDetails(List<ShiftDetailDTO> shiftDetails) {
        this.shiftDetails = shiftDetails;
    }
}
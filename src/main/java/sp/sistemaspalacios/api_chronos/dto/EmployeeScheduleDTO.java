package sp.sistemaspalacios.api_chronos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;
@Data
@JsonInclude(JsonInclude.Include.ALWAYS) // Forzar inclusión de todos los campos
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
    private Long daysParentId;  // Asegúrate que este campo existe
    private Map<String, Object> days;  // Asegúrate que este campo existe

    // Constructor completo
    public EmployeeScheduleDTO() {
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
        this.daysParentId = daysParentId;
        this.days = days;
    }



    public EmployeeScheduleDTO(Long id, Long id1, Object name, Long id2, String name1, String string, String string1, Long daysParentId, Map<String, Object> daysStructure) {
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNumberId() { return numberId; }
    public void setNumberId(Long numberId) { this.numberId = numberId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getSecondName() { return secondName; }
    public void setSecondName(String secondName) { this.secondName = secondName; }

    public String getSurName() { return surName; }
    public void setSurName(String surName) { this.surName = surName; }

    public String getSecondSurname() { return secondSurname; }
    public void setSecondSurname(String secondSurname) { this.secondSurname = secondSurname; }

    public String getDependency() { return dependency; }
    public void setDependency(String dependency) { this.dependency = dependency; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public ShiftsDTO getShift() { return shift; }
    public void setShift(ShiftsDTO shift) { this.shift = shift; }


    public Object getName() {
        return null;
    }
}
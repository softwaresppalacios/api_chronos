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
    private String shiftName;  // 🆕 NUEVO CAMPO AGREGADO
    private Long daysParentId;  // Asegúrate que este campo existe
    private Map<String, Object> days;  // Asegúrate que este campo existe

    // ========== NUEVO CAMPO PARA LAS HORAS ==========
    private Double hoursInPeriod;  // 🔥 ESTE ES EL CAMPO QUE NECESITAMOS

    // Constructor vacío
    public EmployeeScheduleDTO() {
    }

    // Constructor completo
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
        this.hoursInPeriod = hoursInPeriod;  // 🔥 NUEVO
    }

    // Getters y Setters existentes
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

    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }

    public Long getDaysParentId() { return daysParentId; }
    public void setDaysParentId(Long daysParentId) { this.daysParentId = daysParentId; }

    public Map<String, Object> getDays() { return days; }
    public void setDays(Map<String, Object> days) { this.days = days; }

    // ========== NUEVO GETTER Y SETTER PARA HORAS ==========
    public Double getHoursInPeriod() { return hoursInPeriod; }
    public void setHoursInPeriod(Double hoursInPeriod) { this.hoursInPeriod = hoursInPeriod; }

    public Object getName() {
        return null;
    }
}
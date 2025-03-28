package sp.sistemaspalacios.api_chronos.dto;

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

    public EmployeeScheduleDTO(Long id, Long numberId, String firstName, String secondName, String surName, String secondSurname,
                               String dependency, String position, String startDate, String endDate, ShiftsDTO shift) {
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
    }

    public EmployeeScheduleDTO(Long id, Long numberId, String firstName, String secondName, String surName, String secondSurname, String dependency, String position, String string, String endDate, sp.sistemaspalacios.api_chronos.service.employeeSchedule.ShiftsDTO shiftDTO) {
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
}
package sp.sistemaspalacios.api_chronos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmployeeResponse {
    private Employee employee;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public static class Employee {
        private Long id;

        @JsonProperty("numberId")  // Mapea "numberId" del JSON al campo Java
        private Long numberId;

        private String firstName;

        @JsonProperty("secondName")
        private String secondName;

        @JsonProperty("surname")
        private String surName;

        @JsonProperty("secondSurname")
        private String secondSurname;

        private Position position;

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

        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
    }

    public static class Position {
        private String name;
        private Dependency dependency;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Dependency getDependency() { return dependency; }
        public void setDependency(Dependency dependency) { this.dependency = dependency; }
    }

    public static class Dependency {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

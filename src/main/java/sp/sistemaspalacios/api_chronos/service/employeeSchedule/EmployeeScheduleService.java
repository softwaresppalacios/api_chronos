package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sp.sistemaspalacios.api_chronos.dto.EmployeeResponse;
import sp.sistemaspalacios.api_chronos.dto.EmployeeScheduleDTO;
import sp.sistemaspalacios.api_chronos.dto.ShiftDetailDTO;
import sp.sistemaspalacios.api_chronos.dto.ShiftsDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeScheduleService {

    @Autowired
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final RestTemplate restTemplate;

    public EmployeeScheduleService(EmployeeScheduleRepository employeeScheduleRepository,
                                   ShiftsRepository shiftsRepository,
                                   RestTemplate restTemplate) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.restTemplate = restTemplate;
    }

    /** 🔹 Obtiene todos los horarios de empleados */
    public List<EmployeeScheduleDTO> getAllEmployeeSchedules() {
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findAll();
        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /** 🔹 Obtiene un horario por su ID */
    @Transactional
    public EmployeeScheduleDTO getEmployeeScheduleById(Long id) {
        EmployeeSchedule schedule = employeeScheduleRepository.findByIdWithShift(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));

        return convertToDTO(schedule);
    }



    /** 🔹 Obtiene los horarios de un empleado por su ID */
    public List<EmployeeScheduleDTO> getSchedulesByEmployeeId(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("Employee ID debe ser un número válido.");
        }
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeId(employeeId);
        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /** 🔹 Obtiene los horarios según el turno (Shift ID) */
    public List<EmployeeScheduleDTO> getSchedulesByShiftId(Long shiftId) {
        if (shiftId == null || shiftId <= 0) {
            throw new IllegalArgumentException("Shift ID debe ser un número válido.");
        }
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByShiftId(shiftId);
        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /** 🔹 Crea un nuevo horario de empleado */
    @Transactional
    public EmployeeSchedule createEmployeeSchedule(EmployeeSchedule schedule) {
        validateSchedule(schedule);
        schedule.setCreatedAt(new Date());

        // 🔹 Asegurarse de que el objeto shift se recupera completamente
        if (schedule.getShift() != null && schedule.getShift().getId() != null) {
            Shifts shift = shiftsRepository.findById(schedule.getShift().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift not found with id: " + schedule.getShift().getId()));
            schedule.setShift(shift);
        } else {
            throw new IllegalArgumentException("Shift ID no puede ser nulo.");
        }

        return employeeScheduleRepository.save(schedule);
    }


    /** 🔹 Actualiza un horario de empleado */
    @Transactional
    public EmployeeSchedule updateEmployeeSchedule(Long id, EmployeeSchedule schedule) {
        EmployeeSchedule existing = employeeScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeSchedule not found with id: " + id));

        validateSchedule(schedule);

        existing.setEmployeeId(schedule.getEmployeeId());

        if (schedule.getShift() != null) {
            existing.setShift(schedule.getShift());
        }

        existing.setStartDate(schedule.getStartDate());
        existing.setEndDate(schedule.getEndDate());
        existing.setUpdatedAt(new Date());

        return employeeScheduleRepository.save(existing);
    }

    /** 🔹 Elimina un horario de empleado */
    @Transactional
    public void deleteEmployeeSchedule(Long id) {
        if (!employeeScheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("EmployeeSchedule not found with id: " + id);
        }
        employeeScheduleRepository.deleteById(id);
    }

    /** 🔹 Convierte un `EmployeeSchedule` a `EmployeeScheduleDTO` */
    private EmployeeScheduleDTO convertToDTO(EmployeeSchedule schedule) {
        // Obtener datos del empleado desde el microservicio
        String url = "http://192.168.23.6:40020/api/employees/bynumberid/" + schedule.getEmployeeId();
        EmployeeResponse response = restTemplate.getForObject(url, EmployeeResponse.class);

        Long numberId = (response != null && response.getEmployee() != null)
                ? response.getEmployee().getNumberId() : null;

        String firstName = (response != null && response.getEmployee() != null)
                ? response.getEmployee().getFirstName() : "Desconocido";

        String secondName = (response != null && response.getEmployee() != null)
                ? response.getEmployee().getSecondName() : "Desconocido";

        String surName = (response != null && response.getEmployee() != null)
                ? response.getEmployee().getSurName() : "Desconocido";

        String secondSurname = (response != null && response.getEmployee() != null)
                ? response.getEmployee().getSecondSurname() : "Desconocido";

        String position = (response != null && response.getEmployee() != null
                && response.getEmployee().getPosition() != null)
                ? response.getEmployee().getPosition().getName() : "Sin posición";

        String dependency = (response != null && response.getEmployee() != null
                && response.getEmployee().getPosition() != null
                && response.getEmployee().getPosition().getDependency() != null)
                ? response.getEmployee().getPosition().getDependency().getName() : "Sin dependencia";

        // Obtener detalles del turno
        Shifts shift = schedule.getShift();
        ShiftsDTO shiftDTO = null;

        if (shift != null) {
            List<ShiftDetailDTO> shiftDetails = shift.getShiftDetails().stream()
                    .map(detail -> new ShiftDetailDTO(detail.getDayOfWeek(), detail.getStartTime(), detail.getEndTime()))
                    .collect(Collectors.toList());

            shiftDTO = new ShiftsDTO(
                    shift.getId(),
                    shift.getName(),
                    shift.getDescription(),
                    shift.getTimeBreak(),
                    shiftDetails
            );
        }

        return new EmployeeScheduleDTO(
                schedule.getId(),
                numberId,
                firstName,
                secondName,
                surName,
                secondSurname,
                dependency,
                position,
                schedule.getStartDate().toString(),
                schedule.getEndDate().toString(),
                shiftDTO
        );
    }

    /** 🔹 Valida los datos antes de guardar/actualizar un `EmployeeSchedule` */
    private void validateSchedule(EmployeeSchedule schedule) {
        if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0) {
            throw new IllegalArgumentException("Employee ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0) {
            throw new IllegalArgumentException("Shift ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getStartDate() == null || schedule.getEndDate() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias.");
        }
        if (schedule.getStartDate().after(schedule.getEndDate())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
        }
    }

    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(List<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new IllegalArgumentException("La lista de Employee IDs no puede estar vacía.");
        }

        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeIdIn(employeeIds);
        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

}

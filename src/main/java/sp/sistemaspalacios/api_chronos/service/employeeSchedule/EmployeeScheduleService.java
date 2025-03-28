package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sp.sistemaspalacios.api_chronos.dto.*;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;

import javax.swing.text.Position;
import java.sql.Time;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeeScheduleService {

    @Autowired
    private final EmployeeScheduleRepository employeeScheduleRepository;
    private final ShiftsRepository shiftsRepository;
    private final RestTemplate restTemplate;
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository;
    private final EmployeeScheduleRepository employeeRepository;
    public EmployeeScheduleService(EmployeeScheduleRepository employeeScheduleRepository,
                                   ShiftsRepository shiftsRepository,
                                   RestTemplate restTemplate,
                                   EmployeeScheduleDayRepository employeeScheduleDayRepository, EmployeeScheduleTimeBlockRepository employeeScheduleTimeBlockRepository, EmployeeScheduleRepository employeeRepository) {
        this.employeeScheduleRepository = employeeScheduleRepository;
        this.shiftsRepository = shiftsRepository;
        this.restTemplate = restTemplate;
        this.employeeScheduleDayRepository = employeeScheduleDayRepository;
        this.employeeScheduleTimeBlockRepository = employeeScheduleTimeBlockRepository;
        this.employeeRepository = employeeRepository;
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


    @Transactional
    public List<EmployeeSchedule> createMultipleSchedules(List<EmployeeSchedule> schedules) {
        List<EmployeeSchedule> savedSchedules = new ArrayList<>();

        // Variable para almacenar un ID común para days_parent_id
        Long commonDaysParentId = null;

        for (EmployeeSchedule schedule : schedules) {
            // Validaciones y configuraciones básicas
            validateSchedule(schedule);
            schedule.setCreatedAt(new Date());

            // Buscar y establecer el turno
            Shifts shift = shiftsRepository.findById(schedule.getShift().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Turno no encontrado"));
            schedule.setShift(shift);

            // Generar días de horario
            generateScheduleDays(schedule);

            // Guardar el horario
            EmployeeSchedule savedSchedule = employeeScheduleRepository.save(schedule);

            // Guardar explícitamente los días
            List<EmployeeScheduleDay> savedDays = new ArrayList<>();
            for (EmployeeScheduleDay day : savedSchedule.getDays()) {
                day.setEmployeeSchedule(savedSchedule);

                // MODIFICACIÓN IMPORTANTE: Establecer días padre
                if (commonDaysParentId == null) {
                    commonDaysParentId = savedSchedule.getId();
                }
                day.setDaysParentId(commonDaysParentId);

                EmployeeScheduleDay savedDay = employeeScheduleDayRepository.save(day);
                savedDays.add(savedDay);
            }

            savedSchedule.setDays(savedDays);

            // Establecer el mismo days_parent_id para todos los horarios
            savedSchedule.setDaysParentId(commonDaysParentId);
            employeeScheduleRepository.save(savedSchedule);

            savedSchedules.add(savedSchedule);
        }

        return savedSchedules;
    }
    private void generateScheduleDays(EmployeeSchedule schedule) {
        // Clear any existing days to prevent duplicates
        schedule.getDays().clear();

        LocalDate startDate = schedule.getStartDate().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate endDate = schedule.getEndDate().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        List<ShiftDetail> shiftDetails = schedule.getShift().getShiftDetails();

        while (!startDate.isAfter(endDate)) {
            EmployeeScheduleDay day = new EmployeeScheduleDay();
            day.setEmployeeSchedule(schedule);
            day.setDate(java.sql.Date.valueOf(startDate));
            day.setDayOfWeek(startDate.getDayOfWeek().getValue());
            day.setCreatedAt(new Date());

            // Generate time blocks for the day
            generateTimeBlocks(day, shiftDetails, startDate.getDayOfWeek().getValue());

            // Add the day to the schedule
            schedule.getDays().add(day);
            startDate = startDate.plusDays(1);
        }
    }

    private void generateTimeBlocks(EmployeeScheduleDay day, List<ShiftDetail> shiftDetails, int dayOfWeek) {
        // Clear any existing time blocks
        day.setTimeBlocks(new ArrayList<>());

        for (ShiftDetail detail : shiftDetails) {
            if (detail.getDayOfWeek() == dayOfWeek) {
                EmployeeScheduleTimeBlock block = new EmployeeScheduleTimeBlock();
                block.setEmployeeScheduleDay(day);
                block.setStartTime(Time.valueOf(detail.getStartTime() + ":00"));
                block.setEndTime(Time.valueOf(detail.getEndTime() + ":00"));
                block.setCreatedAt(new Date());

                // Add the block to the day's time blocks
                day.getTimeBlocks().add(block);
            }
        }
    }
    public EmployeeScheduleTimeBlock updateTimeBlock(TimeBlockDTO timeBlockDTO) {
        // 1. Validate input parameters
        validateInputParameters(timeBlockDTO);

        // 2. Retrieve existing time block
        EmployeeScheduleTimeBlock existingTimeBlock = employeeScheduleTimeBlockRepository
                .findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Time block not found with id: " + timeBlockDTO.getId()));

        // 3. Validate day and parent day relationship
        validateDayAndParentDay(existingTimeBlock, timeBlockDTO);

        // 4. Validate employee permissions
        validateEmployeePermissions(existingTimeBlock, timeBlockDTO);

        // 5. Update time block details
        updateTimeBlockDetails(existingTimeBlock, timeBlockDTO);

        // 6. Save and return updated time block
        return employeeScheduleTimeBlockRepository.save(existingTimeBlock);
    }

    private void validateInputParameters(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO == null) {
            throw new IllegalArgumentException("Time block data cannot be null");
        }
        if (timeBlockDTO.getStartTime() == null || timeBlockDTO.getEndTime() == null) {
            throw new IllegalArgumentException("Start time and end time must be provided");
        }
        if (timeBlockDTO.getEmployeeScheduleDayId() == null) {
            throw new IllegalArgumentException("Employee schedule day ID must be specified");
        }
    }

    private void validateDayAndParentDay(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        EmployeeScheduleDay currentDay = existingTimeBlock.getEmployeeScheduleDay();

        // Check if the time block belongs to the specified day
        if (!currentDay.getId().equals(timeBlockDTO.getEmployeeScheduleDayId())) {
            throw new IllegalArgumentException("Time block does not belong to the specified day");
        }

        // Additional parent day validation (based on the hint in the original comment)
        Long parentDayId = currentDay.getParentDayId();
        if (parentDayId != null) {
            // Optional: Add specific parent day validation logic here
            // For example, ensuring the update respects parent day constraints
        }
    }

    private void validateEmployeePermissions(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        EmployeeSchedule employeeSchedule = existingTimeBlock.getEmployeeScheduleDay().getEmployeeSchedule();

        if (employeeSchedule == null) {
            throw new IllegalArgumentException("No employee schedule found for this time block");
        }


    }

    private void updateTimeBlockDetails(EmployeeScheduleTimeBlock existingTimeBlock, TimeBlockDTO timeBlockDTO) {
        existingTimeBlock.setStartTime(Time.valueOf(timeBlockDTO.getStartTime()));
        existingTimeBlock.setEndTime(Time.valueOf(timeBlockDTO.getEndTime()));
        existingTimeBlock.setUpdatedAt(new Date());
    }








    @Transactional
    public EmployeeScheduleTimeBlock updateTimeBlockByDependency(TimeBlockDependencyDTO timeBlockDTO) {
        // 1. Obtener el bloque de tiempo existente
        EmployeeScheduleTimeBlock existingTimeBlock = employeeScheduleTimeBlockRepository
                .findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bloque de tiempo no encontrado con id: " + timeBlockDTO.getId()));

        // 2. Validar que el bloque pertenece al día especificado
        if (!existingTimeBlock.getEmployeeScheduleDay().getId().equals(timeBlockDTO.getEmployeeScheduleDayId())) {
            throw new IllegalArgumentException("El bloque de tiempo no pertenece al día especificado.");
        }

        // 3. Validar que pertenece a la dependencia especificada
        EmployeeScheduleDay employeeScheduleDay = existingTimeBlock.getEmployeeScheduleDay();
        EmployeeSchedule employeeSchedule = employeeScheduleDay.getEmployeeSchedule();

        // Verificaciones más detalladas
        if (employeeSchedule == null) {
            throw new IllegalArgumentException("No se encontró el horario del empleado.");
        }

        if (employeeSchedule.getShift() == null) {
            throw new IllegalArgumentException("No se encontró el turno del empleado.");
        }

        // Obtener los IDs de dependencia
        Long scheduleDependencyId = employeeSchedule.getShift().getDependencyId();
        Long dtoDependencyId = Long.valueOf(timeBlockDTO.getDependencyId());

        // Logging o impresión de los IDs (opcional, para depuración)
        System.out.println("Schedule Dependency ID: " + scheduleDependencyId);
        System.out.println("DTO Dependency ID: " + dtoDependencyId);

        // Validación de dependencia más robusta
        if (scheduleDependencyId == null || !scheduleDependencyId.equals(dtoDependencyId)) {
            throw new IllegalArgumentException("El bloque de tiempo no pertenece a la dependencia especificada. " +
                    "Dependency ID en Schedule: " + scheduleDependencyId +
                    ", Dependency ID proporcionado: " + dtoDependencyId);
        }

        // 4. Validar horas
        if (timeBlockDTO.getStartTime() == null || timeBlockDTO.getEndTime() == null) {
            throw new IllegalArgumentException("StartTime y EndTime no pueden ser nulos.");
        }

        // 5. Actualizar campos
        existingTimeBlock.setStartTime(Time.valueOf(timeBlockDTO.getStartTime()));
        existingTimeBlock.setEndTime(Time.valueOf(timeBlockDTO.getEndTime()));
        existingTimeBlock.setUpdatedAt(new Date());

        return employeeScheduleTimeBlockRepository.save(existingTimeBlock);
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

        EmployeeScheduleDTO employeeScheduleDTO = new EmployeeScheduleDTO(
                schedule.getId(),
                numberId,
                firstName,
                secondName,
                surName,
                secondSurname,
                dependency,
                position,
                schedule.getStartDate().toString(),
                schedule.getEndDate() != null ? schedule.getEndDate().toString() : null,  // Manejo de null
                shiftDTO
        );
        return employeeScheduleDTO;
    }

    /** 🔹 Valida los datos antes de guardar/actualizar un `EmployeeSchedule` */
    private void validateSchedule(EmployeeSchedule schedule) {
        if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0) {
            throw new IllegalArgumentException("Employee ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0) {
            throw new IllegalArgumentException("Shift ID es obligatorio y debe ser un número válido.");
        }
        if (schedule.getStartDate() == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }
        if (schedule.getEndDate() != null && schedule.getStartDate().after(schedule.getEndDate())) {
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
    /** 🔹 Obtiene los horarios dentro de un rango de fechas */
    public List<EmployeeScheduleDTO> getSchedulesByDateRange(Date startDate, Date endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }

        List<EmployeeSchedule> schedules;

        if (endDate == null) {
            // Si no se proporciona endDate, obtener registros donde endDate sea NULL
            schedules = employeeScheduleRepository.findByStartDateAndNullEndDate(startDate);
        } else {
            if (startDate.after(endDate)) {
                throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
            }
            schedules = employeeScheduleRepository.findByDateRange(startDate, endDate);
        }

        return schedules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeSchedule> createEmployeeSchedules(List<EmployeeSchedule> schedules) {
        return schedules;
    }

    public EmployeeSchedule createEmployeeSchedule(EmployeeSchedule schedule) {
        return schedule;
    }


}

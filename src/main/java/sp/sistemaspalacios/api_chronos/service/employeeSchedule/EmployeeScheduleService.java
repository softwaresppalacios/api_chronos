package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.sql.Time;
import java.util.Date;
import java.time.LocalDate;
import java.time.LocalTime;
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




    private Map<String, Object> convertDayToCompleteDTO(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new LinkedHashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", formatDate(day.getDate())); // Formatear fecha a yyyy-MM-dd
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        // Ordenar bloques de tiempo por hora de inicio
        List<EmployeeScheduleTimeBlock> sortedBlocks = day.getTimeBlocks().stream()
                .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                .collect(Collectors.toList());

        // Convertir bloques a DTO
        List<Object> timeBlocks = sortedBlocks.stream()
                .map(this::convertTimeBlockToDTO)
                .collect(Collectors.toList()).reversed();

        dayMap.put("timeBlocks", timeBlocks);
        return dayMap;
    }



    private Object convertTimeBlockToDTO(EmployeeScheduleTimeBlock employeeScheduleTimeBlock) {
        return null;
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
        // 1. Obtener datos del empleado desde el microservicio
        EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        // 2. Convertir el turno a DTO
        ShiftsDTO shiftDTO = buildShiftDTO(schedule.getShift());

        // 3. Construir estructura de días
        Map<String, Object> daysStructure = buildDaysStructure(schedule);

        // 4. Construir y retornar el DTO completo
        return new EmployeeScheduleDTO(
                schedule.getId(),
                getEmployeeField(employee, EmployeeResponse.Employee::getNumberId),
                getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSurName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondSurname, "Desconocido"),
                getEmployeeDependency(employee),
                getEmployeePosition(employee),
                formatDate(schedule.getStartDate()),
                formatDate(schedule.getEndDate()),
                shiftDTO,
                schedule.getDaysParentId(),
                daysStructure
        );
    }

// --- Métodos auxiliares ---


    private ShiftsDTO buildShiftDTO(Shifts shift) {
        if (shift == null) {
            return null;
        }

        return new ShiftsDTO(
                shift.getId(),
                shift.getName(),
                shift.getDescription(),
                shift.getTimeBreak(),
                shift.getShiftDetails().stream()
                        .map(detail -> new ShiftDetailDTO(
                                detail.getDayOfWeek(),
                                detail.getStartTime(),
                                detail.getEndTime()))
                        .collect(Collectors.toList())
        );
    }







// --- Helpers para manejo de nulos ---

    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter) {
        return employee != null ? getter.apply(employee) : null;
    }

    private <T> T getEmployeeField(EmployeeResponse.Employee employee,
                                   Function<EmployeeResponse.Employee, T> getter,
                                   T defaultValue) {
        try {
            return employee != null ? getter.apply(employee) : defaultValue;
        } catch (NullPointerException e) {
            return defaultValue;
        }
    }

    private String getEmployeeDependency(EmployeeResponse.Employee employee) {
        return getEmployeeField(employee,
                e -> e.getPosition().getDependency().getName(),
                "Sin dependencia");
    }

    private String getEmployeePosition(EmployeeResponse.Employee employee) {
        return getEmployeeField(employee,
                e -> e.getPosition().getName(),
                "Sin posición");
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


    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByEmployeeIds(List<Long> employeeIds) {
        // 1. Obtener horarios con días (sin timeBlocks)
        List<EmployeeSchedule> schedules = employeeScheduleRepository.findByEmployeeIdInWithDays(employeeIds);

        if (!schedules.isEmpty()) {
            // 2. Obtener IDs de los horarios
            List<Long> scheduleIds = schedules.stream()
                    .map(EmployeeSchedule::getId)
                    .collect(Collectors.toList());

            // 3. Cargar timeBlocks en batch para todos los días
            List<EmployeeScheduleDay> daysWithBlocks = employeeScheduleRepository
                    .findDaysWithTimeBlocksByScheduleIds(scheduleIds);

            // 4. Asociar los timeBlocks a los días correspondientes
            Map<Long, List<EmployeeScheduleDay>> daysByScheduleId = daysWithBlocks.stream()
                    .collect(Collectors.groupingBy(
                            day -> day.getEmployeeSchedule().getId(),
                            Collectors.toList()
                    ));

            schedules.forEach(schedule -> {
                List<EmployeeScheduleDay> days = daysByScheduleId.get(schedule.getId());
                if (days != null) {
                    // Reemplazar la lista de días con los que tienen timeBlocks cargados
                    schedule.getDays().clear();
                    schedule.getDays().addAll(days);
                }
            });
        }

        return schedules.stream()
                .map(this::convertToCompleteDTO)
                .collect(Collectors.toList());
    }







    @Transactional
    public List<EmployeeScheduleDTO> getSchedulesByDependencyId(Long dependencyId, LocalDate startDate, LocalDate endDate, LocalTime startTime, Long shiftId) {
        List<EmployeeSchedule> schedules;
        Time sqlTime = (startTime != null) ? Time.valueOf(startTime) : null;

        // Paso 1: Usar repositorios para filtrar inicialmente
        if (startDate != null && endDate != null && sqlTime != null && shiftId != null) {
            // Si tenemos todos los filtros, usar el método específico
            schedules = employeeScheduleRepository.findByDependencyIdAndFullDateRangeAndShiftId(
                    dependencyId, startDate, endDate, sqlTime, shiftId);
        } else if (startDate != null && endDate != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndDateRangeNoTime(
                    dependencyId, startDate, endDate);
        } else if (sqlTime != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndStartTime(
                    dependencyId, sqlTime);
        } else if (shiftId != null) {
            schedules = employeeScheduleRepository.findByDependencyIdAndShiftId(dependencyId, shiftId);
        } else {
            schedules = employeeScheduleRepository.findByDependencyId(dependencyId);
        }

        // Paso 2: Crear una copia profunda para trabajar con ella
        List<EmployeeSchedule> filteredSchedules = new ArrayList<>();

        for (EmployeeSchedule originalSchedule : schedules) {
            // Filtrar por turno si no se hizo en la consulta inicial
            if (shiftId != null && (originalSchedule.getShift() == null || !originalSchedule.getShift().getId().equals(shiftId))) {
                continue;
            }

            // Crear copia del horario
            EmployeeSchedule clonedSchedule = new EmployeeSchedule();
            BeanUtils.copyProperties(originalSchedule, clonedSchedule);
            clonedSchedule.setDays(new ArrayList<>());

            // Procesar días
            for (EmployeeScheduleDay originalDay : originalSchedule.getDays()) {
                // Filtrar por fecha si es necesario
                if (startDate != null && endDate != null) {
                    Date dayDate = originalDay.getDate();
                    Date startDateUtil = java.sql.Date.valueOf(startDate);
                    Date endDateUtil = java.sql.Date.valueOf(endDate);

                    if (dayDate.before(startDateUtil) || dayDate.after(endDateUtil)) {
                        continue;
                    }
                }

                // Clonar el día
                EmployeeScheduleDay clonedDay = new EmployeeScheduleDay();
                BeanUtils.copyProperties(originalDay, clonedDay);
                clonedDay.setEmployeeSchedule(clonedSchedule);
                clonedDay.setTimeBlocks(new ArrayList<>());

                // Procesar bloques de tiempo
                boolean hasValidBlocks = false;

                for (EmployeeScheduleTimeBlock originalBlock : originalDay.getTimeBlocks()) {
                    // Filtrar por hora de inicio si es necesario
                    if (sqlTime != null && originalBlock.getStartTime().before(sqlTime)) {
                        continue;
                    }

                    // Clonar el bloque
                    EmployeeScheduleTimeBlock clonedBlock = new EmployeeScheduleTimeBlock();
                    BeanUtils.copyProperties(originalBlock, clonedBlock);
                    clonedBlock.setDay(clonedDay);
                    clonedDay.getTimeBlocks().add(clonedBlock);
                    hasValidBlocks = true;
                }

                // Sólo agregar días con bloques válidos (o si no hay filtro de hora)
                if (hasValidBlocks || sqlTime == null) {
                    clonedSchedule.getDays().add(clonedDay);
                }
            }

            // Sólo agregar horarios con días
            if (!clonedSchedule.getDays().isEmpty()) {
                filteredSchedules.add(clonedSchedule);
            }
        }

        // Convertir a DTOs y devolver
        return filteredSchedules.stream()
                .map(this::convertToCompleteDTO)
                .collect(Collectors.toList());
    }














    private EmployeeScheduleDTO convertToCompleteDTO(EmployeeSchedule schedule) {
        // 1. Obtener datos del empleado desde el microservicio
        EmployeeResponse response = getEmployeeData(schedule.getEmployeeId());
        EmployeeResponse.Employee employee = response != null ? response.getEmployee() : null;

        // 2. Construir estructura de días
        Map<String, Object> daysStructure = buildDaysStructure(schedule);

        // 3. Crear DTO con toda la información
        return new EmployeeScheduleDTO(
                schedule.getId(),
                getEmployeeField(employee, EmployeeResponse.Employee::getNumberId),
                getEmployeeField(employee, EmployeeResponse.Employee::getFirstName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSurName, "Desconocido"),
                getEmployeeField(employee, EmployeeResponse.Employee::getSecondSurname, "Desconocido"),
                getEmployeeDependency(employee),
                getEmployeePosition(employee),
                formatDate(schedule.getStartDate()),
                formatDate(schedule.getEndDate()),
                buildShiftDTO(schedule.getShift()),
                schedule.getDaysParentId(),
                daysStructure
        );
    }



    // Métodos auxiliares para mejor legibilidad
    private ShiftsDTO buildShiftDTO(EmployeeSchedule schedule) {
        if (schedule.getShift() == null) {
            return null;
        }

        return new ShiftsDTO(
                schedule.getShift().getId(),
                schedule.getShift().getName(),
                schedule.getShift().getDescription(),
                schedule.getShift().getTimeBreak(),
                schedule.getShift().getShiftDetails().stream()
                        .map(d -> new ShiftDetailDTO(d.getDayOfWeek(), d.getStartTime(), d.getEndTime()))
                        .collect(Collectors.toList())
        );
    }









    private List<Map<String, String>> buildTimeBlocks(EmployeeScheduleDay day) {
        if (day.getTimeBlocks() == null) {
            return new ArrayList<>();
        }

        return day.getTimeBlocks().stream()
                .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                .map(this::buildTimeBlock)
                .collect(Collectors.toList());
    }



    private String getDependencyName(EmployeeResponse.Employee employee) {
        return employee != null && employee.getPosition() != null &&
                employee.getPosition().getDependency() != null ?
                employee.getPosition().getDependency().getName() : null;
    }

    private String getPositionName(EmployeeResponse.Employee employee) {
        return employee != null && employee.getPosition() != null ?
                employee.getPosition().getName() : null;
    }

    private String formatDate(Date date) {
        return date != null ? new SimpleDateFormat("yyyy-MM-dd").format(date) : null;
    }
    private Map<String, Object> buildDaysStructure(EmployeeSchedule schedule) {
        Map<String, Object> daysMap = new LinkedHashMap<>();
        daysMap.put("id", schedule.getDaysParentId());

        // Ordenar días por fecha
        List<EmployeeScheduleDay> sortedDays = schedule.getDays() != null ?
                schedule.getDays().stream()
                        .sorted(Comparator.comparing(EmployeeScheduleDay::getDate))
                        .collect(Collectors.toList()) :
                new ArrayList<>();

        // Convertir días a DTO
        List<Map<String, Object>> dayItems = sortedDays.stream()
                .map(day -> {
                    Map<String, Object> dayMap = new LinkedHashMap<>();
                    dayMap.put("id", day.getId());
                    dayMap.put("date", formatDate(day.getDate()));
                    dayMap.put("dayOfWeek", day.getDayOfWeek());

                    // Convertir bloques de tiempo
                    List<Map<String, String>> timeBlocks = day.getTimeBlocks() != null ?
                            day.getTimeBlocks().stream()
                                    .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                                    .map(block -> {
                                        Map<String, String> blockMap = new LinkedHashMap<>();
                                        blockMap.put("id", block.getId().toString());
                                        blockMap.put("startTime", block.getStartTime().toString());
                                        blockMap.put("endTime", block.getEndTime().toString());
                                        return blockMap;
                                    })
                                    .collect(Collectors.toList()) :
                            new ArrayList<>();

                    dayMap.put("timeBlocks", timeBlocks);
                    return dayMap;
                })
                .collect(Collectors.toList());

        daysMap.put("items", dayItems);
        return daysMap;
    }

    private Map<String, Object> buildDayItem(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new LinkedHashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", new SimpleDateFormat("yyyy-MM-dd").format(day.getDate()));
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        List<Map<String, String>> timeBlocks = day.getTimeBlocks() != null ?
                day.getTimeBlocks().stream()
                        .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                        .map(this::buildTimeBlock)
                        .collect(Collectors.toList()) :
                new ArrayList<>();

        dayMap.put("timeBlocks", timeBlocks);
        return dayMap;
    }

    private Map<String, String> buildTimeBlock(EmployeeScheduleTimeBlock block) {
        Map<String, String> blockMap = new LinkedHashMap<>();
        blockMap.put("startTime", block.getStartTime().toString());
        blockMap.put("endTime", block.getEndTime().toString());
        return blockMap;
    }

    private String getPositionName(EmployeeResponse response) {
        return null;
    }

    private String getDependencyName(EmployeeResponse response) {
        return null;
    }

    private ShiftsDTO convertShiftToDTO(Shifts shift) {
        return null;
    }



    private Map<String, Object> convertDayToMap(EmployeeScheduleDay day) {
        Map<String, Object> dayMap = new LinkedHashMap<>();
        dayMap.put("id", day.getId());
        dayMap.put("date", formatDate(day.getDate()));
        dayMap.put("dayOfWeek", day.getDayOfWeek());

        List<Map<String, String>> timeBlocks = day.getTimeBlocks().stream()
                .sorted(Comparator.comparing(EmployeeScheduleTimeBlock::getStartTime))
                .map(this::convertTimeBlockToMap)
                .collect(Collectors.toList());

        dayMap.put("timeBlocks", timeBlocks);
        return dayMap;
    }

    private Map<String, String> convertTimeBlockToMap(EmployeeScheduleTimeBlock block) {
        Map<String, String> blockMap = new LinkedHashMap<>();
        blockMap.put("startTime", block.getStartTime().toString());
        blockMap.put("endTime", block.getEndTime().toString());
        return blockMap;
    }

    private EmployeeResponse getEmployeeData(Long employeeId) {
        if (employeeId == null) {
            return null;
        }

        try {
            String url = "http://172.23.160.1:40020/api/employees/bynumberid/" + employeeId;
            ResponseEntity<EmployeeResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    EmployeeResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }

        } catch (Exception e) {

        }
        return null;
    }

    private Object createHeaders() {
        return null;
    }

    // Métodos auxiliares para manejo seguro de nulos
    private Long getSafeNumberId(EmployeeResponse.Employee employee) {
        return employee != null ? employee.getNumberId() : null;
    }

    private String getSafeFirstName(EmployeeResponse.Employee employee) {
        return employee != null && employee.getFirstName() != null ?
                employee.getFirstName() : "Desconocido";
    }

    private String getSafeSecondName(EmployeeResponse.Employee employee) {
        return employee != null && employee.getSecondName() != null ?
                employee.getSecondName() : "Desconocido";
    }

    private String getSafeSurName(EmployeeResponse.Employee employee) {
        return employee != null && employee.getSurName() != null ?
                employee.getSurName() : "Desconocido";
    }

    private String getSafeSecondSurname(EmployeeResponse.Employee employee) {
        return employee != null && employee.getSecondSurname() != null ?
                employee.getSecondSurname() : "Desconocido";
    }

    private String getSafeDependency(EmployeeResponse.Employee employee) {
        if (employee == null || employee.getPosition() == null ||
                employee.getPosition().getDependency() == null) {
            return "Sin dependencia";
        }
        return employee.getPosition().getDependency().getName();
    }

    private String getSafePosition(EmployeeResponse.Employee employee) {
        if (employee == null || employee.getPosition() == null) {
            return "Sin posición";
        }
        return employee.getPosition().getName();
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

package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.TimeBlockDependencyDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeScheduleService;

import java.sql.Time;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeBlockManagementService {

    private final EmployeeScheduleTimeBlockRepository timeBlockRepository;
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final EmployeeScheduleService employeeScheduleService;

    @Transactional
    public Map<String, Object> createTimeBlock(Map<String, Object> timeBlockData) {
        validateTimeBlockData(timeBlockData);

        Long employeeScheduleDayId = extractLong(timeBlockData, "employeeScheduleDayId");
        String startTime = extractString(timeBlockData, "startTime");
        String endTime = extractString(timeBlockData, "endTime");

        EmployeeScheduleDay day = findDayOrThrow(employeeScheduleDayId);

        EmployeeScheduleTimeBlock newBlock = new EmployeeScheduleTimeBlock();
        newBlock.setEmployeeScheduleDay(day);
        newBlock.setStartTime(Time.valueOf(normalizeTimeString(startTime)));
        newBlock.setEndTime(Time.valueOf(normalizeTimeString(endTime)));
        newBlock.setCreatedAt(new Date());

        EmployeeScheduleTimeBlock savedBlock = timeBlockRepository.save(newBlock);

        return createTimeBlockResponse(savedBlock, "CREATED");
    }

    @Transactional
    public Map<String, Object> updateTimeBlock(TimeBlockDTO timeBlockDTO) {
        validateTimeBlockDTO(timeBlockDTO);

        EmployeeScheduleTimeBlock timeBlock = timeBlockRepository
                .findById(timeBlockDTO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TimeBlock no encontrado: " + timeBlockDTO.getId()));

        if (timeBlockDTO.getStartTime() != null) {
            timeBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
        }
        if (timeBlockDTO.getEndTime() != null) {
            timeBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));
        }
        timeBlock.setUpdatedAt(new Date());

        EmployeeScheduleTimeBlock updatedBlock = timeBlockRepository.save(timeBlock);
        return createTimeBlockResponse(updatedBlock, "UPDATED");
    }

    @Transactional
    public Map<String, Object> updateTimeBlocksByDependency(List<TimeBlockDependencyDTO> timeBlockDTOList) {
        if (timeBlockDTOList == null || timeBlockDTOList.isEmpty()) {
            throw new IllegalArgumentException("No se proporcionaron bloques de tiempo.");
        }

        log.info("Procesando {} bloques de tiempo", timeBlockDTOList.size());

        List<Map<String, Object>> processedBlocks = new ArrayList<>();
        Set<Long> affectedEmployees = new HashSet<>();
        int successCount = 0;
        int errorCount = 0;

        for (TimeBlockDependencyDTO timeBlockDTO : timeBlockDTOList) {
            try {
                Map<String, Object> result = processSingleTimeBlock(timeBlockDTO, affectedEmployees);
                if (result != null) {
                    processedBlocks.add(result);
                    successCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                log.error("Error procesando bloque: {}", e.getMessage(), e);
                errorCount++;
            }
        }

        // Limpiar empleados afectados
        cleanupAffectedEmployees(affectedEmployees);

        return createBulkUpdateResponse(processedBlocks, successCount, errorCount, affectedEmployees.size());
    }

    @Transactional
    public Map<String, Object> deleteTimeBlock(Long timeBlockId) {
        EmployeeScheduleTimeBlock block = timeBlockRepository
                .findById(timeBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeBlock no encontrado: " + timeBlockId));

        Long dayId = block.getEmployeeScheduleDay().getId();
        timeBlockRepository.deleteById(timeBlockId);

        // Verificar si el día quedó vacío
        List<EmployeeScheduleTimeBlock> remainingBlocks =
                timeBlockRepository.findByEmployeeScheduleDayId(dayId);

        if (remainingBlocks.isEmpty()) {
            employeeScheduleDayRepository.deleteById(dayId);
            log.info("Día eliminado por quedar vacío: {}", dayId);
        }

        return Map.of(
                "success", true,
                "message", "Bloque eliminado correctamente",
                "deletedId", timeBlockId,
                "dayCleanedUp", remainingBlocks.isEmpty()
        );
    }

    @Transactional
    public Map<String, Object> deleteCompleteScheduleDay(Long dayId) {
        EmployeeScheduleDay day = findDayOrThrow(dayId);
        Date dayDate = day.getDate();

        // Eliminar todos los timeBlocks del día
        timeBlockRepository.deleteByEmployeeScheduleDayId(dayId);

        // Eliminar el día
        employeeScheduleDayRepository.deleteById(dayId);

        return Map.of(
                "success", true,
                "message", "Día y sus horarios eliminados completamente",
                "dayId", dayId,
                "date", dayDate
        );
    }

    // =================== MÉTODOS PRIVADOS ===================

    private Map<String, Object> processSingleTimeBlock(TimeBlockDependencyDTO timeBlockDTO, Set<Long> affectedEmployees) {
        if (timeBlockDTO.getEmployeeScheduleDayId() == null || timeBlockDTO.getEmployeeScheduleDayId() <= 0) {
            log.error("employeeScheduleDayId inválido: {}", timeBlockDTO.getEmployeeScheduleDayId());
            return null;
        }

        EmployeeScheduleDay day = employeeScheduleDayRepository
                .findById(timeBlockDTO.getEmployeeScheduleDayId())
                .orElse(null);

        if (day == null) {
            log.error("Día no encontrado: {}", timeBlockDTO.getEmployeeScheduleDayId());
            return null;
        }

        Long employeeId = day.getEmployeeSchedule().getEmployeeId();
        affectedEmployees.add(employeeId);

        // Validar consistencia de numberId
        if (timeBlockDTO.getNumberId() != null && !timeBlockDTO.getNumberId().equals(employeeId)) {
            log.warn("NumberId ({}) no coincide con EmployeeId ({})",
                    timeBlockDTO.getNumberId(), employeeId);
        }

        boolean shouldDelete = isInvalidTimeBlock(timeBlockDTO.getStartTime(), timeBlockDTO.getEndTime());

        if (shouldDelete) {
            return processTimeBlockDeletion(timeBlockDTO, day);
        } else if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
            return processTimeBlockUpdate(timeBlockDTO);
        } else {
            return processTimeBlockCreation(timeBlockDTO, day);
        }
    }

    private Map<String, Object> processTimeBlockDeletion(TimeBlockDependencyDTO timeBlockDTO, EmployeeScheduleDay day) {
        if (timeBlockDTO.getId() != null && timeBlockDTO.getId() > 0) {
            timeBlockRepository.deleteById(timeBlockDTO.getId());

            // Verificar si el día quedó vacío
            List<EmployeeScheduleTimeBlock> remainingBlocks =
                    timeBlockRepository.findByEmployeeScheduleDayId(day.getId());

            if (remainingBlocks.isEmpty()) {
                employeeScheduleDayRepository.deleteById(day.getId());
                log.info("Día eliminado por quedar vacío: {}", day.getId());
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", timeBlockDTO.getId());
            response.put("action", "DELETED");
            response.put("numberId", timeBlockDTO.getNumberId());
            return response;
        }
        return null;
    }

    private Map<String, Object> processTimeBlockUpdate(TimeBlockDependencyDTO timeBlockDTO) {
        EmployeeScheduleTimeBlock existingBlock = timeBlockRepository
                .findById(timeBlockDTO.getId()).orElse(null);

        if (existingBlock == null) {
            log.error("Bloque no encontrado para actualizar: {}", timeBlockDTO.getId());
            return null;
        }

        existingBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
        existingBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));
        existingBlock.setUpdatedAt(new Date());

        EmployeeScheduleTimeBlock updatedBlock = timeBlockRepository.save(existingBlock);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", updatedBlock.getId());
        response.put("employeeScheduleDayId", updatedBlock.getEmployeeScheduleDay().getId());
        response.put("startTime", updatedBlock.getStartTime().toString());
        response.put("endTime", updatedBlock.getEndTime().toString());
        response.put("numberId", timeBlockDTO.getNumberId());
        response.put("action", "UPDATED");
        return response;
    }

    private Map<String, Object> processTimeBlockCreation(TimeBlockDependencyDTO timeBlockDTO, EmployeeScheduleDay day) {
        EmployeeScheduleTimeBlock newBlock = new EmployeeScheduleTimeBlock();
        newBlock.setEmployeeScheduleDay(day);
        newBlock.setStartTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getStartTime())));
        newBlock.setEndTime(Time.valueOf(normalizeTimeString(timeBlockDTO.getEndTime())));
        newBlock.setCreatedAt(new Date());

        EmployeeScheduleTimeBlock savedBlock = timeBlockRepository.save(newBlock);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", savedBlock.getId());
        response.put("employeeScheduleDayId", savedBlock.getEmployeeScheduleDay().getId());
        response.put("startTime", savedBlock.getStartTime().toString());
        response.put("endTime", savedBlock.getEndTime().toString());
        response.put("numberId", timeBlockDTO.getNumberId());
        response.put("action", "CREATED");
        return response;
    }

    private void cleanupAffectedEmployees(Set<Long> affectedEmployees) {
        for (Long employeeId : affectedEmployees) {
            try {
                employeeScheduleService.cleanupEmptyDaysForEmployee(employeeId);
                log.debug("Limpieza completada para empleado: {}", employeeId);
            } catch (Exception e) {
                log.error("Error limpiando empleado {}: {}", employeeId, e.getMessage());
            }
        }
    }

    // =================== VALIDACIONES ===================

    private void validateTimeBlockData(Map<String, Object> timeBlockData) {
        if (timeBlockData == null) {
            throw new IllegalArgumentException("Datos del bloque de tiempo son requeridos");
        }

        if (!timeBlockData.containsKey("employeeScheduleDayId") ||
                !timeBlockData.containsKey("startTime") ||
                !timeBlockData.containsKey("endTime")) {
            throw new IllegalArgumentException("Faltan campos requeridos");
        }
    }

    private void validateTimeBlockDTO(TimeBlockDTO timeBlockDTO) {
        if (timeBlockDTO == null) {
            throw new IllegalArgumentException("TimeBlockDTO es requerido");
        }
        if (timeBlockDTO.getId() == null || timeBlockDTO.getId() <= 0) {
            throw new IllegalArgumentException("ID del bloque inválido");
        }
    }

    private boolean isInvalidTimeBlock(String startTime, String endTime) {
        if (startTime == null || endTime == null) return true;

        String start = startTime.trim();
        String end = endTime.trim();

        return start.isEmpty() || end.isEmpty() ||
                start.contains("__") || end.contains("__") ||
                ("00:00:00".equals(start) && "00:00:00".equals(end));
    }

    private String normalizeTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Horario no puede estar vacío");
        }

        timeStr = timeStr.trim();

        if (timeStr.contains("__")) {
            throw new IllegalArgumentException("Horario contiene caracteres inválidos: " + timeStr);
        }

        // Formato HH:mm:ss (ya completo)
        if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return timeStr;
        }

        // Formato HH:mm (agregar segundos)
        if (timeStr.matches("\\d{2}:\\d{2}")) {
            return timeStr + ":00";
        }

        // Formato H:mm (agregar cero inicial)
        if (timeStr.matches("\\d{1}:\\d{2}")) {
            return "0" + timeStr + ":00";
        }

        throw new IllegalArgumentException("Formato de horario inválido: " + timeStr);
    }

    // =================== UTILIDADES ===================

    private EmployeeScheduleDay findDayOrThrow(Long dayId) {
        return employeeScheduleDayRepository.findById(dayId)
                .orElseThrow(() -> new ResourceNotFoundException("Día no encontrado: " + dayId));
    }

    private Long extractLong(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Campo requerido: " + key);
        }
        return Long.parseLong(value.toString());
    }

    private String extractString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Campo requerido: " + key);
        }
        return value.toString();
    }

    private Map<String, Object> createTimeBlockResponse(EmployeeScheduleTimeBlock block, String action) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", block.getId());
        response.put("employeeScheduleDayId", block.getEmployeeScheduleDay().getId());
        response.put("startTime", block.getStartTime().toString());
        response.put("endTime", block.getEndTime().toString());
        response.put("action", action);
        response.put("message", "Bloque procesado correctamente");
        return response;
    }

    private Map<String, Object> createBulkUpdateResponse(List<Map<String, Object>> processedBlocks,
                                                         int successCount, int errorCount, int affectedEmployees) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Bloques procesados correctamente");
        response.put("processedCount", successCount);
        response.put("errorCount", errorCount);
        response.put("processedBlocks", processedBlocks);
        response.put("affectedEmployees", affectedEmployees);
        return response;
    }
}
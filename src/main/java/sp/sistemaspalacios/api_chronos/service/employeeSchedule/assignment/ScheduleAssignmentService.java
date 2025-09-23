package sp.sistemaspalacios.api_chronos.service.employeeSchedule.assignment;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeHoursSummaryDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleDto.*;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.ScheduleCalculationService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayExemptionService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday.HolidayProcessingService;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.time.ScheduleDayGeneratorService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
 @Service
 public class ScheduleAssignmentService {

     private final EmployeeScheduleRepository employeeScheduleRepository;
     private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
     private final ShiftsRepository shiftsRepository;
     private final ScheduleValidationService scheduleValidationService;
     private final HolidayProcessingService holidayProcessingService;
     private final ScheduleDayGeneratorService scheduleDayGeneratorService;
     private final ScheduleAssignmentGroupService groupService;
     private final HolidayExemptionService holidayExemptionService;
     private final ScheduleCalculationService scheduleCalculationService;

     public ScheduleAssignmentService(
             EmployeeScheduleRepository employeeScheduleRepository,
             EmployeeScheduleDayRepository employeeScheduleDayRepository,
             ShiftsRepository shiftsRepository,
             ScheduleValidationService scheduleValidationService,
             HolidayProcessingService holidayProcessingService,
             ScheduleDayGeneratorService scheduleDayGeneratorService,
             ScheduleAssignmentGroupService groupService,
             HolidayExemptionService holidayExemptionService,
             ScheduleCalculationService scheduleCalculationService
     ) {
         this.employeeScheduleRepository = employeeScheduleRepository;
         this.employeeScheduleDayRepository = employeeScheduleDayRepository;
         this.shiftsRepository = shiftsRepository;
         this.scheduleValidationService = scheduleValidationService;
         this.holidayProcessingService = holidayProcessingService;
         this.scheduleDayGeneratorService = scheduleDayGeneratorService;
         this.groupService = groupService;
         this.holidayExemptionService = holidayExemptionService;
         this.scheduleCalculationService = scheduleCalculationService;
     }


     @Transactional
     public AssignmentResult processHolidayAssignment(HolidayConfirmationRequest request) {
         try {
             System.out.println("=== PROCESS HOLIDAY ASSIGNMENT START ===");
             System.out.println("Request: " + request);

             if (request == null || request.getConfirmedAssignments() == null || request.getConfirmedAssignments().isEmpty()) {
                 throw new IllegalArgumentException("confirmedAssignments es requerido");
             }

             List<EmployeeSchedule> created = new ArrayList<>();
             for (int i = 0; i < request.getConfirmedAssignments().size(); i++) {
                 ConfirmedAssignment ca = request.getConfirmedAssignments().get(i);
                 System.out.println("Processing confirmed assignment " + (i+1) + ": " + ca);

                 try {
                     shiftsRepository.findById(ca.getShiftId())
                             .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + ca.getShiftId()));

                     EmployeeSchedule s = createScheduleFromConfirmedAssignment(ca);
                     s.setDays(new ArrayList<>());
                     EmployeeSchedule saved = employeeScheduleRepository.save(s);

                     List<HolidayDecision> decisions = (ca.getHolidayDecisions() != null) ? ca.getHolidayDecisions() : Collections.emptyList();
                     System.out.println("Holiday decisions for assignment " + (i+1) + ": " + decisions.size() + " decisions");

                     scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, decisions);
                     created.add(employeeScheduleRepository.save(saved));

                 } catch (Exception e) {
                     System.err.println("Error processing confirmed assignment " + (i+1) + ": " + e.getMessage());
                     e.printStackTrace();
                     throw e;
                 }
             }

             System.out.println("=== PROCESS HOLIDAY ASSIGNMENT END ===");
             return processCreatedSchedules(created);

         } catch (Exception e) {
             System.err.println("FATAL ERROR in processHolidayAssignment: " + e.getMessage());
             e.printStackTrace();
             throw e;
         }
     }

     public ValidationResult validateAssignmentOnly(AssignmentRequest request) {
         ValidationResult result = new ValidationResult();
         List<String> errors = new ArrayList<>();

         try {
             scheduleValidationService.validateAssignmentRequest(request);

             List<ScheduleConflict> conflicts = scheduleValidationService.detectScheduleConflicts(request.getAssignments());
             if (!conflicts.isEmpty()) errors.add("Se detectaron conflictos de horarios");

             List<HolidayWarning> holidayWarnings = holidayProcessingService.detectHolidayWarnings(request.getAssignments());
             result.setHolidayWarnings(holidayWarnings);

             result.setValid(errors.isEmpty());
             result.setErrors(errors);
         } catch (Exception e) {
             errors.add(e.getMessage());
             result.setValid(false);
             result.setErrors(errors);
         }
         return result;
     }

     @Transactional
     public List<EmployeeSchedule> createMultipleSchedules(List<EmployeeSchedule> schedules) {
         List<EmployeeSchedule> savedSchedules = new ArrayList<>();
         Long commonDaysParentId = null;

         for (EmployeeSchedule schedule : schedules) {
             validateSchedule(schedule);
             schedule.setCreatedAt(new Date());

             Shifts shift = shiftsRepository.findById(schedule.getShift().getId())
                     .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));
             schedule.setShift(shift);

             scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(schedule, Collections.emptyList());

             EmployeeSchedule savedSchedule = employeeScheduleRepository.save(schedule);

             List<EmployeeScheduleDay> savedDays = new ArrayList<>();
             for (EmployeeScheduleDay day : savedSchedule.getDays()) {
                 day.setEmployeeSchedule(savedSchedule);
                 if (commonDaysParentId == null) commonDaysParentId = savedSchedule.getId();
                 day.setDaysParentId(commonDaysParentId);
                 EmployeeScheduleDay savedDay = employeeScheduleDayRepository.save(day);
                 savedDays.add(savedDay);
             }

             savedSchedule.setDays(savedDays);
             savedSchedule.setDaysParentId(commonDaysParentId);
             employeeScheduleRepository.save(savedSchedule);

             savedSchedules.add(savedSchedule);
         }

         return savedSchedules;
     }

     // =================== MÉTODOS PRIVADOS ===================

     private AssignmentResult processCreatedSchedules(List<EmployeeSchedule> created) {
         try {
             System.out.println("=== PROCESS CREATED SCHEDULES START ===");
             System.out.println("Created schedules count: " + created.size());

             if (created == null || created.isEmpty()) {
                 System.out.println("No hay schedules creados para procesar");
                 AssignmentResult result = new AssignmentResult();
                 result.setSuccess(true);
                 result.setMessage("No hay horarios para procesar");
                 result.setUpdatedEmployees(Collections.emptyList());
                 result.setRequiresConfirmation(false);
                 return result;
             }

             // Agrupar por empleado de forma segura
             Map<Long, List<Long>> idsPorEmpleado = new HashMap<>();

             for (EmployeeSchedule schedule : created) {
                 try {
                     if (schedule != null && schedule.getEmployeeId() != null && schedule.getId() != null) {
                         idsPorEmpleado.computeIfAbsent(schedule.getEmployeeId(), k -> new ArrayList<>())
                                 .add(schedule.getId());
                     } else {
                         System.err.println("Schedule inválido encontrado: " + schedule);
                     }
                 } catch (Exception e) {
                     System.err.println("Error procesando schedule individual: " + e.getMessage());
                     // Continuar con el siguiente schedule
                 }
             }

             System.out.println("Employee groups: " + idsPorEmpleado);

             // Procesar cada empleado de forma segura
             List<Object> summaries = new ArrayList<>();

             for (Map.Entry<Long, List<Long>> entry : idsPorEmpleado.entrySet()) {
                 Long empId = entry.getKey();
                 List<Long> scheduleIds = entry.getValue();

                 try {
                     System.out.println("Processing group service for employee: " + empId + " with schedules: " + scheduleIds);

                     // PROCESO PRINCIPAL CON MANEJO DE ERRORES
                     groupService.processScheduleAssignment(empId, scheduleIds);
                     System.out.println("Group service processed successfully for employee: " + empId);

                     // Backfill de exenciones con manejo de errores
                     try {
                         System.out.println("Backfilling group IDs for employee: " + empId);
                         List<
                                 ScheduleAssignmentGroupDTO> employeeGroups = groupService.getEmployeeGroups(empId);
                         holidayExemptionService.backfillGroupIds(empId, employeeGroups);
                         System.out.println("Backfill completed for employee: " + empId);
                     } catch (Exception e) {
                         System.err.println("No se pudo enlazar exenciones a grupos para empId " + empId + ": " + e.getMessage());
                         // No lanzar excepción - esto es opcional
                     }

                     // Calcular resumen con manejo de errores
                     try {
                         System.out.println("Calculating summary for employee: " + empId);
                         EmployeeHoursSummaryDTO summary = scheduleCalculationService.calculateEmployeeHoursSummary(empId);
                         if (summary != null) {
                             summaries.add(summary);
                             System.out.println("Summary calculated successfully for employee: " + empId);
                         } else {
                             System.err.println("Summary es null para empleado: " + empId);
                             summaries.add(createEmptySummary(empId));
                         }
                     } catch (Exception ex) {
                         System.err.println("Error calculating summary for employee " + empId + ": " + ex.getMessage());
                         ex.printStackTrace();
                         summaries.add(createEmptySummary(empId));
                     }

                 } catch (Exception e) {
                     System.err.println("ERROR CRÍTICO processing group for employee " + empId + ": " + e.getMessage());
                     e.printStackTrace();

                     // DECISIÓN: ¿Fallar todo o continuar con otros empleados?
                     // Por ahora, continuamos con otros empleados y agregamos un resumen vacío
                     summaries.add(createEmptySummaryWithError(empId, e.getMessage()));

                     // Si quieres que falle todo, descomenta la siguiente línea:
                     // throw new RuntimeException("Error processing group for employee " + empId, e);
                 }
             }

             System.out.println("Creating result object...");
             AssignmentResult result = new AssignmentResult();
             result.setSuccess(true);
             result.setMessage("Turnos asignados correctamente" +
                     (summaries.size() < idsPorEmpleado.size() ? " con algunos errores" : ""));
             result.setUpdatedEmployees(summaries);
             result.setRequiresConfirmation(false);
             result.setHolidayWarnings(new ArrayList<>());

             System.out.println("=== PROCESS CREATED SCHEDULES END ===");
             return result;

         } catch (Exception e) {
             System.err.println("FATAL ERROR in processCreatedSchedules: " + e.getClass().getName());
             System.err.println("FATAL ERROR message: " + e.getMessage());
             e.printStackTrace();

             // Devolver resultado de error estructurado
             AssignmentResult errorResult = new AssignmentResult();
             errorResult.setSuccess(false);
             errorResult.setMessage("Error procesando horarios: " + e.getMessage());
             errorResult.setUpdatedEmployees(Collections.emptyList());
             errorResult.setRequiresConfirmation(false);
             errorResult.setHolidayWarnings(new ArrayList<>());

             throw new RuntimeException("Error processing created schedules", e);
         }
     }

     // MÉTODO para crear resumen vacío en caso de error
     private EmployeeHoursSummaryDTO createEmptySummaryWithError(Long empId, String errorMessage) {
         EmployeeHoursSummaryDTO empty = createEmptySummary(empId);
         return empty;
     }
     private EmployeeSchedule createScheduleFromAssignment(ScheduleDto.ScheduleAssignment assignment) {
         System.out.println("=== CREANDO SCHEDULE ===");
         System.out.println("Assignment startDate: " + assignment.getStartDate());
         System.out.println("Assignment endDate: " + assignment.getEndDate());

         // Validaciones básicas
         if (assignment == null) {
             throw new IllegalArgumentException("El cuerpo de la solicitud es requerido");
         }
         if (assignment.getEmployeeId() == null || assignment.getEmployeeId() <= 0) {
             throw new IllegalArgumentException("Employee ID requerido y debe ser > 0");
         }
         if (assignment.getShiftId() == null || assignment.getShiftId() <= 0) {
             throw new IllegalArgumentException("Shift ID requerido y debe ser > 0");
         }
         if (assignment.getStartDate() == null) {
             throw new IllegalArgumentException("Fecha de inicio requerida");
         }
         if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(assignment.getStartDate())) {
             throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
         }

         // ✅ VALIDACIÓN MEJORADA DE FECHAS PASADAS
         java.time.LocalDate today = java.time.LocalDate.now();

         // Solo advertir, no bloquear completamente
         if (assignment.getStartDate().isBefore(today)) {
             System.out.println("⚠️ ADVERTENCIA: Asignando fechas pasadas - " +
                     assignment.getStartDate() + " es anterior a " + today);
             // Permitir continuar pero logear la advertencia
         }

         System.out.println("✅ Validación de fechas completada para empleado: " + assignment.getEmployeeId());

         // Cargar shift
         Shifts shift = getShiftOrThrow(assignment.getShiftId());

         // Construcción del schedule
         EmployeeSchedule schedule = new EmployeeSchedule();
         schedule.setEmployeeId(assignment.getEmployeeId());
         schedule.setShift(shift);
         schedule.setStartDate(assignment.getStartDate());
         schedule.setEndDate(assignment.getEndDate());

         if (schedule.getCreatedAt() == null) {
             schedule.setCreatedAt(new java.util.Date());
         }
         schedule.setUpdatedAt(new java.util.Date());

         validateSchedule(schedule);
         return schedule;
     }
     private Shifts getShiftOrThrow(Long shiftId) {
         return shiftsRepository.findById(shiftId)
                 .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + shiftId));
     }

     private EmployeeSchedule createScheduleFromConfirmedAssignment(ConfirmedAssignment assignment) {
         // VALIDACIÓN DE FECHAS PASADAS
         java.time.LocalDate today = java.time.LocalDate.now();

         if (assignment.getStartDate().isBefore(today)) {
             throw new IllegalArgumentException(String.format(
                     "No se puede asignar fechas pasadas. La fecha de inicio %s es anterior a hoy (%s)",
                     assignment.getStartDate(), today
             ));
         }

         if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(today)) {
             throw new IllegalArgumentException(String.format(
                     "No se puede asignar fechas pasadas. La fecha de fin %s es anterior a hoy (%s)",
                     assignment.getEndDate(), today
             ));
         }

         System.out.println("✅ Validación de fechas pasada - OK para asignación confirmada empleado: " + assignment.getEmployeeId());

         EmployeeSchedule schedule = new EmployeeSchedule();
         schedule.setEmployeeId(assignment.getEmployeeId());

         Shifts shift = shiftsRepository.findById(assignment.getShiftId())
                 .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + assignment.getShiftId()));
         schedule.setShift(shift);

         schedule.setStartDate(assignment.getStartDate());
         schedule.setEndDate(assignment.getEndDate());
         schedule.setCreatedAt(new Date());
         return schedule;
     }
     private EmployeeHoursSummaryDTO createEmptySummary(Long empId) {
         var empty = new EmployeeHoursSummaryDTO();
         empty.setEmployeeId(empId);
         empty.setTotalHours(0.0);
         empty.setAssignedHours(0.0);
         empty.setOvertimeHours(0.0);
         empty.setOvertimeType("Normal");
         empty.setFestivoHours(0.0);
         empty.setFestivoType(null);
         empty.setOvertimeBreakdown(new HashMap<>());
         return empty;
     }

     private void validateSchedule(EmployeeSchedule schedule) {
         List<String> errors = new ArrayList<>();

         if (schedule.getEmployeeId() == null || schedule.getEmployeeId() <= 0) {
             errors.add("Employee ID es obligatorio y debe ser un número válido.");
         }
         if (schedule.getShift() == null || schedule.getShift().getId() == null || schedule.getShift().getId() <= 0) {
             errors.add("Shift ID es obligatorio y debe ser un número válido.");
         }
         if (schedule.getStartDate() == null) {
             errors.add("La fecha de inicio es obligatoria.");
         }
         if (schedule.getEndDate() != null && schedule.getStartDate() != null
                 && schedule.getStartDate().isAfter(schedule.getEndDate())) {
             errors.add("La fecha de inicio debe ser anterior a la fecha de fin.");
         }

         if (!errors.isEmpty()) {
             throw new ScheduleDto.ValidationException("Errores de validación", errors);
         }
     }




























     public AssignmentResult processMultipleAssignments(AssignmentRequest request) {
         try {
             System.out.println("=== PROCESS MULTIPLE ASSIGNMENTS START ===");

             scheduleValidationService.validateAssignmentRequest(request);
             System.out.println("✅ Validation passed");

             // VALIDACIÓN SIMPLE: Solo rechazar si es la misma fecha con horas solapadas
             List<ScheduleConflict> conflicts = detectSameDateConflicts(request.getAssignments());
             if (!conflicts.isEmpty()) {
                 System.out.println("❌ CONFLICTOS EN MISMA FECHA:");
                 conflicts.forEach(conflict -> {
                     System.out.println("  - " + conflict.getMessage());
                 });
                 throw new ConflictException("No se pueden asignar turnos con horarios solapados en la misma fecha", conflicts);
             }
             System.out.println("✅ No same-date conflicts detected");

             List<HolidayWarning> holidayWarnings = holidayProcessingService.detectHolidayWarnings(request.getAssignments());
             if (!holidayWarnings.isEmpty()) {
                 System.out.println("⚠️ Holiday warnings detected, returning confirmation request");
                 AssignmentResult preview = new AssignmentResult();
                 preview.setSuccess(false);
                 preview.setMessage("Se detectaron días festivos");
                 preview.setHolidayWarnings(holidayWarnings);
                 preview.setRequiresConfirmation(true);
                 return preview;
             }
             System.out.println("✅ No holiday warnings");

             List<EmployeeSchedule> created = new ArrayList<>();
             for (int i = 0; i < request.getAssignments().size(); i++) {
                 ScheduleAssignment a = request.getAssignments().get(i);
                 System.out.println("📝 Processing assignment " + (i+1) + ": " + a);

                 try {
                     EmployeeSchedule s = createScheduleFromAssignment(a);
                     System.out.println("✅ Schedule created: " + s.getId());

                     s.setDays(new ArrayList<>());
                     EmployeeSchedule saved = employeeScheduleRepository.save(s);
                     System.out.println("✅ Schedule saved with ID: " + saved.getId());

                     System.out.println("🔄 Generating schedule days...");
                     scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, Collections.emptyList());
                     System.out.println("✅ Schedule days generated");

                     EmployeeSchedule finalSaved = employeeScheduleRepository.save(saved);
                     System.out.println("✅ Final save completed for schedule: " + finalSaved.getId());

                     created.add(finalSaved);

                 } catch (Exception e) {
                     System.err.println("❌ Error processing assignment " + (i+1) + ": " + e.getClass().getName());
                     System.err.println("❌ Error message: " + e.getMessage());
                     e.printStackTrace();
                     throw e;
                 }
             }

             employeeScheduleRepository.flush();
             System.out.println("✅ Repository flushed");

             System.out.println("🔄 Processing created schedules...");
             AssignmentResult result = processCreatedSchedules(created);
             System.out.println("✅ Processing completed");

             System.out.println("=== PROCESS MULTIPLE ASSIGNMENTS END ===");
             return result;

         } catch (Exception e) {
             System.err.println("❌ FATAL ERROR in processMultipleAssignments: " + e.getClass().getName());
             System.err.println("❌ FATAL ERROR message: " + e.getMessage());
             e.printStackTrace();
             throw e;
         }
     }


     // REEMPLAZAR el método hasTimeOverlapOnDateBetweenAssignments en ScheduleAssignmentService.java
     private boolean hasTimeOverlapOnDateBetweenAssignments(ScheduleAssignment a1, ScheduleAssignment a2, LocalDate date) {
         Shifts shift1 = shiftsRepository.findById(a1.getShiftId()).orElse(null);
         Shifts shift2 = shiftsRepository.findById(a2.getShiftId()).orElse(null);

         if (shift1 == null || shift2 == null) {
             return false;
         }

         // ✅ Si es el mismo turno, no hay conflicto
         if (Objects.equals(shift1.getId(), shift2.getId())) {
             System.out.println("✅ Mismo turno entre asignaciones - NO HAY CONFLICTO");
             return false;
         }

         int dayOfWeek = date.getDayOfWeek().getValue();

         List<ShiftDetail> details1 = getShiftDetailsForDay(shift1, dayOfWeek);
         List<ShiftDetail> details2 = getShiftDetailsForDay(shift2, dayOfWeek);

         if (details1.isEmpty() || details2.isEmpty()) {
             return false;
         }

         // Verificar solapamiento real de horarios
         for (ShiftDetail detail1 : details1) {
             for (ShiftDetail detail2 : details2) {
                 if (timesActuallyOverlap(detail1, detail2)) {
                     return true;
                 }
             }
         }

         return false;
     }

     // Método para obtener detalles de un turno para un día específico
     private List<ShiftDetail> getShiftDetailsForDay(Shifts shift, int dayOfWeek) {
         if (shift == null || shift.getShiftDetails() == null) {
             return Collections.emptyList();
         }

         return shift.getShiftDetails().stream()
                 .filter(detail -> Objects.equals(detail.getDayOfWeek(), dayOfWeek))
                 .filter(detail -> detail.getStartTime() != null && detail.getEndTime() != null)
                 .filter(detail -> !detail.getStartTime().trim().isEmpty() && !detail.getEndTime().trim().isEmpty())
                 .collect(Collectors.toList());
     }


     // REEMPLAZAR el método detectSameDateConflicts en ScheduleAssignmentService.java
     private List<ScheduleConflict> detectSameDateConflicts(List<ScheduleAssignment> assignments) {
         System.out.println("🔍 === INICIANDO DETECCIÓN DE CONFLICTOS MEJORADA ===");
         System.out.println("Asignaciones recibidas: " + assignments.size());

         List<ScheduleConflict> conflicts = new ArrayList<>();

         // Agrupar por empleado
         Map<Long, List<ScheduleAssignment>> byEmployee = assignments.stream()
                 .collect(Collectors.groupingBy(ScheduleAssignment::getEmployeeId));

         System.out.println("Empleados a procesar: " + byEmployee.keySet());

         for (Map.Entry<Long, List<ScheduleAssignment>> entry : byEmployee.entrySet()) {
             Long employeeId = entry.getKey();
             List<ScheduleAssignment> employeeAssignments = entry.getValue();

             System.out.println("🔍 === VALIDANDO EMPLEADO " + employeeId + " ===");

             // 1. Verificar conflictos con turnos existentes
             List<EmployeeSchedule> existingSchedules = employeeScheduleRepository.findByEmployeeId(employeeId);
             System.out.println("Turnos existentes: " + existingSchedules.size());

             for (ScheduleAssignment newAssignment : employeeAssignments) {
                 LocalDate newStart = newAssignment.getStartDate();
                 LocalDate newEnd = (newAssignment.getEndDate() != null) ? newAssignment.getEndDate() : newStart;

                 System.out.println("🔍 Procesando nueva asignación:");
                 System.out.println("  Turno: " + newAssignment.getShiftId() + ", Fechas: " + newStart + " al " + newEnd);

                 // Verificar contra cada turno existente
                 for (EmployeeSchedule existing : existingSchedules) {
                     LocalDate existingStart = existing.getStartDate();
                     LocalDate existingEnd = (existing.getEndDate() != null) ? existing.getEndDate() : existingStart;

                     // ✅ VERIFICAR SOLO SI HAY SOLAPAMIENTO DE FECHAS
                     if (datesOverlap(newStart, newEnd, existingStart, existingEnd)) {
                         System.out.println("  📅 Fechas se solapan con turno existente ID: " + existing.getId());

                         // ✅ VERIFICAR CONFLICTO DE HORARIOS EN FECHAS COMUNES
                         LocalDate overlapStart = Collections.max(Arrays.asList(newStart, existingStart));
                         LocalDate overlapEnd = Collections.min(Arrays.asList(newEnd, existingEnd));

                         boolean hasRealConflict = false;
                         LocalDate conflictDate = null;

                         // Verificar cada fecha en el período de solapamiento
                         for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
                             if (hasTimeOverlapOnDate(newAssignment, existing, date)) {
                                 hasRealConflict = true;
                                 conflictDate = date;
                                 break; // Solo necesitamos encontrar un conflicto
                             }
                         }

                         if (hasRealConflict) {
                             System.out.println("❌ CONFLICTO REAL detectado en fecha: " + conflictDate);
                             ScheduleConflict conflict = new ScheduleConflict();
                             conflict.setEmployeeId(employeeId);
                             conflict.setConflictDate(conflictDate);
                             conflict.setMessage("Conflicto de horarios: el empleado ya tiene un turno con horarios que se solapan en la fecha " +
                                     conflictDate + " (Turno existente: " + existing.getShift().getId() +
                                     " vs Turno nuevo: " + newAssignment.getShiftId() + ")");
                             conflicts.add(conflict);
                         } else {
                             System.out.println("✅ No hay conflicto de horarios - turnos en diferentes horarios");
                         }
                     } else {
                         System.out.println("  ✅ No hay solapamiento de fechas");
                     }
                 }
             }

             // 2. Verificar conflictos entre nuevas asignaciones del mismo empleado
             if (employeeAssignments.size() > 1) {
                 System.out.println("🔍 Verificando conflictos entre " + employeeAssignments.size() + " nuevas asignaciones...");

                 for (int i = 0; i < employeeAssignments.size(); i++) {
                     for (int j = i + 1; j < employeeAssignments.size(); j++) {
                         ScheduleAssignment a1 = employeeAssignments.get(i);
                         ScheduleAssignment a2 = employeeAssignments.get(j);

                         LocalDate start1 = a1.getStartDate();
                         LocalDate end1 = (a1.getEndDate() != null) ? a1.getEndDate() : start1;
                         LocalDate start2 = a2.getStartDate();
                         LocalDate end2 = (a2.getEndDate() != null) ? a2.getEndDate() : start2;

                         if (datesOverlap(start1, end1, start2, end2)) {
                             // Verificar solapamiento de horarios
                             LocalDate overlapStart = Collections.max(Arrays.asList(start1, start2));
                             LocalDate overlapEnd = Collections.min(Arrays.asList(end1, end2));

                             for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
                                 if (hasTimeOverlapOnDateBetweenAssignments(a1, a2, date)) {
                                     System.out.println("❌ Conflicto entre nuevas asignaciones en fecha: " + date);
                                     ScheduleConflict conflict = new ScheduleConflict();
                                     conflict.setEmployeeId(employeeId);
                                     conflict.setConflictDate(date);
                                     conflict.setMessage("Conflicto entre asignaciones: el empleado tiene dos turnos con horarios que se solapan en la fecha " +
                                             date + " (Turno " + a1.getShiftId() + " vs Turno " + a2.getShiftId() + ")");
                                     conflicts.add(conflict);
                                     break; // Solo agregar el primer conflicto
                                 }
                             }
                         }
                     }
                 }
             }
         }

         System.out.println("🔍 === FIN DETECCIÓN DE CONFLICTOS ===");
         System.out.println("📊 RESULTADO FINAL: " + conflicts.size() + " conflictos encontrados");

         return conflicts;
     }

     // Método auxiliar para verificar solapamiento de fechas
     private boolean datesOverlap(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
         return !start1.isAfter(end2) && !start2.isAfter(end1);
     }     // MÉTODO COMPLETO CORREGIDO: hasTimeOverlapOnDate
     private boolean hasTimeOverlapOnDate(ScheduleAssignment assignment, EmployeeSchedule existing, LocalDate date) {
         System.out.println("🔍 Verificando solapamiento para fecha: " + date);

         Shifts newShift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
         Shifts existingShift = existing.getShift();

         if (newShift == null || existingShift == null) {
             System.out.println("❌ Uno de los turnos es null");
             return false;
         }

         System.out.println("  Nuevo turno ID: " + newShift.getId());
         System.out.println("  Turno existente ID: " + existingShift.getId());

         // ✅ NUEVA LÓGICA: Si es el mismo turno, permitir siempre
         if (Objects.equals(newShift.getId(), existingShift.getId())) {
             System.out.println("✅ Es el mismo turno - NO HAY CONFLICTO");
             return false;
         }

         int dayOfWeek = date.getDayOfWeek().getValue();
         System.out.println("  Día de la semana: " + dayOfWeek + " (" + date.getDayOfWeek() + ")");

         List<ShiftDetail> newDetails = getShiftDetailsForDay(newShift, dayOfWeek);
         List<ShiftDetail> existingDetails = getShiftDetailsForDay(existingShift, dayOfWeek);

         System.out.println("  Detalles nuevo turno: " + newDetails.size() + " horarios");
         System.out.println("  Detalles turno existente: " + existingDetails.size() + " horarios");

         // Si alguno no tiene horarios definidos para este día, no hay conflicto
         if (newDetails.isEmpty() || existingDetails.isEmpty()) {
             System.out.println("✅ No hay conflicto - uno de los turnos no trabaja este día");
             return false;
         }

         // ✅ MEJORA: Solo verificar solapamiento real de horarios
         boolean hasOverlap = false;
         for (ShiftDetail newDetail : newDetails) {
             for (ShiftDetail existingDetail : existingDetails) {
                 if (timesActuallyOverlap(newDetail, existingDetail)) {
                     System.out.println("❌ CONFLICTO DETECTADO!");
                     System.out.println("  Nuevo: " + newDetail.getStartTime() + " - " + newDetail.getEndTime());
                     System.out.println("  Existente: " + existingDetail.getStartTime() + " - " + existingDetail.getEndTime());
                     hasOverlap = true;
                     break;
                 }
             }
             if (hasOverlap) break;
         }

         if (!hasOverlap) {
             System.out.println("✅ No hay solapamiento de horarios - turnos en horarios diferentes");
         }

         return hasOverlap;
     }

     private boolean timesActuallyOverlap(ShiftDetail detail1, ShiftDetail detail2) {
         if (detail1.getStartTime() == null || detail1.getEndTime() == null ||
                 detail2.getStartTime() == null || detail2.getEndTime() == null) {
             return false;
         }

         try {
             java.time.LocalTime start1 = java.time.LocalTime.parse(detail1.getStartTime());
             java.time.LocalTime end1 = java.time.LocalTime.parse(detail1.getEndTime());
             java.time.LocalTime start2 = java.time.LocalTime.parse(detail2.getStartTime());
             java.time.LocalTime end2 = java.time.LocalTime.parse(detail2.getEndTime());

             // ✅ LÓGICA MEJORADA: Solo hay conflicto si hay solapamiento REAL
             // Casos SIN conflicto:
             // 1. Turno 1 termina antes o cuando empieza turno 2
             // 2. Turno 2 termina antes o cuando empieza turno 1

             boolean noOverlap = end1.isBefore(start2) || end1.equals(start2) ||
                     end2.isBefore(start1) || end2.equals(start1);

             System.out.println("    Turno 1: " + start1 + " - " + end1);
             System.out.println("    Turno 2: " + start2 + " - " + end2);
             System.out.println("    ¿No se solapan? " + noOverlap);

             return !noOverlap; // Hay solapamiento si NO están separados

         } catch (java.time.format.DateTimeParseException e) {
             System.err.println("❌ Error parsing time: " + e.getMessage());
             return false;
         }
     }


     // MÉTODO COMPLETO CORREGIDO: timesOverlap
     private boolean timesOverlap(ShiftDetail detail1, ShiftDetail detail2) {
         if (detail1.getStartTime() == null || detail1.getEndTime() == null ||
                 detail2.getStartTime() == null || detail2.getEndTime() == null) {
             System.out.println("❌ Uno de los horarios es null");
             return false;
         }

         try {
             java.time.LocalTime start1 = java.time.LocalTime.parse(detail1.getStartTime());
             java.time.LocalTime end1 = java.time.LocalTime.parse(detail1.getEndTime());
             java.time.LocalTime start2 = java.time.LocalTime.parse(detail2.getStartTime());
             java.time.LocalTime end2 = java.time.LocalTime.parse(detail2.getEndTime());

             // DEBUG: Logging detallado
             System.out.println("🔍 === COMPARANDO HORARIOS ===");
             System.out.println("  Turno 1: " + start1 + " - " + end1);
             System.out.println("  Turno 2: " + start2 + " - " + end2);

             // Verificar condiciones específicas
             boolean end1BeforeStart2 = end1.isBefore(start2);
             boolean start1AfterEnd2 = start1.isAfter(end2);
             boolean end1EqualsStart2 = end1.equals(start2);
             boolean start1EqualsEnd2 = start1.equals(end2);

             System.out.println("  end1.isBefore(start2): " + end1BeforeStart2 + " (" + end1 + " < " + start2 + ")");
             System.out.println("  start1.isAfter(end2): " + start1AfterEnd2 + " (" + start1 + " > " + end2 + ")");
             System.out.println("  end1.equals(start2): " + end1EqualsStart2 + " (" + end1 + " == " + start2 + ")");
             System.out.println("  start1.equals(end2): " + start1EqualsEnd2 + " (" + start1 + " == " + end2 + ")");

             boolean noOverlap = end1BeforeStart2 || start1AfterEnd2 || end1EqualsStart2 || start1EqualsEnd2;
             boolean overlap = !noOverlap;

             System.out.println("  ¿NO se solapan? " + noOverlap);
             System.out.println("  ¿SÍ se solapan? " + overlap);
             System.out.println("🔍 === FIN COMPARACIÓN ===");

             return overlap;

         } catch (java.time.format.DateTimeParseException e) {
             System.err.println("❌ Error parsing time: " + e.getMessage());
             System.err.println("  detail1.getStartTime(): " + detail1.getStartTime());
             System.err.println("  detail1.getEndTime(): " + detail1.getEndTime());
             System.err.println("  detail2.getStartTime(): " + detail2.getStartTime());
             System.err.println("  detail2.getEndTime(): " + detail2.getEndTime());
             return false; // En caso de error, asumir que no hay solapamiento
         }
     }




 }
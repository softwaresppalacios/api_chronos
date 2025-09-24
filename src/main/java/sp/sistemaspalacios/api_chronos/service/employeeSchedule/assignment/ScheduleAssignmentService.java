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
             if (request == null || request.getConfirmedAssignments() == null || request.getConfirmedAssignments().isEmpty()) {
                 throw new IllegalArgumentException("confirmedAssignments es requerido");
             }
             List<EmployeeSchedule> created = new ArrayList<>();
             for (int i = 0; i < request.getConfirmedAssignments().size(); i++) {
                 ConfirmedAssignment ca = request.getConfirmedAssignments().get(i);
                 try {
                     shiftsRepository.findById(ca.getShiftId())
                             .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado: " + ca.getShiftId()));

                     EmployeeSchedule s = createScheduleFromConfirmedAssignment(ca);
                     s.setDays(new ArrayList<>());
                     EmployeeSchedule saved = employeeScheduleRepository.save(s);

                     List<HolidayDecision> decisions = (ca.getHolidayDecisions() != null) ? ca.getHolidayDecisions() : Collections.emptyList();
                     scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, decisions);
                     created.add(employeeScheduleRepository.save(saved));

                 } catch (Exception e) {
                     System.err.println("Error processing confirmed assignment " + (i+1) + ": " + e.getMessage());
                     e.printStackTrace();
                     throw e;
                 }
             }
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



     private AssignmentResult processCreatedSchedules(List<EmployeeSchedule> created) {
         try {
             if (created == null || created.isEmpty()) {
                 AssignmentResult result = new AssignmentResult();
                 result.setSuccess(true);
                 result.setMessage("No hay horarios para procesar");
                 result.setUpdatedEmployees(Collections.emptyList());
                 result.setRequiresConfirmation(false);
                 return result;
             }
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
             // Procesar cada empleado de forma segura
             List<Object> summaries = new ArrayList<>();

             for (Map.Entry<Long, List<Long>> entry : idsPorEmpleado.entrySet()) {
                 Long empId = entry.getKey();
                 List<Long> scheduleIds = entry.getValue();

                 try {
                     groupService.processScheduleAssignment(empId, scheduleIds);
                     try {
                         List<ScheduleAssignmentGroupDTO> employeeGroups = groupService.getEmployeeGroups(empId);
                         holidayExemptionService.backfillGroupIds(empId, employeeGroups);
                     } catch (Exception e) {
                         System.err.println("No se pudo enlazar exenciones a grupos para empId " + empId + ": " + e.getMessage());
                     }
                     try {
                         EmployeeHoursSummaryDTO summary = scheduleCalculationService.calculateEmployeeHoursSummary(empId);
                         if (summary != null) {
                             summaries.add(summary);
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

                     summaries.add(createEmptySummaryWithError(empId, e.getMessage()));

                 }
             }
             AssignmentResult result = new AssignmentResult();
             result.setSuccess(true);
             result.setMessage("Turnos asignados correctamente" +
                     (summaries.size() < idsPorEmpleado.size() ? " con algunos errores" : ""));
             result.setUpdatedEmployees(summaries);
             result.setRequiresConfirmation(false);
             result.setHolidayWarnings(new ArrayList<>());
             return result;

         } catch (Exception e) {
             System.err.println("FATAL ERROR in processCreatedSchedules: " + e.getClass().getName());
             System.err.println("FATAL ERROR message: " + e.getMessage());
             e.printStackTrace();
             AssignmentResult errorResult = new AssignmentResult();
             errorResult.setSuccess(false);
             errorResult.setMessage("Error procesando horarios: " + e.getMessage());
             errorResult.setUpdatedEmployees(Collections.emptyList());
             errorResult.setRequiresConfirmation(false);
             errorResult.setHolidayWarnings(new ArrayList<>());

             throw new RuntimeException("Error processing created schedules", e);
         }
     }

     private EmployeeHoursSummaryDTO createEmptySummaryWithError(Long empId, String errorMessage) {
         EmployeeHoursSummaryDTO empty = createEmptySummary(empId);
         return empty;
     }
     private EmployeeSchedule createScheduleFromAssignment(ScheduleDto.ScheduleAssignment assignment) {
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
         java.time.LocalDate today = java.time.LocalDate.now();
         if (assignment.getStartDate().isBefore(today)) {

         }
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
             scheduleValidationService.validateAssignmentRequest(request);
             List<ScheduleConflict> conflicts = detectSameDateConflicts(request.getAssignments());
             if (!conflicts.isEmpty()) {
                 conflicts.forEach(conflict -> {
                 });
                 throw new ConflictException("No se pueden asignar turnos con horarios solapados en la misma fecha", conflicts);
             }

             List<HolidayWarning> holidayWarnings = holidayProcessingService.detectHolidayWarnings(request.getAssignments());
             if (!holidayWarnings.isEmpty()) {
                 AssignmentResult preview = new AssignmentResult();
                 preview.setSuccess(false);
                 preview.setMessage("Se detectaron días festivos");
                 preview.setHolidayWarnings(holidayWarnings);
                 preview.setRequiresConfirmation(true);
                 return preview;
             }
             List<EmployeeSchedule> created = new ArrayList<>();
             for (int i = 0; i < request.getAssignments().size(); i++) {
                 ScheduleAssignment a = request.getAssignments().get(i);
                 try {
                     EmployeeSchedule s = createScheduleFromAssignment(a);
                     s.setDays(new ArrayList<>());
                     EmployeeSchedule saved = employeeScheduleRepository.save(s);
                     scheduleDayGeneratorService.generateScheduleDaysWithHolidayDecisions(saved, Collections.emptyList());
                     EmployeeSchedule finalSaved = employeeScheduleRepository.save(saved);
                     created.add(finalSaved);

                 } catch (Exception e) {
                     System.err.println("Error processing assignment " + (i+1) + ": " + e.getClass().getName());
                     System.err.println("Error message: " + e.getMessage());
                     e.printStackTrace();
                     throw e;
                 }
             }

             employeeScheduleRepository.flush();
             AssignmentResult result = processCreatedSchedules(created);
             return result;

         } catch (Exception e) {
             System.err.println("FATAL ERROR in processMultipleAssignments: " + e.getClass().getName());
             System.err.println("FATAL ERROR message: " + e.getMessage());
             e.printStackTrace();
             throw e;
         }
     }
     private boolean hasTimeOverlapOnDateBetweenAssignments(ScheduleAssignment a1, ScheduleAssignment a2, LocalDate date) {
         Shifts shift1 = shiftsRepository.findById(a1.getShiftId()).orElse(null);
         Shifts shift2 = shiftsRepository.findById(a2.getShiftId()).orElse(null);

         if (shift1 == null || shift2 == null) {
             return false;
         }
         if (Objects.equals(shift1.getId(), shift2.getId())) {
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


     private List<ScheduleConflict> detectSameDateConflicts(List<ScheduleAssignment> assignments) {
         List<ScheduleConflict> conflicts = new ArrayList<>();

         // Agrupar por empleado
         Map<Long, List<ScheduleAssignment>> byEmployee = assignments.stream()
                 .collect(Collectors.groupingBy(ScheduleAssignment::getEmployeeId));
         for (Map.Entry<Long, List<ScheduleAssignment>> entry : byEmployee.entrySet()) {
             Long employeeId = entry.getKey();
             List<ScheduleAssignment> employeeAssignments = entry.getValue();
             List<EmployeeSchedule> existingSchedules = employeeScheduleRepository.findByEmployeeId(employeeId);
             for (ScheduleAssignment newAssignment : employeeAssignments) {
                 LocalDate newStart = newAssignment.getStartDate();
                 LocalDate newEnd = (newAssignment.getEndDate() != null) ? newAssignment.getEndDate() : newStart;
                 for (EmployeeSchedule existing : existingSchedules) {
                     LocalDate existingStart = existing.getStartDate();
                     LocalDate existingEnd = (existing.getEndDate() != null) ? existing.getEndDate() : existingStart;
                     if (datesOverlap(newStart, newEnd, existingStart, existingEnd)) {
                         LocalDate overlapStart = Collections.max(Arrays.asList(newStart, existingStart));
                         LocalDate overlapEnd = Collections.min(Arrays.asList(newEnd, existingEnd));

                         boolean hasRealConflict = false;
                         LocalDate conflictDate = null;

                         // Verificar cada fecha en el período de solapamiento
                         for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
                             if (hasTimeOverlapOnDate(newAssignment, existing, date)) {
                                 hasRealConflict = true;
                                 conflictDate = date;
                                 break;
                             }
                         }
                         if (hasRealConflict) {
                             ScheduleConflict conflict = new ScheduleConflict();
                             conflict.setEmployeeId(employeeId);
                             conflict.setConflictDate(conflictDate);
                             conflict.setMessage("Conflicto de horarios: el empleado ya tiene un turno con horarios que se solapan en la fecha " +
                                     conflictDate + " (Turno existente: " + existing.getShift().getId() +
                                     " vs Turno nuevo: " + newAssignment.getShiftId() + ")");
                             conflicts.add(conflict);
                         } else {

                         }
                     } else {

                     }
                 }
             }

             // 2. Verificar conflictos entre nuevas asignaciones del mismo empleado
             if (employeeAssignments.size() > 1) {
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
                                     ScheduleConflict conflict = new ScheduleConflict();
                                     conflict.setEmployeeId(employeeId);
                                     conflict.setConflictDate(date);
                                     conflict.setMessage("Conflicto entre asignaciones: el empleado tiene dos turnos con horarios que se solapan en la fecha " +
                                             date + " (Turno " + a1.getShiftId() + " vs Turno " + a2.getShiftId() + ")");
                                     conflicts.add(conflict);
                                     break;
                                 }
                             }
                         }
                     }
                 }
             }
         }
         return conflicts;
     }
     private boolean datesOverlap(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
         return !start1.isAfter(end2) && !start2.isAfter(end1);
     }
     private boolean hasTimeOverlapOnDate(ScheduleAssignment assignment, EmployeeSchedule existing, LocalDate date) {
         Shifts newShift = shiftsRepository.findById(assignment.getShiftId()).orElse(null);
         Shifts existingShift = existing.getShift();

         if (newShift == null || existingShift == null) {
             return false;
         }
         if (Objects.equals(newShift.getId(), existingShift.getId())) {
             return false;
         }
         int dayOfWeek = date.getDayOfWeek().getValue();
         List<ShiftDetail> newDetails = getShiftDetailsForDay(newShift, dayOfWeek);
         List<ShiftDetail> existingDetails = getShiftDetailsForDay(existingShift, dayOfWeek);
         if (newDetails.isEmpty() || existingDetails.isEmpty()) {
             return false;
         }
         boolean hasOverlap = false;
         for (ShiftDetail newDetail : newDetails) {
             for (ShiftDetail existingDetail : existingDetails) {
                 if (timesActuallyOverlap(newDetail, existingDetail)) {
                     hasOverlap = true;
                     break;
                 }
             }
             if (hasOverlap) break;
         }

         if (!hasOverlap) {
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

             boolean noOverlap = end1.isBefore(start2) || end1.equals(start2) ||
                     end2.isBefore(start1) || end2.equals(start1);

             return !noOverlap;
         } catch (java.time.format.DateTimeParseException e) {
             System.err.println("Error parsing time: " + e.getMessage());
             return false;
         }
     }






 }
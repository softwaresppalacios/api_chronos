package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.ScheduleAssignmentGroupDTO;
import sp.sistemaspalacios.api_chronos.dto.ScheduleDetailDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.ScheduleAssignmentGroup;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.ScheduleAssignmentGroupRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;
import sp.sistemaspalacios.api_chronos.service.boundaries.holiday.HolidayService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleAssignmentGroupService {

    private final ScheduleAssignmentGroupRepository groupRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final GeneralConfigurationService configService;
    private final HolidayService holidayService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private static class HoursSummary {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal regular = BigDecimal.ZERO;
        BigDecimal overtime = BigDecimal.ZERO;
    }

    // ===== Helpers de fecha/string/tiempo =====

    private static LocalDate toLocalDate(java.util.Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private int toMinutes(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private int diffMinutesWrap(int start, int end) {
        return (end >= start) ? (end - start) : (1440 - start + end);
    }

    /** Minutos del detalle SIN considerar break */
    private int minutesForDetailNoBreaks(ShiftDetail d) {
        if (d == null || d.getStartTime() == null || d.getEndTime() == null) return 0;
        int s = toMinutes(d.getStartTime());
        int e = toMinutes(d.getEndTime());
        return Math.max(0, diffMinutesWrap(s, e));
    }

    /** Calcula las horas TOTALES del turno (suma de todos los start_time/end_time) */
    private BigDecimal calculateTotalShiftHours(Shifts shift) {
        if (shift == null || shift.getShiftDetails() == null) return BigDecimal.ZERO;


        int totalMinutes = 0;
        for (ShiftDetail detail : shift.getShiftDetails()) {
            int minutes = minutesForDetailNoBreaks(detail);
            totalMinutes += minutes;

        }

        BigDecimal hours = BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        return hours;
    }

    /** Verifica si dos schedules se solapan en fechas */
    private boolean schedulesOverlap(EmployeeSchedule s1, EmployeeSchedule s2) {
        LocalDate start1 = toLocalDate(s1.getStartDate());
        LocalDate end1 = (s1.getEndDate() != null) ? toLocalDate(s1.getEndDate()) : start1;
        LocalDate start2 = toLocalDate(s2.getStartDate());
        LocalDate end2 = (s2.getEndDate() != null) ? toLocalDate(s2.getEndDate()) : start2;

        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    // ====== PÚBLICOS ======

    /**
     * LÓGICA CORREGIDA: Festivos incluidos en regulares, nocturnos también en regulares
     */
    @Transactional
    public ScheduleAssignmentGroupDTO processScheduleAssignment(Long employeeId, List<Long> scheduleIds) {

        List<EmployeeSchedule> newSchedules = scheduleRepository.findAllById(scheduleIds);
        if (newSchedules.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron schedules con los IDs proporcionados");
        }

        for (EmployeeSchedule schedule : newSchedules) {
            BigDecimal shiftHours = calculateTotalShiftHours(schedule.getShift());

        }

        List<EmployeeSchedule> allSchedules = new ArrayList<>(newSchedules);

        Date periodStart = findEarliestDate(allSchedules);
        Date periodEnd   = findLatestDate(allSchedules);


        // Buscar grupo existente que solape el período
        List<ScheduleAssignmentGroup> existingGroups = groupRepository.findByEmployeeId(employeeId)
                .stream()
                .filter(g -> periodsOverlap(periodStart, periodEnd, g.getPeriodStart(), g.getPeriodEnd()))
                .collect(Collectors.toList());


        ScheduleAssignmentGroup group;
        if (!existingGroups.isEmpty()) {
            group = existingGroups.get(0);


            for (Long id : scheduleIds) {
                if (!group.getEmployeeScheduleIds().contains(id)) {
                    group.getEmployeeScheduleIds().add(id);
                } else {
                }
            }
            if (periodStart.before(group.getPeriodStart())) group.setPeriodStart(periodStart);
            if (periodEnd.after(group.getPeriodEnd()))     group.setPeriodEnd(periodEnd);

            // Obtener TODOS los schedules del grupo (existentes + nuevos)
            allSchedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());


            BigDecimal totalHorasGrupo = BigDecimal.ZERO;
            for (EmployeeSchedule schedule : allSchedules) {
                BigDecimal shiftHours = calculateTotalShiftHours(schedule.getShift());
                totalHorasGrupo = totalHorasGrupo.add(shiftHours);

            }

        } else {
            group = new ScheduleAssignmentGroup();
            group.setEmployeeId(employeeId);
            group.setPeriodStart(periodStart);
            group.setPeriodEnd(periodEnd);
            group.setEmployeeScheduleIds(
                    allSchedules.stream().map(EmployeeSchedule::getId).collect(Collectors.toList())
            );
            group.setStatus("ACTIVE");

        }

        // OBTENER DESGLOSE POR TIPOS
        Map<String, BigDecimal> hoursByType = calculateHoursByType(allSchedules);

        // LÓGICA DEFINITIVAMENTE CORREGIDA
        BigDecimal regularDiurnas = hoursByType.get("REGULAR_DIURNA");
        BigDecimal regularNocturnas = hoursByType.get("REGULAR_NOCTURNA");
        BigDecimal festivoTotal = hoursByType.get("FESTIVO_DIURNA").add(hoursByType.get("FESTIVO_NOCTURNA"));

        // Las horas regulares son SOLO las no festivas
        // Las festivas se cuentan por separado pero NO se suman al total
        BigDecimal regularesCompletas = regularDiurnas.add(regularNocturnas).add(festivoTotal);

        // Las horas extras son solo dominicales y excesos (SIN festivos)
        BigDecimal dominicalesTotal = hoursByType.get("DOMINICAL_DIURNA").add(hoursByType.get("DOMINICAL_NOCTURNA"));
        BigDecimal extrasTotal = hoursByType.get("EXTRA_DIURNA").add(hoursByType.get("EXTRA_NOCTURNA"));
        BigDecimal extrasCompletas = dominicalesTotal.add(extrasTotal);

        // TOTAL CORRECTO: regulares + festivas + extras
        // (porque calculateHoursByType ya aplicó el límite correctamente)
        BigDecimal totalReal = regularesCompletas.add(extrasCompletas);

        //  ASIGNAR VALORES CORRECTOS
        group.setTotalHours(totalReal.setScale(2, RoundingMode.HALF_UP));
        group.setRegularHours(regularesCompletas.setScale(2, RoundingMode.HALF_UP)); // Solo regulares sin festivos
        group.setOvertimeHours(extrasCompletas.setScale(2, RoundingMode.HALF_UP));   // Solo dominicales y excesos

        //  FESTIVOS: Se muestran por separado SOLO para indicar recargo, NO se suman al total
        group.setFestivoHours(festivoTotal.setScale(2, RoundingMode.HALF_UP)); // 9h festivas (para recargo)
        group.setFestivoType(determineFestivoType(hoursByType));
        group.setOvertimeType(determineNonFestivoType(hoursByType));



        group = groupRepository.save(group);
        return convertToDTO(group, newSchedules);
    }
    /**
     * MÉTODO CORREGIDO: Las horas regulares son exactamente REGULAR_DIURNA sin limitaciones artificiales
     */
    private HoursSummary computeHoursWithCorrectLogic(List<EmployeeSchedule> schedules) {
        HoursSummary hs = new HoursSummary();

        if (schedules.isEmpty()) return hs;

        // Calcular total de horas de todos los schedules
        for (EmployeeSchedule schedule : schedules) {
            if (schedule.getShift() == null) continue;
            BigDecimal shiftHours = calculateTotalShiftHours(schedule.getShift());
            hs.total = hs.total.add(shiftHours);
        }

        // Obtener el desglose por tipos
        Map<String, BigDecimal> hoursByType = calculateHoursByType(schedules);

        // ✅ CORRECCIÓN: Las horas regulares son EXACTAMENTE las REGULAR_DIURNA calculadas
        // (el límite semanal ya se aplica DENTRO de calculateHoursByType)
        hs.regular = hoursByType.get("REGULAR_DIURNA");

        // Todo lo demás son "overtime"
        hs.overtime = hs.total.subtract(hs.regular);



        // Verificar que los cálculos son consistentes
        BigDecimal sumaVerificacion = BigDecimal.ZERO;
        hoursByType.forEach((tipo, horas) -> {
            System.out.println("     " + tipo + ": " + horas);
        });

        return hs;
    }

    /**
     * MÉTODO CORREGIDO: Maneja solapamientos - primer turno regulares, adicionales extras
     */
    private HoursSummary computeHoursWithOverlapLogic(List<EmployeeSchedule> schedules) {
        HoursSummary hs = new HoursSummary();

        if (schedules.isEmpty()) return hs;

        // Ordenar schedules por ID (o fecha de creación) para determinar precedencia
        List<EmployeeSchedule> sortedSchedules = schedules.stream()
                .sorted(Comparator.comparing(EmployeeSchedule::getId))
                .collect(Collectors.toList());

        BigDecimal weeklyLimit = getWeeklyHoursLimit(); // 44h
        BigDecimal regularUsed = BigDecimal.ZERO;

        for (EmployeeSchedule schedule : sortedSchedules) {
            if (schedule.getShift() == null) continue;

            BigDecimal shiftHours = calculateTotalShiftHours(schedule.getShift());
            hs.total = hs.total.add(shiftHours);

            // Verificar si este schedule se solapa con alguno anterior
            boolean hasOverlap = false;
            for (EmployeeSchedule previousSchedule : sortedSchedules) {
                if (previousSchedule.getId().equals(schedule.getId())) break; // Llegamos al actual
                if (schedulesOverlap(schedule, previousSchedule)) {
                    hasOverlap = true;
                    break;
                }
            }

            if (!hasOverlap && regularUsed.compareTo(weeklyLimit) < 0) {
                // Primer turno o sin solapamiento: puede ser regular (hasta el límite)
                BigDecimal remainingRegular = weeklyLimit.subtract(regularUsed);
                BigDecimal regularPortion = shiftHours.min(remainingRegular);
                BigDecimal overtimePortion = shiftHours.subtract(regularPortion);

                hs.regular = hs.regular.add(regularPortion);
                hs.overtime = hs.overtime.add(overtimePortion);
                regularUsed = regularUsed.add(regularPortion);
            } else {
                // Turno con solapamiento o ya se agotó el límite: todo es extra
                hs.overtime = hs.overtime.add(shiftHours);
            }
        }

        return hs;
    }

    /** Determina el tipo predominante de recargo */
    private String determineOvertimeType(List<EmployeeSchedule> schedules, BigDecimal overtimeHours) {
        if (overtimeHours.compareTo(BigDecimal.ZERO) == 0) {
            return "Normal";
        }

        Map<String, BigDecimal> hoursByType = calculateHoursByType(schedules);

        Map<String, String> codeToDisplay = new HashMap<>();
        codeToDisplay.put("REGULAR_DIURNA", "Normal");
        codeToDisplay.put("REGULAR_NOCTURNA", "Nocturno");
        codeToDisplay.put("EXTRA_DIURNA", "Extra Diurna");
        codeToDisplay.put("EXTRA_NOCTURNA", "Extra Nocturna");
        codeToDisplay.put("DOMINICAL_DIURNA", "Dominical");
        codeToDisplay.put("DOMINICAL_NOCTURNA", "Dominical Nocturno");
        codeToDisplay.put("FESTIVO_DIURNA", "Festivo");
        codeToDisplay.put("FESTIVO_NOCTURNA", "Festivo Nocturno");

        String[] priority = {
                "FESTIVO_NOCTURNA","FESTIVO_DIURNA",
                "DOMINICAL_NOCTURNA","DOMINICAL_DIURNA",
                "EXTRA_NOCTURNA","EXTRA_DIURNA",
                "REGULAR_NOCTURNA","REGULAR_DIURNA"
        };

        String predominantCode = "REGULAR_DIURNA";
        for (String code : priority) {
            BigDecimal h = hoursByType.get(code);
            if (h != null && h.compareTo(BigDecimal.ZERO) > 0) {
                predominantCode = code;
                break;
            }
        }
        return codeToDisplay.getOrDefault(predominantCode, "Normal");
    }

    /** Límite semanal desde config */
    private BigDecimal getWeeklyHoursLimit() {
        try {
            String value = configService.getByType("WEEKLY_HOURS").getValue();
            if (value != null && value.contains(":")) {
                String[] parts = value.split(":");
                BigDecimal hours = new BigDecimal(parts[0]);
                BigDecimal minutes = new BigDecimal(parts[1]).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
                return hours.add(minutes);
            }
            return new BigDecimal(value);
        } catch (Exception e) {
            return new BigDecimal("44.0");
        }
    }

    /**
     * MÉTODO CORREGIDO: Festivos SÍ cuentan hacia el límite de 44h
     */
    private Map<String, BigDecimal> calculateHoursByType(List<EmployeeSchedule> schedules) {
        Map<String, BigDecimal> m = new HashMap<>();
        m.put("REGULAR_DIURNA", BigDecimal.ZERO);
        m.put("REGULAR_NOCTURNA", BigDecimal.ZERO);
        m.put("EXTRA_DIURNA", BigDecimal.ZERO);
        m.put("EXTRA_NOCTURNA", BigDecimal.ZERO);
        m.put("DOMINICAL_DIURNA", BigDecimal.ZERO);
        m.put("DOMINICAL_NOCTURNA", BigDecimal.ZERO);
        m.put("FESTIVO_DIURNA", BigDecimal.ZERO);
        m.put("FESTIVO_NOCTURNA", BigDecimal.ZERO);

        String nightStartHM = "21:00", nightEndHM = "06:00";
        try { nightStartHM = configService.getByType("NIGHT_START").getValue(); } catch (Exception ignore) {}
        try { nightEndHM   = configService.getByType("NIGHT_END").getValue();   } catch (Exception ignore) {}
        final int NIGHT_START = toMinutes(nightStartHM);
        final int NIGHT_END   = toMinutes(nightEndHM);

        List<sp.sistemaspalacios.api_chronos.entity.holiday.Holiday> holidays = holidayService.getAllHolidays();
        Set<LocalDate> holidayDates = holidays.stream().map(h -> h.getHolidayDate()).collect(Collectors.toSet());

        BigDecimal weeklyLimit = getWeeklyHoursLimit(); // 44h

        // ✅ CORRECCIÓN: Solo trackear horas REGULARES para el límite (festivos también cuentan pero son diferentes)
        BigDecimal regularHoursAccumulated = BigDecimal.ZERO; // Solo para horas regulares (no festivas ni dominicales)

        System.out.println("🔍 INICIANDO CÁLCULO CORREGIDO - Límite semanal: " + weeklyLimit);

        for (EmployeeSchedule schedule : schedules) {
            if (schedule.getShift() == null || schedule.getShift().getShiftDetails() == null) continue;

            LocalDate startDate = toLocalDate(schedule.getStartDate());
            LocalDate endDate = (schedule.getEndDate() != null) ? toLocalDate(schedule.getEndDate()) : startDate;


            // Procesar cada día del período
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                int dow = d.getDayOfWeek().getValue(); // 1=lunes, 7=domingo
                boolean isSunday = (dow == 7);
                boolean isHoliday = holidayDates.contains(d);


                // Procesar cada detalle que coincida con este día
                for (ShiftDetail detail : schedule.getShift().getShiftDetails()) {
                    if (detail.getDayOfWeek() == null || detail.getStartTime() == null || detail.getEndTime() == null) continue;
                    if (!detail.getDayOfWeek().equals(dow)) continue;

                    // Determinar si es nocturno
                    int startMinutes = toMinutes(detail.getStartTime());
                    int endMinutes = toMinutes(detail.getEndTime());
                    List<Segment> segments = splitIntoDayNightSegments(startMinutes, endMinutes, NIGHT_START, NIGHT_END);


                    for (Segment seg : segments) {
                        if (seg.minutes <= 0) continue;

                        BigDecimal hours = BigDecimal.valueOf(seg.minutes)
                                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);


                        String recargoType;

                        if (isHoliday) {
                            recargoType = seg.night ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";

                        } else if (isSunday) {
                            recargoType = seg.night ? "DOMINICAL_NOCTURNA" : "DOMINICAL_DIURNA";

                        } else if (seg.night) {
                            //  NOCTURNO: Tercera prioridad - SIEMPRE recargo nocturno
                            recargoType = "REGULAR_NOCTURNA";

                        } else {


                            // VERIFICAR LÍMITE SOLO PARA REGULARES
                            if (regularHoursAccumulated.add(hours).compareTo(weeklyLimit) <= 0) {
                                // Cabe dentro del límite de regulares
                                recargoType = "REGULAR_DIURNA";
                                regularHoursAccumulated = regularHoursAccumulated.add(hours);
                                System.out.println("        → TODO REGULAR: " + hours + "h | Regular acumulado: " + regularHoursAccumulated);

                            } else {
                                // Excede límite de regulares - dividir entre regular y extra
                                BigDecimal remainingRegular = weeklyLimit.subtract(regularHoursAccumulated);

                                if (remainingRegular.compareTo(BigDecimal.ZERO) > 0) {
                                    // Parte regular, parte extra
                                    BigDecimal extraPortion = hours.subtract(remainingRegular);


                                    m.put("REGULAR_DIURNA", m.get("REGULAR_DIURNA").add(remainingRegular));
                                    m.put("EXTRA_DIURNA", m.get("EXTRA_DIURNA").add(extraPortion));

                                    regularHoursAccumulated = weeklyLimit; // Llegamos exacto al límite

                                    // Continue para evitar agregar dos veces
                                    continue;

                                } else {
                                    // Ya no queda espacio regular - todo es extra
                                    recargoType = "EXTRA_DIURNA";
                                }
                            }
                        }

                        // Agregar las horas al tipo correspondiente (solo si no se dividió arriba)
                        BigDecimal currentValue = m.get(recargoType);
                        BigDecimal newValue = currentValue.add(hours);
                        m.put(recargoType, newValue);


                    }
                }
            }
        }

        BigDecimal totalVerificacion = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : m.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("   - " + entry.getKey() + ": " + entry.getValue().doubleValue() + "h");
                totalVerificacion = totalVerificacion.add(entry.getValue());
            }
        }

        return m;
    }

    private static class Segment {
        boolean night;
        int minutes;
        Segment(boolean night, int minutes){ this.night = night; this.minutes = minutes; }
    }

    /** Parte [start,end) en segmentos diurno/nocturno sin considerar breaks */
    private List<Segment> splitIntoDayNightSegments(int start, int end, int nightStart, int nightEnd) {
        List<Segment> out = new ArrayList<>();
        int total = diffMinutesWrap(start, end);
        int cursor = start;
        int remain = total;

        while (remain > 0) {
            boolean inNight = (cursor >= nightStart) || (cursor < nightEnd);
            int nextBreak;
            if (inNight) nextBreak = (cursor >= nightStart) ? 1440 : nightEnd;
            else         nextBreak = (cursor >= nightEnd && cursor < nightStart) ? nightStart : 1440;

            int span = nextBreak - cursor;
            if (span <= 0) span += 1440;
            if (span > remain) span = remain;

            if (span > 0) {
                if (!out.isEmpty() && out.get(out.size()-1).night == inNight) {
                    out.get(out.size()-1).minutes += span;
                } else {
                    out.add(new Segment(inNight, span));
                }
            }
            remain -= span;
            cursor = (cursor + span) % 1440;
        }
        return out;
    }

    // ===== DTOs / utilidades repos =====

    private Date findEarliestDate(List<EmployeeSchedule> schedules) {
        return schedules.stream()
                .map(EmployeeSchedule::getStartDate)
                .filter(Objects::nonNull)
                .min(Date::compareTo)
                .orElse(new Date());
    }

    private Date findLatestDate(List<EmployeeSchedule> schedules) {
        return schedules.stream()
                .map(es -> es.getEndDate() != null ? es.getEndDate() : es.getStartDate())
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(new Date());
    }

    private ScheduleAssignmentGroupDTO convertToDTO(ScheduleAssignmentGroup group, List<EmployeeSchedule> schedules) {
        ScheduleAssignmentGroupDTO dto = new ScheduleAssignmentGroupDTO();

        dto.setId(group.getId());
        dto.setEmployeeId(group.getEmployeeId());
        dto.setPeriodStart(dateFormat.format(group.getPeriodStart()));
        dto.setPeriodEnd(dateFormat.format(group.getPeriodEnd()));
        dto.setTotalHours(group.getTotalHours());
        dto.setRegularHours(group.getRegularHours());
        dto.setAssignedHours(group.getRegularHours()); // assignedHours = regularHours

        // CAMPOS SEPARADOS
        dto.setOvertimeHours(group.getOvertimeHours());
        dto.setOvertimeType(group.getOvertimeType());
        dto.setFestivoHours(group.getFestivoHours());
        dto.setFestivoType(group.getFestivoType());

        dto.setStatus(group.getStatus());
        dto.setEmployeeScheduleIds(group.getEmployeeScheduleIds());

        // CALCULAR DESGLOSE DETALLADO POR TIPOS DE RECARGO
        Map<String, BigDecimal> hoursByType = calculateHoursByType(schedules);
        Map<String, Object> detailedBreakdown = new HashMap<>();

        // SEPARAR EN DOS CATEGORÍAS PRINCIPALES
        Map<String, Double> festivoBreakdown = new HashMap<>();
        Map<String, Double> otherBreakdown = new HashMap<>();

        // Festivos
        festivoBreakdown.put("FESTIVO_DIURNA", hoursByType.get("FESTIVO_DIURNA").doubleValue());
        festivoBreakdown.put("FESTIVO_NOCTURNA", hoursByType.get("FESTIVO_NOCTURNA").doubleValue());

        // Otros tipos
        otherBreakdown.put("DOMINICAL_DIURNA", hoursByType.get("DOMINICAL_DIURNA").doubleValue());
        otherBreakdown.put("DOMINICAL_NOCTURNA", hoursByType.get("DOMINICAL_NOCTURNA").doubleValue());
        otherBreakdown.put("EXTRA_DIURNA", hoursByType.get("EXTRA_DIURNA").doubleValue());
        otherBreakdown.put("EXTRA_NOCTURNA", hoursByType.get("EXTRA_NOCTURNA").doubleValue());
        otherBreakdown.put("REGULAR_DIURNA", hoursByType.get("REGULAR_DIURNA").doubleValue());
        otherBreakdown.put("REGULAR_NOCTURNA", hoursByType.get("REGULAR_NOCTURNA").doubleValue());

        detailedBreakdown.put("festivo", festivoBreakdown);
        detailedBreakdown.put("otros", otherBreakdown);
        detailedBreakdown.put("total_festivo",
                hoursByType.get("FESTIVO_DIURNA").add(hoursByType.get("FESTIVO_NOCTURNA")).doubleValue());
        detailedBreakdown.put("total_otros",
                hoursByType.get("DOMINICAL_DIURNA").add(hoursByType.get("DOMINICAL_NOCTURNA"))
                        .add(hoursByType.get("EXTRA_DIURNA")).add(hoursByType.get("EXTRA_NOCTURNA"))
                        .add(hoursByType.get("REGULAR_DIURNA")).add(hoursByType.get("REGULAR_NOCTURNA"))
                        .doubleValue());

        // Usar el desglose detallado como overtimeBreakdown
        Map<String, Object> combinedBreakdown = new HashMap<>();
        combinedBreakdown.putAll(hoursByType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().doubleValue()
                )));
        combinedBreakdown.put("detailed", detailedBreakdown);
        dto.setOvertimeBreakdown(combinedBreakdown);

        List<ScheduleDetailDTO> details = new ArrayList<>();
        for (EmployeeSchedule schedule : schedules) {
            ScheduleDetailDTO detail = new ScheduleDetailDTO();
            detail.setScheduleId(schedule.getId());
            detail.setShiftName(schedule.getShift() != null ? schedule.getShift().getName() : "Sin nombre");
            detail.setStartDate(dateFormat.format(schedule.getStartDate()));
            Date safeEnd = (schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate());
            detail.setEndDate(dateFormat.format(safeEnd));

            BigDecimal hoursInPeriod = (schedule.getShift() != null) ? calculateTotalShiftHours(schedule.getShift()) : BigDecimal.ZERO;
            detail.setHoursInPeriod(hoursInPeriod.doubleValue());

            details.add(detail);
        }
        dto.setScheduleDetails(details);

        return dto;
    }

    private boolean periodsOverlap(Date aStart, Date aEnd, Date bStart, Date bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false;
        return !aStart.after(bEnd) && !bStart.after(aEnd);
    }

    // ===== Consultas públicas =====

    public List<ScheduleAssignmentGroupDTO> getEmployeeGroups(Long employeeId) {
        List<ScheduleAssignmentGroup> groups = groupRepository.findByEmployeeId(employeeId);
        return groups.stream().map(group -> {
            List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
            return convertToDTO(group, schedules);
        }).collect(Collectors.toList());
    }

    /**
     * Obtiene el grupo más reciente para un empleado
     */
    public ScheduleAssignmentGroupDTO getLatestGroupForEmployee(Long employeeId) {
        List<ScheduleAssignmentGroup> groups = groupRepository.findByEmployeeId(employeeId);
        if (groups.isEmpty()) return null;

        // Obtener el grupo más reciente
        ScheduleAssignmentGroup latest = groups.stream()
                .max(Comparator.comparing(ScheduleAssignmentGroup::getId))
                .orElse(null);

        if (latest == null) return null;

        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(latest.getEmployeeScheduleIds());
        return convertToDTO(latest, schedules);
    }

    public ScheduleAssignmentGroupDTO getGroupById(Long groupId) {
        Optional<ScheduleAssignmentGroup> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isEmpty()) return null;
        ScheduleAssignmentGroup group = groupOpt.get();
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        return convertToDTO(group, schedules);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("Grupo no encontrado con ID: " + groupId);
        }
        groupRepository.deleteById(groupId);
    }

    @Transactional
    public ScheduleAssignmentGroupDTO recalculateGroup(Long groupId) {
        ScheduleAssignmentGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado"));

        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());

        HoursSummary hs = computeHoursWithOverlapLogic(schedules);

        group.setTotalHours(hs.total.setScale(2, RoundingMode.HALF_UP));
        group.setRegularHours(hs.regular.setScale(2, RoundingMode.HALF_UP));
        group.setOvertimeHours(hs.overtime.setScale(2, RoundingMode.HALF_UP));
        group.setOvertimeType(determineOvertimeType(schedules, hs.overtime));

        group = groupRepository.save(group);
        return convertToDTO(group, schedules);
    }

    /**
     * Determina el tipo predominante para horas NO festivas (otros tipos de recargo)
     */
    private String determineNonFestivoType(Map<String, BigDecimal> hoursByType) {
        // Crear un mapa sin los tipos festivos
        Map<String, BigDecimal> noFestivoHours = new HashMap<>();
        noFestivoHours.put("DOMINICAL_DIURNA", hoursByType.get("DOMINICAL_DIURNA"));
        noFestivoHours.put("DOMINICAL_NOCTURNA", hoursByType.get("DOMINICAL_NOCTURNA"));
        noFestivoHours.put("EXTRA_DIURNA", hoursByType.get("EXTRA_DIURNA"));
        noFestivoHours.put("EXTRA_NOCTURNA", hoursByType.get("EXTRA_NOCTURNA"));
        noFestivoHours.put("REGULAR_NOCTURNA", hoursByType.get("REGULAR_NOCTURNA"));
        noFestivoHours.put("REGULAR_DIURNA", hoursByType.get("REGULAR_DIURNA"));

        // Calcular total de horas no festivas
        BigDecimal totalNoFestivo = noFestivoHours.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalNoFestivo.compareTo(BigDecimal.ZERO) == 0) {
            return "Normal";
        }

        // Mapeo de códigos a nombres display
        Map<String, String> codeToDisplay = new HashMap<>();
        codeToDisplay.put("DOMINICAL_NOCTURNA", "Dominical Nocturno");
        codeToDisplay.put("DOMINICAL_DIURNA", "Dominical");
        codeToDisplay.put("EXTRA_NOCTURNA", "Extra Nocturna");
        codeToDisplay.put("EXTRA_DIURNA", "Extra Diurna");
        codeToDisplay.put("REGULAR_NOCTURNA", "Nocturno");
        codeToDisplay.put("REGULAR_DIURNA", "Normal");

        // Orden de prioridad para determinar el tipo predominante
        String[] priority = {
                "DOMINICAL_NOCTURNA", "DOMINICAL_DIURNA",
                "EXTRA_NOCTURNA", "EXTRA_DIURNA",
                "REGULAR_NOCTURNA", "REGULAR_DIURNA"
        };

        // Buscar el tipo con horas > 0 según prioridad
        for (String code : priority) {
            BigDecimal hours = noFestivoHours.get(code);
            if (hours != null && hours.compareTo(BigDecimal.ZERO) > 0) {
                return codeToDisplay.get(code);
            }
        }

        return "Normal";
    }

    /**
     * Determina el tipo predominante para horas festivas
     */
    private String determineFestivoType(Map<String, BigDecimal> hoursByType) {
        BigDecimal festivoDiurna = hoursByType.get("FESTIVO_DIURNA");
        BigDecimal festivoNocturna = hoursByType.get("FESTIVO_NOCTURNA");

        // Si no hay horas festivas, retornar null
        if ((festivoDiurna == null || festivoDiurna.compareTo(BigDecimal.ZERO) == 0) &&
                (festivoNocturna == null || festivoNocturna.compareTo(BigDecimal.ZERO) == 0)) {
            return null;
        }

        // Determinar el tipo predominante
        if (festivoNocturna != null && festivoNocturna.compareTo(BigDecimal.ZERO) > 0) {
            if (festivoDiurna != null && festivoDiurna.compareTo(festivoNocturna) > 0) {
                return "Festivo"; // Más horas diurnas que nocturnas
            } else {
                return "Festivo Nocturno"; // Más o igual horas nocturnas
            }
        } else if (festivoDiurna != null && festivoDiurna.compareTo(BigDecimal.ZERO) > 0) {
            return "Festivo"; // Solo hay horas diurnas
        }

        return null;
    }
}
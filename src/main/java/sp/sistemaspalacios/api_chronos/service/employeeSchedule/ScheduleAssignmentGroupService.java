package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.dto.OvertimeTypeDTO;
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
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleAssignmentGroupService {

    private final ScheduleAssignmentGroupRepository groupRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final GeneralConfigurationService configService;
    private final HolidayService holidayService;
    private final OvertimeTypeService overtimeTypeService;

    @Value("${chronos.group.default-status:ACTIVE}")
    private String defaultGroupStatus;

    @Value("${chronos.shift.default-name:Sin nombre}")
    private String defaultShiftName;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    // ======== Configuración nocturna ========
    private int getNightStartMinutes() {
        try {
            String raw = configService.getByType("NIGHT_START").getValue(); // "19:00", "7 pm", etc.
            int minutes = parseNightStartMinutes(raw);
            if (minutes < 15 * 60 || minutes > 22 * 60) {
                log.warn("⚠️ NIGHT_START={} -> {}min fuera de rango. Usando 19:00", raw, minutes);
                return 19 * 60;
            }
            log.info("🌙 NIGHT_START='{}' -> {} ({}:{})", raw, minutes, minutes / 60, String.format("%02d", minutes % 60));
            return minutes;
        } catch (Exception e) {
            log.warn("⚠️ NIGHT_START inválido. Usando 19:00", e);
            return 19 * 60;
        }
    }

    private int parseNightStartMinutes(String raw) {
        if (raw == null) return 19 * 60;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        boolean pm = s.contains("pm");
        boolean am = s.contains("am");
        s = s.replace("pm", "").replace("am", "").trim();

        int hour = 19, minute = 0;
        if (s.contains(":")) {
            String[] p = s.split(":");
            hour = Integer.parseInt(p[0]);
            minute = Integer.parseInt(p[1]);
        } else if (!s.isEmpty()) {
            hour = Integer.parseInt(s);
        }

        if (am || pm) {
            if (hour == 12) hour = 0;
            if (pm) hour += 12;
        } else {
            if (hour >= 0 && hour <= 12) hour += 12; // heurística: "7" => 19
        }
        hour = ((hour % 24) + 24) % 24;
        minute = Math.max(0, Math.min(59, minute));
        return hour * 60 + minute;
    }

    private int getNightEndMinutes() {
        return 6 * 60;
    }

    // ===== Métodos públicos principales =====
    @Transactional
    public ScheduleAssignmentGroupDTO processScheduleAssignment(Long employeeId, List<Long> scheduleIds) {
        validateInputs(employeeId, scheduleIds);

        List<EmployeeSchedule> newSchedules = getValidatedSchedules(scheduleIds);
        ScheduleAssignmentGroup group = findOrCreateGroup(employeeId, newSchedules, scheduleIds);

        List<EmployeeSchedule> allSchedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        HoursCalculation hoursCalc = calculateHours(allSchedules);

        updateGroupTotals(group, hoursCalc);
        group = groupRepository.save(group);

        return convertToDTO(group, newSchedules, hoursCalc);
    }

    public List<ScheduleAssignmentGroupDTO> getEmployeeGroups(Long employeeId) {
        return groupRepository.findByEmployeeId(employeeId).stream()
                .map(this::convertGroupToDTO)
                .collect(Collectors.toList());
    }

    public ScheduleAssignmentGroupDTO getLatestGroupForEmployee(Long employeeId) {
        return groupRepository.findByEmployeeId(employeeId).stream()
                .max(Comparator.comparing(ScheduleAssignmentGroup::getId))
                .map(this::convertGroupToDTO)
                .orElse(null);
    }

    public ScheduleAssignmentGroupDTO getGroupById(Long groupId) {
        return groupRepository.findById(groupId).map(this::convertGroupToDTO).orElse(null);
    }

    @Transactional
    public ScheduleAssignmentGroupDTO recalculateGroup(Long groupId) {
        ScheduleAssignmentGroup group = getGroupOrThrow(groupId);
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        HoursCalculation hoursCalc = calculateHours(schedules);
        updateGroupTotals(group, hoursCalc);
        group = groupRepository.save(group);
        return convertToDTO(group, schedules, hoursCalc);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        if (!groupRepository.existsById(groupId))
            throw new IllegalArgumentException("Grupo no encontrado con ID: " + groupId);
        groupRepository.deleteById(groupId);
    }

    // ===== Cálculo de horas (ahora POR SEMANA NATURAL) =====
    private HoursCalculation calculateHours(List<EmployeeSchedule> schedules) {
        BigDecimal weeklyLimit = getWeeklyHoursLimit();
        Set<LocalDate> holidayDates = getHolidayDates();

        Map<String, OvertimeTypeDTO> availableTypes = overtimeTypeService.getAllActiveTypes()
                .stream().collect(Collectors.toMap(OvertimeTypeDTO::getCode, t -> t));

        // Tipos EXTRA esperados (avisos si faltan)
        final List<String> REQUIRED_EXTRA_TYPES = Arrays.asList(
                "EXTRA_DOMINICAL_NOCTURNA_RECARGO_NOCTURNO",
                "EXTRA_DOMINICAL_NOCTURNA",
                "EXTRA_DOMINICAL_DIURNA",
                "EXTRA_FESTIVO_NOCTURNA",
                "EXTRA_FESTIVO_DIURNA",
                "EXTRA_NOCTURNA",
                "EXTRA_DIURNA"
        );
        REQUIRED_EXTRA_TYPES.stream()
                .filter(code -> !availableTypes.containsKey(code))
                .forEach(code -> log.warn("⚠️ Tipo EXTRA faltante en BD: {}", code));

        // 1) Obtener todos los segmentos diurno/nocturno por día
        List<HourDetail> segments = processAllSchedulesChronologically(schedules, holidayDates);

        // 2) Asignar base/extra con límite SEMANAL (reinicia lunes–domingo)
        Map<String, BigDecimal> finalHoursByType = assignHourTypesWithWeeklyLimit(segments, weeklyLimit, availableTypes);

        BigDecimal totalHours = finalHoursByType.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HoursCalculation(finalHoursByType, totalHours);
    }

    private List<HourDetail> processAllSchedulesChronologically(List<EmployeeSchedule> schedules, Set<LocalDate> holidayDates) {
        List<HourDetail> all = new ArrayList<>();
        final int nightStart = getNightStartMinutes();
        final int nightEnd = getNightEndMinutes();

        for (EmployeeSchedule schedule : schedules) {
            if (schedule.getShift() == null || schedule.getShift().getShiftDetails() == null) continue;

            System.out.println("🔍 PROCESANDO Schedule ID: " + schedule.getId());

            // CAMBIO CRÍTICO: Siempre usar rango de fechas como fallback
            List<LocalDate> datesToProcess;

            if (schedule.getDays() != null && !schedule.getDays().isEmpty()) {
                // Usar días generados (respeta exenciones)
                datesToProcess = schedule.getDays().stream()
                        .filter(Objects::nonNull)
                        .map(d -> d.getDate())
                        .filter(Objects::nonNull)
                        .map(jd -> {
                            if (jd instanceof java.sql.Date) {
                                return ((java.sql.Date) jd).toLocalDate();
                            }
                            return jd.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        })
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                System.out.println("   📋 Usando días generados: " + datesToProcess.size() + " días");
            } else {
                // FALLBACK MEJORADO: Usar rango completo pero verificar festivos
                DatePeriod period = DatePeriod.fromSchedule(schedule);
                LocalDate start = period.getStartLocalDate();
                LocalDate end = period.getEndLocalDate();

                List<LocalDate> tmp = new ArrayList<>();
                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                    // INCLUIR festivos por defecto si no hay días generados
                    // (esto evita que se pierdan festivos en recálculos)
                    tmp.add(date);
                }
                datesToProcess = tmp;

                System.out.println("   🔄 Usando rango completo (fallback): " + datesToProcess.size() + " días");
                System.out.println("   ⚠️  ADVERTENCIA: Schedule sin días generados - podrían perderse exenciones");
            }

            // Procesar cada fecha válida
            for (LocalDate date : datesToProcess) {
                int dow = date.getDayOfWeek().getValue();

                for (ShiftDetail d : schedule.getShift().getShiftDetails()) {
                    if (!Objects.equals(d.getDayOfWeek(), dow) || d.getStartTime() == null || d.getEndTime() == null)
                        continue;

                    TimeUtils.DayNightSplit split = TimeUtils.splitDayNight(d.getStartTime(), d.getEndTime(), nightStart, nightEnd);

                    if (split.dayMinutes > 0) {
                        HourDetail hd = new HourDetail();
                        hd.date = date;
                        hd.dayOfWeek = dow;
                        hd.segmentMinutes = split.dayMinutes;
                        hd.isNightSegment = false;
                        hd.isSunday = (dow == 7);
                        hd.isHoliday = holidayDates.contains(date);
                        all.add(hd);
                    }
                    if (split.nightMinutes > 0) {
                        HourDetail hn = new HourDetail();
                        hn.date = date;
                        hn.dayOfWeek = dow;
                        hn.segmentMinutes = split.nightMinutes;
                        hn.isNightSegment = true;
                        hn.isSunday = (dow == 7);
                        hn.isHoliday = holidayDates.contains(date);
                        all.add(hn);
                    }
                }
            }
        }

        all.sort(Comparator.comparing((HourDetail h) -> h.date));

        System.out.println("📈 TOTAL SEGMENTOS PROCESADOS: " + all.size());
        Map<Boolean, Long> byHoliday = all.stream().collect(Collectors.groupingBy(h -> h.isHoliday, Collectors.counting()));
        System.out.println("   🎉 Segmentos festivos: " + byHoliday.getOrDefault(true, 0L));
        System.out.println("   📅 Segmentos normales: " + byHoliday.getOrDefault(false, 0L));

        return all;
    }

    /**
     * Limita por semana natural (ISO: lunes–domingo). Reinicia el acumulado en cada semana.
     */
    private Map<String, BigDecimal> assignHourTypesWithWeeklyLimit(List<HourDetail> segments,
                                                                   BigDecimal weeklyLimit,
                                                                   Map<String, OvertimeTypeDTO> availableTypes) {
        Map<String, BigDecimal> hoursByType = new HashMap<>();
        Map<String, BigDecimal> accumulatedByWeek = new HashMap<>();

        WeekFields wf = WeekFields.ISO; // lunes–domingo

        log.info("🚀 INICIANDO ASIGNACIÓN CORREGIDA (festivos reemplazan) - Límite semanal: {}h", weeklyLimit);
        log.info("📊 Total de segmentos a procesar: {}", segments.size());

        for (HourDetail seg : segments) {
            String weekKey = weekKey(seg.date, wf);
            BigDecimal acc = accumulatedByWeek.getOrDefault(weekKey, BigDecimal.ZERO);

            BigDecimal hours = BigDecimal.valueOf(seg.segmentMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            String baseCode = determineBaseOvertimeCode(seg);

            log.debug("📅 {} [{}] - {}min = {}h | Acum: {}h | Festivo: {} | Domingo: {}",
                    seg.date, weekKey, seg.segmentMinutes, hours, acc, seg.isHoliday, seg.isSunday);

            if (seg.isHoliday) {
                // FESTIVO: Va como FESTIVO_* y NO incrementa el acumulado semanal
                String festivoCode = convertToFestivoType(baseCode, seg, availableTypes);
                addHoursToMap(hoursByType, festivoCode, hours);
                log.info("🎉 {} [{}] FESTIVO: +{}h como {} (reemplaza regulares, NO suma a acumulado)",
                        seg.date, weekKey, hours, festivoCode);
                // NO incrementar accumulatedByWeek - las horas festivas no cuentan para límite
            }
            else if (seg.isSunday) {
                // DOMINICAL: Va como EXTRA_DOMINICAL_* y NO incrementa el acumulado semanal
                String dominicalCode = convertToExtraType(baseCode, seg, availableTypes);
                addHoursToMap(hoursByType, dominicalCode, hours);
                log.info("🏛️ {} [{}] DOMINICAL: +{}h como {} (NO suma a acumulado)",
                        seg.date, weekKey, hours, dominicalCode);
                // NO incrementar accumulatedByWeek - los dominicales no cuentan para límite
            }
            else {
                // === DÍAS NORMALES: APLICAR LÍMITE SEMANAL ===
                boolean exceeds = acc.compareTo(weeklyLimit) >= 0;
                BigDecimal remaining = weeklyLimit.subtract(acc);

                if (exceeds) {
                    // Ya se superó el límite, todo es extra
                    String extraCode = convertToExtraType(baseCode, seg, availableTypes);
                    addHoursToMap(hoursByType, extraCode, hours);
                    acc = acc.add(hours);
                    log.info("🔴 {} [{}] EXCESO TOTAL: +{}h como {} (acum: {}h)",
                            seg.date, weekKey, hours, extraCode, acc);
                } else if (remaining.compareTo(hours) < 0) {
                    // Parte regular + parte extra
                    BigDecimal regularPart = remaining.max(BigDecimal.ZERO);
                    BigDecimal extraPart = hours.subtract(regularPart);

                    if (regularPart.compareTo(BigDecimal.ZERO) > 0) {
                        addHoursToMap(hoursByType, baseCode, regularPart);
                        log.info("🟢 {} [{}] REGULAR: +{}h como {}",
                                seg.date, weekKey, regularPart, baseCode);
                    }

                    String extraCode = convertToExtraType(baseCode, seg, availableTypes);
                    addHoursToMap(hoursByType, extraCode, extraPart);
                    log.info("🟡 {} [{}] EXCESO PARCIAL: +{}h como {} (acum: {}h)",
                            seg.date, weekKey, extraPart, extraCode, acc.add(hours));

                    acc = acc.add(hours);
                } else {
                    // Todo cabe como regular
                    addHoursToMap(hoursByType, baseCode, hours);
                    acc = acc.add(hours);
                    log.debug("🟢 {} [{}] REGULAR TOTAL: +{}h como {} (acum: {}h)",
                            seg.date, weekKey, hours, baseCode, acc);
                }

                // SOLO incrementar acumulado para días normales
                accumulatedByWeek.put(weekKey, acc);
            }
        }

        // Log de resumen final
        log.info("📈 RESUMEN FINAL POR TIPO:");
        hoursByType.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> log.info("   {} = {}h", e.getKey(), e.getValue()));

        if (!accumulatedByWeek.isEmpty()) {
            log.info("📆 Acumulados por semana (límite {}h, SOLO días normales): {}", weeklyLimit, accumulatedByWeek);
        }

        return hoursByType;
    }


    private String convertToFestivoType(String baseCode, HourDetail d, Map<String, OvertimeTypeDTO> availableTypes) {
        String festivoCode = d.isNightSegment ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";

        if (availableTypes.containsKey(festivoCode)) {
            return festivoCode;
        }

        log.warn("⚠️ Tipo FESTIVO no encontrado en BD: {}. Usando fallback.", festivoCode);
        return d.isNightSegment ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";
    }


    private String weekKey(LocalDate date, WeekFields wf) {
        int y = date.get(wf.weekBasedYear());
        int w = date.get(wf.weekOfWeekBasedYear());
        return y + "-W" + String.format("%02d", w);
    }

    private String determineBaseOvertimeCode(HourDetail d) {
        if (d.isHoliday) {
            return d.isNightSegment ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";
        }
        if (d.isSunday) {
            // Para domingo nocturno con recargo nocturno especial, usamos el código fuerte
            return d.isNightSegment ? "DOMINICAL_NOCTURNA_RECARGO_NOCTURNO" : "DOMINICAL_DIURNA";
        }
        return d.isNightSegment ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA";
    }

    private String convertToExtraType(String baseCode, HourDetail d, Map<String, OvertimeTypeDTO> availableTypes) {
        String extraCode;

        if (d.isHoliday) {
            extraCode = d.isNightSegment ? "EXTRA_FESTIVO_NOCTURNA" : "EXTRA_FESTIVO_DIURNA";
        } else if (d.isSunday) {
            extraCode = d.isNightSegment ? "EXTRA_DOMINICAL_NOCTURNA_RECARGO_NOCTURNO" : "EXTRA_DOMINICAL_DIURNA";
        } else {
            extraCode = d.isNightSegment ? "EXTRA_NOCTURNA" : "EXTRA_DIURNA";
        }

        if (availableTypes.containsKey(extraCode)) return extraCode;

        log.warn("⚠️ Tipo EXTRA no encontrado en BD: {}. Usando fallback.", extraCode);
        return d.isNightSegment ? "EXTRA_NOCTURNA" : "EXTRA_DIURNA";
    }

    private void addHoursToMap(Map<String, BigDecimal> map, String code, BigDecimal hours) {
        if (code == null || hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) return;
        map.put(code, map.getOrDefault(code, BigDecimal.ZERO).add(hours));
    }

    private void updateGroupTotals(ScheduleAssignmentGroup group, HoursCalculation calc) {
        // Debug: mostrar todas las horas calculadas
        log.info("🔍 HORAS CALCULADAS POR TIPO:");
        calc.getHoursByType().forEach((code, hours) -> {
            if (hours.compareTo(BigDecimal.ZERO) > 0) {
                log.info("   {} = {}h", code, hours);
            }
        });

        HoursSummary summary = HoursSummary.fromCalculation(calc, overtimeTypeService.getAllActiveTypes());

        // 🔒 Persistir EXACTAMENTE por columna:
        // regular_hours  = SOLO regulares dentro del límite
        // overtime_hours = SOLO extras
        // festivo_hours  = SOLO festivos
        // total_hours    = regular + festivo + extra

        BigDecimal oldRegular = group.getRegularHours();
        BigDecimal oldOvertime = group.getOvertimeHours();
        BigDecimal oldFestivo = group.getFestivoHours();
        BigDecimal oldTotal = group.getTotalHours();

        group.setRegularHours(summary.getRegularWithinLimit());
        group.setOvertimeHours(summary.getOvertimeHours());
        group.setFestivoHours(summary.getFestivoHours());
        group.setTotalHours(summary.getTotalHours());

        group.setFestivoType(summary.getFestivoType());
        group.setOvertimeType(summary.getOvertimeType());

        // Log cambios
        log.info("📝 CAMBIOS EN EL GRUPO:");
        log.info("   Regular: {}h -> {}h", oldRegular, group.getRegularHours());
        log.info("   Overtime: {}h -> {}h", oldOvertime, group.getOvertimeHours());
        log.info("   Festivo: {}h -> {}h", oldFestivo, group.getFestivoHours());
        log.info("   Total: {}h -> {}h", oldTotal, group.getTotalHours());

        log.info("✅ GRUPO ACTUALIZADO: Total={}h, Regulares={}h, Overtime={}h, Festivos={}h",
                group.getTotalHours(), group.getRegularHours(),
                group.getOvertimeHours(), group.getFestivoHours());
        log.info("🏷️ Tipos: Overtime='{}', Festivo='{}'",
                group.getOvertimeType(), group.getFestivoType());
    }


    // ===== Conversión a DTO =====
    private ScheduleAssignmentGroupDTO convertGroupToDTO(ScheduleAssignmentGroup group) {
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(group.getEmployeeScheduleIds());
        HoursCalculation calc = calculateHours(schedules);
        return convertToDTO(group, schedules, calc);
    }

    private ScheduleAssignmentGroupDTO convertToDTO(ScheduleAssignmentGroup group, List<EmployeeSchedule> schedules, HoursCalculation calc) {
        ScheduleAssignmentGroupDTO dto = new ScheduleAssignmentGroupDTO();

        dto.setId(group.getId());
        dto.setEmployeeId(group.getEmployeeId());
        dto.setPeriodStart(dateFormat.format(group.getPeriodStart()));
        dto.setPeriodEnd(dateFormat.format(group.getPeriodEnd()));
        dto.setTotalHours(group.getTotalHours());

        // regular_hours (solo regular) se guarda tal cual en DB:
        dto.setRegularHours(group.getRegularHours());

        // assignedHours = regular + festivo (solo para UI)
        dto.setAssignedHours(group.getRegularHours().add(group.getFestivoHours()));

        dto.setOvertimeHours(group.getOvertimeHours());
        dto.setOvertimeType(group.getOvertimeType());
        dto.setFestivoHours(group.getFestivoHours());
        dto.setFestivoType(group.getFestivoType());
        dto.setStatus(getEffectiveStatus(group));

        dto.setEmployeeScheduleIds(group.getEmployeeScheduleIds());

        Map<String, Object> breakdown = createBreakdown(calc.getHoursByType());
        dto.setOvertimeBreakdown(breakdown);

        List<ScheduleDetailDTO> details = schedules.stream()
                .map(this::createScheduleDetail)
                .collect(Collectors.toList());
        dto.setScheduleDetails(details);
        return dto;
    }


    private Map<String, Object> createBreakdown(Map<String, BigDecimal> hoursByType) {
        Map<String, Object> breakdown = new HashMap<>();

        hoursByType.forEach((k, v) -> {
            if (v.compareTo(BigDecimal.ZERO) > 0) breakdown.put(k, v.doubleValue());
        });

        BigDecimal totalRegular = hoursByType.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("EXTRA_") && !e.getKey().startsWith("FESTIVO_"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExtra = hoursByType.entrySet().stream()
                .filter(e -> e.getKey().startsWith("EXTRA_"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFestivo = hoursByType.entrySet().stream()
                .filter(e -> e.getKey().startsWith("FESTIVO_"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Predominantes (con prioridad)
        String predominantExtraCode = determinePredominantWithPriority(hoursByType, true);
        String predominantFestivoCode = determinePredominantWithPriority(hoursByType, false);

        Map<String, String> codeToName = overtimeTypeService.getAllActiveTypes().stream()
                .collect(Collectors.toMap(OvertimeTypeDTO::getCode, OvertimeTypeDTO::getDisplayName, (a, b) -> a));

        breakdown.put("total_regular", totalRegular.doubleValue());
        breakdown.put("total_extra", totalExtra.doubleValue());
        breakdown.put("total_festivo", totalFestivo.doubleValue());
        breakdown.put("predominant_extra_code", predominantExtraCode);
        breakdown.put("predominant_extra_display", predominantExtraCode != null ? codeToName.getOrDefault(predominantExtraCode, predominantExtraCode) : null);
        breakdown.put("predominant_festivo_code", predominantFestivoCode);
        breakdown.put("predominant_festivo_display", predominantFestivoCode != null ? codeToName.getOrDefault(predominantFestivoCode, predominantFestivoCode) : null);

        return breakdown;
    }

    private String determinePredominantWithPriority(Map<String, BigDecimal> hoursByType, boolean forExtra) {
        // Prioridades (mayor = más importante)
        Map<String, Integer> priority = new HashMap<>();
        priority.put("EXTRA_DOMINICAL_NOCTURNA_RECARGO_NOCTURNO", 700);
        priority.put("EXTRA_DOMINICAL_NOCTURNA", 600);
        priority.put("EXTRA_DOMINICAL_DIURNA", 500);
        priority.put("EXTRA_FESTIVO_NOCTURNA", 400);
        priority.put("EXTRA_FESTIVO_DIURNA", 300);
        priority.put("EXTRA_NOCTURNA", 200);
        priority.put("EXTRA_DIURNA", 100);

        priority.put("FESTIVO_NOCTURNA", 400);
        priority.put("FESTIVO_DIURNA", 300);

        String bestCode = null;
        BigDecimal bestHours = BigDecimal.ZERO;
        int bestPriority = -1;

        for (Map.Entry<String, BigDecimal> e : hoursByType.entrySet()) {
            String code = e.getKey();
            BigDecimal h = e.getValue();
            if (h.compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean isCandidate = forExtra ? code.startsWith("EXTRA_") : code.startsWith("FESTIVO_");
            if (!isCandidate) continue;

            int p = priority.getOrDefault(code, 0);
            if (bestCode == null || h.compareTo(bestHours) > 0 || (h.compareTo(bestHours) == 0 && p > bestPriority)) {
                bestCode = code;
                bestHours = h;
                bestPriority = p;
            }
        }
        return bestCode;
    }

    // ========== MÉTODO CAMBIADO AQUÍ ==========
    private ScheduleDetailDTO createScheduleDetail(EmployeeSchedule schedule) {
        return createScheduleDetailWithCalculation(schedule);
    }
    private String getEffectiveStatus(ScheduleAssignmentGroup group) {
        LocalDate today = LocalDate.now();

        // Si no hay fin de periodo, asume activo
        Date end = group.getPeriodEnd();
        if (end == null) return "ACTIVE";

        LocalDate endDate;
        if (end instanceof java.sql.Date) {
            // ✅ camino seguro para java.sql.Date
            endDate = ((java.sql.Date) end).toLocalDate();
        } else {
            // ✅ camino seguro para java.util.Date
            endDate = java.time.Instant.ofEpochMilli(end.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        return today.isAfter(endDate) ? "INACTIVE" : "ACTIVE";
    }

    // ========== MÉTODO NUEVO AGREGADO AQUÍ ==========
    public ScheduleDetailDTO createScheduleDetailWithCalculation(EmployeeSchedule schedule) {
        ScheduleDetailDTO detail = new ScheduleDetailDTO();

        detail.setScheduleId(schedule.getId());
        detail.setShiftName(schedule.getShift() != null ? schedule.getShift().getName() : defaultShiftName);

        detail.setStartDate(dateFormat.format(schedule.getStartDate()));
        detail.setEndDate(dateFormat.format(schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate()));

        List<EmployeeSchedule> soloEste = Collections.singletonList(schedule);
        HoursCalculation calc = calculateHours(soloEste);
        HoursSummary summary = HoursSummary.fromCalculation(calc, overtimeTypeService.getAllActiveTypes());

        // TOTAL CORREGIDO: Solo horas que cuentan para límite (regular + extra)
        detail.setHoursInPeriod(summary.getTotalHours().doubleValue());

        detail.setRegularHours(summary.getRegularWithinLimit().doubleValue());
        detail.setOvertimeHours(summary.getOvertimeHours().doubleValue());
        detail.setFestivoHours(summary.getFestivoHours().doubleValue());
        detail.setOvertimeType(summary.getOvertimeType());
        detail.setFestivoType(summary.getFestivoType());

        log.info("🧮 DETALLE TURNO [{}]: Regular={}h, Extra={}h, Festivo={}h (separado), Total={}h",
                detail.getShiftName(),
                detail.getRegularHours(),
                detail.getOvertimeHours(),
                detail.getFestivoHours(),
                detail.getHoursInPeriod());

        return detail;
    }
    // ===== Validaciones / utilidades de repos =====
    private void validateInputs(Long employeeId, List<Long> scheduleIds) {
        if (employeeId == null) throw new IllegalArgumentException("Employee ID no puede ser nulo");
        if (scheduleIds == null || scheduleIds.isEmpty())
            throw new IllegalArgumentException("Lista de schedule IDs no puede estar vacía");
    }

    private List<EmployeeSchedule> getValidatedSchedules(List<Long> scheduleIds) {
        List<EmployeeSchedule> schedules = scheduleRepository.findAllById(scheduleIds);
        if (schedules.isEmpty())
            throw new IllegalArgumentException("No se encontraron schedules con los IDs proporcionados");
        return schedules;
    }

    private ScheduleAssignmentGroup getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Grupo no encontrado con ID: " + groupId));
    }

    private ScheduleAssignmentGroup findOrCreateGroup(Long employeeId, List<EmployeeSchedule> newSchedules, List<Long> scheduleIds) {
        DatePeriod period = DatePeriod.fromSchedules(newSchedules);
        Optional<ScheduleAssignmentGroup> existing = findOverlappingGroup(employeeId, period);

        if (existing.isPresent()) {
            return updateExistingGroup(existing.get(), scheduleIds, period);
        } else {
            return createNewGroup(employeeId, scheduleIds, period);
        }
    }

    private Optional<ScheduleAssignmentGroup> findOverlappingGroup(Long employeeId, DatePeriod period) {
        return groupRepository.findByEmployeeId(employeeId).stream()
                .filter(group -> period.overlapsWith(group.getPeriodStart(), group.getPeriodEnd()))
                .findFirst();
    }

    private ScheduleAssignmentGroup updateExistingGroup(ScheduleAssignmentGroup group, List<Long> scheduleIds, DatePeriod period) {
        System.out.println("🔄 ACTUALIZANDO GRUPO EXISTENTE:");
        System.out.println("   👥 Employee: " + group.getEmployeeId());
        System.out.println("   📋 Schedules actuales: " + group.getEmployeeScheduleIds());
        System.out.println("   📋 Schedules nuevos: " + scheduleIds);
        System.out.println("   📅 Período actual: " + dateFormat.format(group.getPeriodStart()) + " al " + dateFormat.format(group.getPeriodEnd()));
        System.out.println("   📅 Período nuevo: " + dateFormat.format(period.getStartDate()) + " al " + dateFormat.format(period.getEndDate()));

        // Guardar horas antes del cambio
        BigDecimal oldFestivoHours = group.getFestivoHours();

        scheduleIds.forEach(id -> {
            if (!group.getEmployeeScheduleIds().contains(id)) {
                group.getEmployeeScheduleIds().add(id);
                System.out.println("   ➕ Agregado schedule ID: " + id);
            }
        });

        if (period.getStartDate().before(group.getPeriodStart())) {
            System.out.println("   📅 Extendiendo inicio: " + dateFormat.format(group.getPeriodStart()) + " → " + dateFormat.format(period.getStartDate()));
            group.setPeriodStart(period.getStartDate());
        }
        if (period.getEndDate().after(group.getPeriodEnd())) {
            System.out.println("   📅 Extendiendo fin: " + dateFormat.format(group.getPeriodEnd()) + " → " + dateFormat.format(period.getEndDate()));
            group.setPeriodEnd(period.getEndDate());
        }

        System.out.println("   🎉 Horas festivas previas: " + oldFestivoHours + "h");
        System.out.println("   📋 Total schedules en grupo: " + group.getEmployeeScheduleIds().size());

        return group;
    }
    private ScheduleAssignmentGroup createNewGroup(Long employeeId, List<Long> scheduleIds, DatePeriod period) {
        ScheduleAssignmentGroup group = new ScheduleAssignmentGroup();
        group.setEmployeeId(employeeId);
        group.setPeriodStart(period.getStartDate());
        group.setPeriodEnd(period.getEndDate());
        group.setEmployeeScheduleIds(new ArrayList<>(scheduleIds));
        group.setStatus(defaultGroupStatus);
        return group;
    }

    private Set<LocalDate> getHolidayDates() {
        var list = holidayService.getAllHolidays();
        if (list == null) return Collections.emptySet();
        return list.stream()
                .filter(Objects::nonNull)
                .map(h -> h.getHolidayDate())
                .filter(Objects::nonNull) // ← filtra fechas nulas
                .collect(Collectors.toSet());
    }

    private BigDecimal getWeeklyHoursLimit() {
        try {
            String value = configService.getByType("WEEKLY_HOURS").getValue();
            return TimeUtils.parseHourOrDecimal(value);
        } catch (Exception e) {
            log.warn("⚠️ Error obteniendo límite semanal, usando fallback: 44h", e);
            return new BigDecimal("44.0");
        }
    }

    // ===== Clases de apoyo =====
    private static class DatePeriod {
        private final Date startDate;
        private final Date endDate;
        private final LocalDate startLocalDate;
        private final LocalDate endLocalDate;

        private DatePeriod(Date startDate, Date endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.startLocalDate = toLocalDate(startDate);
            this.endLocalDate = toLocalDate(endDate);
        }

        static DatePeriod fromSchedules(List<EmployeeSchedule> schedules) {
            Date start = schedules.stream().map(EmployeeSchedule::getStartDate).filter(Objects::nonNull).min(Date::compareTo).orElse(new Date());
            Date end = schedules.stream().map(es -> es.getEndDate() != null ? es.getEndDate() : es.getStartDate()).filter(Objects::nonNull).max(Date::compareTo).orElse(new Date());
            return new DatePeriod(start, end);
        }

        static DatePeriod fromSchedule(EmployeeSchedule schedule) {
            Date start = schedule.getStartDate();
            Date end = schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate();
            return new DatePeriod(start, end);
        }

        boolean overlapsWith(Date otherStart, Date otherEnd) {
            return !this.startDate.after(otherEnd) && !otherStart.after(this.endDate);
        }

        Date getStartDate() {
            return startDate;
        }

        Date getEndDate() {
            return endDate;
        }

        LocalDate getStartLocalDate() {
            return startLocalDate;
        }

        LocalDate getEndLocalDate() {
            return endLocalDate;
        }

        private static LocalDate toLocalDate(Date date) {
            if (date == null) return LocalDate.now();
            if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    private static class HoursCalculation {
        private final Map<String, BigDecimal> hoursByType;
        private final BigDecimal totalHours;

        HoursCalculation(Map<String, BigDecimal> hoursByType, BigDecimal totalHours) {
            this.hoursByType = hoursByType;
            this.totalHours = totalHours;
        }

        Map<String, BigDecimal> getHoursByType() {
            return hoursByType;
        }

        BigDecimal getTotalHours() {
            return totalHours;
        }
    }

    private static class HoursSummary {
        // totales "puros" por categoría
        private final BigDecimal totalHours;         // regular + festivo + extra
        private final BigDecimal regularWithinLimit; // SOLO regular (no festivo, no extra)
        private final BigDecimal overtimeHours;      // SOLO extra (EXTRA_*)
        private final BigDecimal festivoHours;       // SOLO festivo (FESTIVO_*)
        private final String festivoType;            // predominante entre FESTIVO_*
        private final String overtimeType;           // predominante entre EXTRA_*

        // derivado para DTO (no se guarda en DB): "horas asignadas" = regular + festivo
        private final BigDecimal assignedHours;

        private HoursSummary(BigDecimal totalHours,
                             BigDecimal regularWithinLimit,
                             BigDecimal overtimeHours,
                             BigDecimal festivoHours,
                             String festivoType,
                             String overtimeType) {

            this.totalHours = totalHours.setScale(2, RoundingMode.HALF_UP);
            this.regularWithinLimit = regularWithinLimit.setScale(2, RoundingMode.HALF_UP);
            this.overtimeHours = overtimeHours.setScale(2, RoundingMode.HALF_UP);
            this.festivoHours = festivoHours.setScale(2, RoundingMode.HALF_UP);
            this.festivoType = festivoType;
            this.overtimeType = overtimeType;
            this.assignedHours = this.regularWithinLimit.add(this.festivoHours).setScale(2, RoundingMode.HALF_UP);
        }

        static HoursSummary fromCalculation(HoursCalculation calc, List<OvertimeTypeDTO> types) {
            Map<String, BigDecimal> byType = calc.getHoursByType();

            BigDecimal regularTotal = BigDecimal.ZERO;
            BigDecimal extraTotal = BigDecimal.ZERO;
            BigDecimal festivoTotal = BigDecimal.ZERO;

            Map<String, String> codeToName = types.stream()
                    .collect(Collectors.toMap(OvertimeTypeDTO::getCode, OvertimeTypeDTO::getDisplayName, (a, b) -> a));

            String predominantExtraType = null;
            BigDecimal maxExtraHours = BigDecimal.ZERO;
            String predominantFestivoType = null;
            BigDecimal maxFestivoHours = BigDecimal.ZERO;

            log.info("🔍 CLASIFICACIÓN FINAL CORREGIDA:");

            Set<String> regularCodes = types.stream()
                    .map(OvertimeTypeDTO::getCode)
                    .filter(code -> code.startsWith("REGULAR_"))
                    .collect(Collectors.toSet());

            Set<String> festivoCodes = types.stream()
                    .map(OvertimeTypeDTO::getCode)
                    .filter(code -> code.startsWith("FESTIVO_") && !code.contains("DOMINICAL"))
                    .collect(Collectors.toSet());

            Set<String> extraCodes = types.stream()
                    .map(OvertimeTypeDTO::getCode)
                    .filter(code -> code.startsWith("EXTRA_") || code.startsWith("DOMINICAL_"))
                    .collect(Collectors.toSet());

            for (Map.Entry<String, BigDecimal> e : byType.entrySet()) {
                String code = e.getKey();
                BigDecimal hours = e.getValue();
                if (hours.compareTo(BigDecimal.ZERO) <= 0) continue;

                if (extraCodes.contains(code)) {
                    extraTotal = extraTotal.add(hours);
                    if (hours.compareTo(maxExtraHours) > 0) {
                        maxExtraHours = hours;
                        predominantExtraType = codeToName.getOrDefault(code, code);
                    }
                    log.info("   🔴 {} = {}h → EXTRA", code, hours);

                } else if (festivoCodes.contains(code)) {
                    festivoTotal = festivoTotal.add(hours);
                    if (hours.compareTo(maxFestivoHours) > 0) {
                        maxFestivoHours = hours;
                        predominantFestivoType = codeToName.getOrDefault(code, code);
                    }
                    log.info("   🟡 {} = {}h → FESTIVO", code, hours);

                } else if (regularCodes.contains(code)) {
                    regularTotal = regularTotal.add(hours);
                    log.info("   🟢 {} = {}h → REGULAR", code, hours);

                } else {
                    // Fallback
                    if (code.startsWith("EXTRA_") || code.contains("DOMINICAL")) {
                        extraTotal = extraTotal.add(hours);
                        log.info("   🔴 {} = {}h → EXTRA (fallback)", code, hours);
                    } else if (code.startsWith("FESTIVO_")) {
                        festivoTotal = festivoTotal.add(hours);
                        log.info("   🟡 {} = {}h → FESTIVO (fallback)", code, hours);
                    } else {
                        regularTotal = regularTotal.add(hours);
                        log.info("   🟢 {} = {}h → REGULAR (fallback)", code, hours);
                    }
                }
            }

            if (predominantExtraType == null && extraTotal.compareTo(BigDecimal.ZERO) > 0) {
                predominantExtraType = "Horas Extra";
            }
            if (predominantFestivoType == null && festivoTotal.compareTo(BigDecimal.ZERO) > 0) {
                predominantFestivoType = "Horas Festivas";
            }

            // TOTAL CORREGIDO: regular + extra (festivos NO se suman al total)
            BigDecimal total = regularTotal.add(extraTotal);

            log.info("📊 TOTALES FINALES CORREGIDOS:");
            log.info("   🟢 Regular: {}h", regularTotal);
            log.info("   🔴 Extra: {}h", extraTotal);
            log.info("   🟡 Festivo: {}h (NO sumado al total)", festivoTotal);
            log.info("   📊 TOTAL EFECTIVO: {}h (regular + extra)", total);

            return new HoursSummary(
                    total,          // SOLO regular + extra
                    regularTotal,
                    extraTotal,
                    festivoTotal,
                    predominantFestivoType,
                    predominantExtraType
            );
        }
        BigDecimal getTotalHours() {
            return totalHours;
        }

        BigDecimal getRegularWithinLimit() {
            return regularWithinLimit;
        }

        BigDecimal getOvertimeHours() {
            return overtimeHours;
        }

        BigDecimal getFestivoHours() {
            return festivoHours;
        }

        String getFestivoType() {
            return festivoType;
        }

        String getOvertimeType() {
            return overtimeType;
        }

        // Para el DTO (no se guarda en DB)
        BigDecimal getAssignedHours() {
            return assignedHours;
        }
    }


    /** Segmento ya dividido en diurno/nocturno (minutos) */
    private static class HourDetail {
        LocalDate date;
        int dayOfWeek;
        int segmentMinutes;      // minutos del segmento
        boolean isNightSegment;  // true = nocturno, false = diurno
        boolean isSunday;
        boolean isHoliday;
    }

    private static class TimeUtils {

        /** Devuelve minutos separados en diurno / nocturno para un bloque. Maneja cruces de medianoche */
        static DayNightSplit splitDayNight(String start, String end, int nightStart, int nightEnd) {
            int s = toMinutes(normalizeTimeFormat(start));
            int e = toMinutes(normalizeTimeFormat(end));
            if (s == e) return new DayNightSplit(0, 0); // 0 minutos

            int total = (e > s) ? (e - s) : (1440 - s + e);

            // Nocturno = [nightStart, 1440) U [0, nightEnd)
            int night1Start = nightStart, night1End = 1440;
            int night2Start = 0, night2End = nightEnd;

            int nightMinutes = 0;
            int cursor = s;
            int remaining = total;
            while (remaining > 0) {
                int current = cursor % 1440;
                int step = Math.min(remaining, 1440 - current);

                nightMinutes += overlapMinutes(current, current + step, night1Start, night1End);
                nightMinutes += overlapMinutes(current, current + step, night2Start, night2End);

                cursor += step;
                remaining -= step;
            }

            int dayMinutes = total - nightMinutes;
            return new DayNightSplit(dayMinutes, nightMinutes);
        }

        private static int overlapMinutes(int aStart, int aEnd, int bStart, int bEnd) {
            int start = Math.max(aStart, bStart);
            int end = Math.min(aEnd, bEnd);
            return Math.max(0, end - start);
        }

        static BigDecimal calculateTotalShiftHours(Shifts shift) {
            if (shift == null || shift.getShiftDetails() == null) return BigDecimal.ZERO;
            int totalMinutes = shift.getShiftDetails().stream()
                    .mapToInt(TimeUtils::calculateDetailMinutes)
                    .sum();
            return BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        static int calculateDetailMinutes(ShiftDetail detail) {
            if (detail == null || detail.getStartTime() == null || detail.getEndTime() == null) return 0;
            int start = toMinutes(detail.getStartTime());
            int end = toMinutes(detail.getEndTime());
            return Math.max(0, (end >= start) ? (end - start) : (1440 - start + end));
        }

        static BigDecimal parseHourOrDecimal(String value) {
            if (value == null) return BigDecimal.ZERO;
            try {
                if (value.contains(":")) {
                    String[] parts = value.split(":");
                    BigDecimal h = new BigDecimal(parts[0]);
                    BigDecimal m = new BigDecimal(parts[1]).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
                    return h.add(m);
                }
                return new BigDecimal(value.replace(",", "."));
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }

        static String normalizeTimeFormat(String time) {
            if (time == null) return "00:00:00";
            String[] p = time.split(":");
            if (p.length == 2) return time + ":00";
            if (p.length == 1) return time + ":00:00";
            return time;
        }

        static int toMinutes(String timeStr) {
            try {
                String[] parts = normalizeTimeFormat(timeStr).split(":");
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } catch (Exception e) {
                return 0;
            }
        }

        static class DayNightSplit {
            final int dayMinutes;
            final int nightMinutes;
            DayNightSplit(int dayMinutes, int nightMinutes) {
                this.dayMinutes = dayMinutes;
                this.nightMinutes = nightMinutes;
            }
        }
    }
}

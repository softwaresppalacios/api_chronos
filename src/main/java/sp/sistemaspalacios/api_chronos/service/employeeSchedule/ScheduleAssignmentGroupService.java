package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    // >>> NUEVO: inyectamos para saber si hay exención (incluye NO_APLICAR_RECARGO)
    private final HolidayExemptionService holidayExemptionService;

    // >>> NUEVO: base URL por configuración (sin hardcode; si no está, no se consulta)

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    // ======== Configuración nocturna ========
    private int getNightStartMinutes() {
        try {
            String raw = configService.getByType("NIGHT_START").getValue();
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
        syncStatusWithDates(group);
        group = groupRepository.save(group);

        return convertToDTO(group, newSchedules, hoursCalc);
    }

    public List<ScheduleAssignmentGroupDTO> getEmployeeGroups(Long employeeId) {
        var groups = groupRepository.findByEmployeeId(employeeId);
        for (var g : groups) syncStatusWithDates(g); // ← AQUI
        return groups.stream().map(this::convertGroupToDTO).collect(Collectors.toList());
    }

    @Transactional
    public ScheduleAssignmentGroupDTO getLatestGroupForEmployee(Long employeeId) {
        var opt = groupRepository.findByEmployeeId(employeeId).stream()
                .max(Comparator.comparing(ScheduleAssignmentGroup::getId));
        if (opt.isEmpty()) return null;
        var g = opt.get();
        syncStatusWithDates(g); // ← AQUI
        return convertGroupToDTO(g);
    }

    @Transactional
    public ScheduleAssignmentGroupDTO getGroupById(Long groupId) {
        var g = getGroupOrThrow(groupId);
        syncStatusWithDates(g); // ← AQUI
        return convertToDTO(g, scheduleRepository.findAllById(g.getEmployeeScheduleIds()),
                calculateHours(scheduleRepository.findAllById(g.getEmployeeScheduleIds())));
    }

    @Transactional
    public ScheduleAssignmentGroupDTO recalculateGroup(Long groupId) {
        ScheduleAssignmentGroup group = getGroupOrThrow(groupId);
        List<EmployeeSchedule> schedules = scheduleRepository.findAllByIdWithShift(group.getEmployeeScheduleIds());
        HoursCalculation hoursCalc = calculateHours(schedules);
        updateGroupTotals(group, hoursCalc);
        syncStatusWithDates(group);
        group = groupRepository.save(group);
        return convertToDTO(group, schedules, hoursCalc);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        if (!groupRepository.existsById(groupId))
            throw new IllegalArgumentException("Grupo no encontrado con ID: " + groupId);
        groupRepository.deleteById(groupId);
    }

    // ===== Cálculo de horas (POR SEMANA NATURAL) =====
    private HoursCalculation calculateHours(List<EmployeeSchedule> schedules) {
        BigDecimal weeklyLimit = getWeeklyHoursLimit();
        Set<LocalDate> holidayDates = getHolidayDates();

        Map<String, OvertimeTypeDTO> availableTypes = overtimeTypeService.getAllActiveTypes()
                .stream().collect(Collectors.toMap(OvertimeTypeDTO::getCode, t -> t));

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

        List<HourDetail> segments = processAllSchedulesChronologically(schedules, holidayDates);
        Map<String, BigDecimal> finalHoursByType = assignHourTypesWithWeeklyLimit(segments, weeklyLimit, availableTypes);

        BigDecimal totalHours = finalHoursByType.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HoursCalculation(finalHoursByType, totalHours);
    }

    private List<HourDetail> processAllSchedulesChronologically(List<EmployeeSchedule> schedules, Set<LocalDate> holidayDates) {
        List<HourDetail> all = new ArrayList<>();
        final int nightStart = getNightStartMinutes();
        final int nightEnd = getNightEndMinutes();

        log.info("🔍 PROCESANDO {} SCHEDULES INDIVIDUALES CON FESTIVOS (usando timeBlocks si existen)", schedules.size());
        log.info("🎉 Festivos conocidos globalmente: {}", holidayDates);

        for (EmployeeSchedule schedule : schedules) {
            if (schedule.getShift() == null) continue;

            log.info("📋 Schedule ID: {} | Período: {} - {}",
                    schedule.getId(), schedule.getStartDate(), schedule.getEndDate());

            // 1) Días a procesar (ya tienes este helper)
            List<LocalDate> datesToProcess = getDatesToProcess(schedule);
            int festivosEnEsteSchedule = 0;

            for (LocalDate date : datesToProcess) {
                int dow = date.getDayOfWeek().getValue();
                boolean esFestivo = holidayDates.contains(date);
                if (esFestivo) {
                    festivosEnEsteSchedule++;
                    log.info("   🎉 FESTIVO DETECTADO EN TURNO: {} (dow: {})", date, dow);
                }

                // 2) Buscamos el EmployeeScheduleDay para esta fecha (si existen días generados)
                var dayOpt = Optional.ofNullable(schedule.getDays())
                        .orElseGet(Collections::emptyList)
                        .stream()
                        .filter(d -> {
                            LocalDate dDate;
                            var raw = d.getDate();
                            if (raw instanceof java.sql.Date) {
                                dDate = ((java.sql.Date) raw).toLocalDate();
                            } else {
                                dDate = raw.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            }
                            return dDate.equals(date);
                        })
                        .findFirst();

                boolean anyBlockProcessed = false;

                if (dayOpt.isPresent() && dayOpt.get().getTimeBlocks() != null && !dayOpt.get().getTimeBlocks().isEmpty()) {
                    // 3) ✅ PRIORIDAD: usar los bloques reales del día (respetan overrides del modal)
                    for (var tb : dayOpt.get().getTimeBlocks()) {
                        String start = TimeUtils.normalizeTimeFormat(tb.getStartTime().toString()); // HH:mm:ss
                        String end   = TimeUtils.normalizeTimeFormat(tb.getEndTime().toString());   // HH:mm:ss

                        TimeUtils.DayNightSplit split = TimeUtils.splitDayNight(start, end, nightStart, nightEnd);

                        if (split.dayMinutes > 0) {
                            HourDetail hd = new HourDetail();
                            hd.date = date;
                            hd.dayOfWeek = dow;
                            hd.segmentMinutes = split.dayMinutes;
                            hd.isNightSegment = false;
                            hd.isSunday = (dow == 7);
                            hd.isHoliday = shouldTreatAsHoliday(date, schedule, esFestivo);
                            all.add(hd);
                        }
                        if (split.nightMinutes > 0) {
                            HourDetail hn = new HourDetail();
                            hn.date = date;
                            hn.dayOfWeek = dow;
                            hn.segmentMinutes = split.nightMinutes;
                            hn.isNightSegment = true;
                            hn.isSunday = (dow == 7);
                            hn.isHoliday = shouldTreatAsHoliday(date, schedule, esFestivo);
                            all.add(hn);
                        }
                    }
                    anyBlockProcessed = true;
                }

                if (!anyBlockProcessed) {
                    // 4) 🔁 FALLBACK: no hay timeBlocks para ese día -> usamos ShiftDetail (comportamiento anterior)
                    if (schedule.getShift().getShiftDetails() == null) continue;

                    for (ShiftDetail d : schedule.getShift().getShiftDetails()) {
                        if (!Objects.equals(d.getDayOfWeek(), dow) ||
                                d.getStartTime() == null || d.getEndTime() == null) continue;

                        TimeUtils.DayNightSplit split = TimeUtils.splitDayNight(
                                d.getStartTime(), d.getEndTime(), nightStart, nightEnd);

                        if (split.dayMinutes > 0) {
                            HourDetail hd = new HourDetail();
                            hd.date = date;
                            hd.dayOfWeek = dow;
                            hd.segmentMinutes = split.dayMinutes;
                            hd.isNightSegment = false;
                            hd.isSunday = (dow == 7);
                            hd.isHoliday = shouldTreatAsHoliday(date, schedule, esFestivo);
                            all.add(hd);
                        }
                        if (split.nightMinutes > 0) {
                            HourDetail hn = new HourDetail();
                            hn.date = date;
                            hn.dayOfWeek = dow;
                            hn.segmentMinutes = split.nightMinutes;
                            hn.isNightSegment = true;
                            hn.isSunday = (dow == 7);
                            hn.isHoliday = shouldTreatAsHoliday(date, schedule, esFestivo);
                            all.add(hn);
                        }
                    }
                }
            }

            log.info("📊 FESTIVOS EN ESTE SCHEDULE: {} días festivos procesados", festivosEnEsteSchedule);
        }

        long totalSegmentosFestivos = all.stream().mapToLong(h -> h.isHoliday ? 1 : 0).sum();
        log.info("📈 RESUMEN PROCESAMIENTO INDIVIDUAL:");
        log.info("   Total segmentos: {}", all.size());
        log.info("   Segmentos festivos: {}", totalSegmentosFestivos);

        return all;
    }

    private boolean shouldTreatAsHoliday(LocalDate date, EmployeeSchedule schedule, boolean isHoliday) {
        if (!isHoliday) return false;

        // Si hay exención registrada (incluye NO_APLICAR_RECARGO) → NO se trata como festivo (será REGULAR)
        try {
            if (holidayExemptionService != null &&
                    holidayExemptionService.hasExemption(schedule.getEmployeeId(), date)) {
                return false;
            }
        } catch (Exception ignore) {}

        // Si el día no fue generado (exento real con razón), no cuenta como festivo
        if (schedule.getDays() != null) {
            boolean dayExists = schedule.getDays().stream()
                    .anyMatch(day -> {
                        LocalDate dayDate = (day.getDate() instanceof java.sql.Date)
                                ? ((java.sql.Date) day.getDate()).toLocalDate()
                                : day.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        return dayDate.equals(date);
                    });
            if (!dayExists) return false;
        }

        // Festivo real (con recargo)
        return true;
    }

    private List<LocalDate> getDatesToProcess(EmployeeSchedule schedule) {
        if (schedule.getDays() != null && !schedule.getDays().isEmpty()) {
            return schedule.getDays().stream()
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
        } else {
            DatePeriod period = DatePeriod.fromSchedule(schedule);
            LocalDate start = period.getStartLocalDate();
            LocalDate end = period.getEndLocalDate();

            List<LocalDate> days = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                days.add(date);
            }
            return days;
        }
    }

    private Map<String, BigDecimal> assignHourTypesWithWeeklyLimit(
            List<HourDetail> segments,
            BigDecimal weeklyLimit,
            Map<String, OvertimeTypeDTO> availableTypes
    ) {
        Map<String, BigDecimal> hoursByType = new HashMap<>();
        Map<String, BigDecimal> accumulatedByWeek = new HashMap<>();

        WeekFields wf = WeekFields.ISO;

        log.info("🚀 ASIGNACIÓN INDIVIDUAL - Límite semanal: {}h", weeklyLimit);
        log.info("📊 Total segmentos a procesar: {}", segments.size());

        for (HourDetail seg : segments) {
            String weekKey = weekKey(seg.date, wf);
            BigDecimal acc = accumulatedByWeek.getOrDefault(weekKey, BigDecimal.ZERO);

            BigDecimal hours = BigDecimal.valueOf(seg.segmentMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            log.debug("📅 {} [{}] - {}min = {}h | Festivo: {} | Domingo: {} | Nocturno: {}",
                    seg.date, weekKey, seg.segmentMinutes, hours, seg.isHoliday, seg.isSunday, seg.isNightSegment);

            if (seg.isHoliday) {
                // ✅ FESTIVO: Cuenta como REGULAR_* (para total/semana) y además como FESTIVO_*
                String regularCode = seg.isNightSegment ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA";
                String festivoCode = convertToFestivoType("", seg, availableTypes);

                addHoursToMap(hoursByType, regularCode, hours); // ← suma a REGULAR
                addHoursToMap(hoursByType, festivoCode, hours); // ← referencia FESTIVO

                acc = acc.add(hours); // ← también cuenta para el límite semanal
                log.info("🎉 {} [{}] FESTIVO: +{}h REGULAR ({}) y +{}h FESTIVO ({})",
                        seg.date, weekKey, hours, regularCode, hours, festivoCode);
            }
            else if (seg.isSunday) {
                // DOMINGO: como extra dominical
                String dominicalCode = convertToExtraType("", seg, availableTypes);
                addHoursToMap(hoursByType, dominicalCode, hours);
                log.info("🛡️ {} [{}] DOMINICAL: +{}h como {}", seg.date, weekKey, hours, dominicalCode);
                // Nota: no afecta el acumulado REGULAR semanal (no sumamos a acc)
            }
            else {
                // DÍAS NORMALES: aplicar límite semanal
                boolean exceeds = acc.compareTo(weeklyLimit) >= 0;
                BigDecimal remaining = weeklyLimit.subtract(acc);

                if (exceeds) {
                    String extraCode = convertToExtraType("", seg, availableTypes);
                    addHoursToMap(hoursByType, extraCode, hours);
                    acc = acc.add(hours);
                    log.info("🔴 {} [{}] EXCESO: +{}h como {}", seg.date, weekKey, hours, extraCode);
                } else if (remaining.compareTo(hours) < 0) {
                    BigDecimal regularPart = remaining.max(BigDecimal.ZERO);
                    BigDecimal extraPart = hours.subtract(regularPart);

                    if (regularPart.compareTo(BigDecimal.ZERO) > 0) {
                        addHoursToMap(hoursByType, seg.isNightSegment ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA", regularPart);
                    }
                    String extraCode = convertToExtraType("", seg, availableTypes);
                    addHoursToMap(hoursByType, extraCode, extraPart);

                    acc = acc.add(hours);
                    log.info("🟡 {} [{}] TOPE: {}h REGULAR + {}h EXTRA ({})",
                            seg.date, weekKey, regularPart, extraPart, extraCode);
                } else {
                    addHoursToMap(hoursByType, seg.isNightSegment ? "REGULAR_NOCTURNA" : "REGULAR_DIURNA", hours);
                    acc = acc.add(hours);
                    log.info("🟢 {} [{}] REGULAR: +{}h", seg.date, weekKey, hours);
                }
            }

            accumulatedByWeek.put(weekKey, acc);
        }

        // Log resumen final
        log.info("📈 RESUMEN FINAL POR TIPO (INDIVIDUAL):");
        hoursByType.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> log.info("   {} = {}h", e.getKey(), e.getValue()));

        return hoursByType;
    }

    private String convertToFestivoType(String baseCode, HourDetail d, Map<String, OvertimeTypeDTO> availableTypes) {
        String festivoCode = d.isNightSegment ? "FESTIVO_NOCTURNA" : "FESTIVO_DIURNA";
        if (availableTypes.containsKey(festivoCode)) {
            return festivoCode;
        }
        log.warn("⚠️ Tipo FESTIVO no encontrado en BD: {}. Usando fallback.", festivoCode);
        return festivoCode;
    }

    private String weekKey(LocalDate date, WeekFields wf) {
        int y = date.get(wf.weekBasedYear());
        int w = date.get(wf.weekOfWeekBasedYear());
        return y + "-W" + String.format("%02d", w);
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
        log.info("🔍 HORAS CALCULADAS POR TIPO:");
        calc.getHoursByType().forEach((code, hours) -> {
            if (hours.compareTo(BigDecimal.ZERO) > 0) {
                log.info("   {} = {}h", code, hours);
            }
        });

        HoursSummary summary = HoursSummary.fromCalculation(calc, overtimeTypeService.getAllActiveTypes());

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

    private ScheduleAssignmentGroupDTO convertGroupToDTO(ScheduleAssignmentGroup group) {
        List<EmployeeSchedule> schedules = scheduleRepository.findAllByIdWithShift(group.getEmployeeScheduleIds());
        HoursCalculation calc = calculateHours(schedules);
        return convertToDTO(group, schedules, calc);
    }

    private ScheduleAssignmentGroupDTO convertToDTO(ScheduleAssignmentGroup group,
                                                    List<EmployeeSchedule> schedules,
                                                    HoursCalculation calc) {
        ScheduleAssignmentGroupDTO dto = new ScheduleAssignmentGroupDTO();

        dto.setId(group.getId());
        dto.setEmployeeId(group.getEmployeeId());

        dto.setPeriodStart(dateFormat.format(group.getPeriodStart()));
        dto.setPeriodEnd(dateFormat.format(group.getPeriodEnd()));
        dto.setEmployeeScheduleIds(group.getEmployeeScheduleIds());
        dto.setStatus(getEffectiveStatus(group));

        HoursSummary summary = HoursSummary.fromCalculation(calc, overtimeTypeService.getAllActiveTypes());

        dto.setTotalHours(summary.getTotalHours());
        dto.setRegularHours(summary.getRegularWithinLimit());
        dto.setOvertimeHours(summary.getOvertimeHours());
        dto.setFestivoHours(summary.getFestivoHours());
        dto.setAssignedHours(summary.getAssignedHours());
        dto.setOvertimeType(summary.getOvertimeType());
        dto.setFestivoType(summary.getFestivoType());

        dto.setOvertimeBreakdown(createBreakdown(calc.getHoursByType()));

        List<ScheduleDetailDTO> details = schedules.stream()
                .map(this::createScheduleDetailWithCalculation)
                .collect(Collectors.toList());
        dto.setScheduleDetails(details);
        log.info("DETALLES CREADOS: {} turnos", details.size());
        details.forEach(d -> {
            log.info("  Turno: {} - Regular: {}h, Extra: {}h, Festivo: {}h",
                    d.getShiftName(), d.getRegularHours(), d.getOvertimeHours(), d.getFestivoHours());
        });


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

    private ScheduleDetailDTO createScheduleDetail(EmployeeSchedule schedule) {
        return createScheduleDetailWithCalculation(schedule);
    }

    @Transactional
    private void syncStatusWithDates(ScheduleAssignmentGroup g) {
        String effective = getEffectiveStatus(g); // ya lo tienes
        if (!Objects.equals(g.getStatus(), effective)) {
            g.setStatus(effective);
            groupRepository.save(g); // ← ahora sí se guarda en BD
        }
    }

    private String getEffectiveStatus(ScheduleAssignmentGroup group) {
        if (group.getPeriodEnd() == null) return "ACTIVE";

        LocalDate today = LocalDate.now();
        LocalDate endDate = convertToLocalDate(group.getPeriodEnd());

        return today.isAfter(endDate) ? "INACTIVE" : "ACTIVE";
    }

    @Transactional
    public void autoUpdateExpiredGroups() {
        log.info("🔄 Actualizando automáticamente estados de grupos expirados...");

        List<ScheduleAssignmentGroup> allGroups = groupRepository.findAll();
        int updatedCount = 0;

        for (ScheduleAssignmentGroup group : allGroups) {
            String currentStatus = group.getStatus();
            String effectiveStatus = getEffectiveStatus(group);

            if (!Objects.equals(currentStatus, effectiveStatus)) {
                group.setStatus(effectiveStatus);
                updatedCount++;
            }
        }

        groupRepository.saveAll(allGroups);
        log.info("✅ {} grupos actualizados automáticamente", updatedCount);
    }

    private LocalDate convertToLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public ScheduleDetailDTO createScheduleDetailWithCalculation(EmployeeSchedule schedule) {
        ScheduleDetailDTO detail = new ScheduleDetailDTO();

        detail.setScheduleId(schedule.getId());
        detail.setStartDate(dateFormat.format(schedule.getStartDate()));
        detail.setEndDate(dateFormat.format(schedule.getEndDate() != null ? schedule.getEndDate() : schedule.getStartDate()));

        // >>> NUEVO: cargar ID y nombre del turno
        if (schedule.getShift() != null) {
            var shift = schedule.getShift();
            detail.setShiftId(shift.getId());

            // Asume que Shifts tiene getName(); si tu campo se llama distinto cámbialo aquí.
            String name = null;
            try { name = shift.getName(); } catch (Exception ignored) {}
            if (name == null || name.isBlank()) {
                // Fallbacks comunes si tu entidad lo nombró distinto
                try { name = (String) shift.getClass().getMethod("getShiftName").invoke(shift); } catch (Exception ignored) {}
                if (name == null || name.isBlank()) {
                    try { name = (String) shift.getClass().getMethod("getTitle").invoke(shift); } catch (Exception ignored) {}
                }
            }
            detail.setShiftName((name != null && !name.isBlank()) ? name : "Turno #" + shift.getId());
        } else {
            detail.setShiftName("Sin turno");
        }
        // <<< FIN NUEVO

        // Cálculo de horas (lo que ya tenías)
        List<EmployeeSchedule> soloEste = Collections.singletonList(schedule);
        HoursCalculation calc = calculateHours(soloEste);
        HoursSummary summary = HoursSummary.fromCalculation(calc, overtimeTypeService.getAllActiveTypes());

        detail.setHoursInPeriod(summary.getTotalHours().doubleValue());
        detail.setRegularHours(summary.getRegularWithinLimit().doubleValue());
        detail.setOvertimeHours(summary.getOvertimeHours().doubleValue());
        detail.setFestivoHours(summary.getFestivoHours().doubleValue());
        detail.setOvertimeType(summary.getOvertimeType());
        detail.setFestivoType(summary.getFestivoType());

        log.info("====== DEBUG SCHEDULE DETAIL ======");
        log.info("Schedule ID: {}", schedule.getId());
        log.info("Shift Name: {}", detail.getShiftName());
        log.info("Period: {} to {}", detail.getStartDate(), detail.getEndDate());
        log.info("Hours calculated - Total: {}, Regular: {}, Overtime: {}, Festivo: {}",
                detail.getHoursInPeriod(),
                detail.getRegularHours(),
                detail.getOvertimeHours(),
                detail.getFestivoHours());
        log.info("Types - Overtime: {}, Festivo: {}", detail.getOvertimeType(), detail.getFestivoType());
        log.info("====================================");

        return detail;
    }

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
        scheduleIds.forEach(id -> {
            if (!group.getEmployeeScheduleIds().contains(id)) {
                group.getEmployeeScheduleIds().add(id);
            }
        });

        if (period.getStartDate().before(group.getPeriodStart())) {
            group.setPeriodStart(period.getStartDate());
        }
        if (period.getEndDate().after(group.getPeriodEnd())) {
            group.setPeriodEnd(period.getEndDate());
        }

        return group;
    }

    private ScheduleAssignmentGroup createNewGroup(Long employeeId, List<Long> scheduleIds, DatePeriod period) {
        ScheduleAssignmentGroup group = new ScheduleAssignmentGroup();
        group.setEmployeeId(employeeId);
        group.setPeriodStart(period.getStartDate());
        group.setPeriodEnd(period.getEndDate());
        group.setEmployeeScheduleIds(new ArrayList<>(scheduleIds));
        return group;
    }

    private Set<LocalDate> getHolidayDates() {
        var list = holidayService.getAllHolidays();
        if (list == null) return Collections.emptySet();
        return list.stream()
                .filter(Objects::nonNull)
                .map(h -> h.getHolidayDate())
                .filter(Objects::nonNull)
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
        private final BigDecimal totalHours;         // regular + extra (festivo NO suma al total)
        private final BigDecimal regularWithinLimit; // SOLO regular
        private final BigDecimal overtimeHours;      // SOLO extra
        private final BigDecimal festivoHours;       // SOLO festivo
        private final String festivoType;
        private final String overtimeType;
        private final BigDecimal assignedHours;      // regular + festivo

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

            BigDecimal total = regularTotal.add(extraTotal);

            log.info("📊 TOTALES FINALES CORREGIDOS:");
            log.info("   🟢 Regular: {}h", regularTotal);
            log.info("   🔴 Extra: {}h", extraTotal);
            log.info("   🟡 Festivo: {}h (NO sumado al total)", festivoTotal);
            log.info("   📊 TOTAL EFECTIVO: {}h (regular + extra)", total);

            return new HoursSummary(
                    total,
                    regularTotal,
                    extraTotal,
                    festivoTotal,
                    predominantFestivoType,
                    predominantExtraType
            );
        }

        BigDecimal getTotalHours() { return totalHours; }
        BigDecimal getRegularWithinLimit() { return regularWithinLimit; }
        BigDecimal getOvertimeHours() { return overtimeHours; }
        BigDecimal getFestivoHours() { return festivoHours; }
        String getFestivoType() { return festivoType; }
        String getOvertimeType() { return overtimeType; }
        BigDecimal getAssignedHours() { return assignedHours; }
    }

    /** Segmento ya dividido en diurno/nocturno (minutos) */
    private static class HourDetail {
        LocalDate date;
        int dayOfWeek;
        int segmentMinutes;
        boolean isNightSegment;
        boolean isSunday;
        boolean isHoliday;
    }

    private static class TimeUtils {
        static DayNightSplit splitDayNight(String start, String end, int nightStart, int nightEnd) {
            int s = toMinutes(normalizeTimeFormat(start));
            int e = toMinutes(normalizeTimeFormat(end));
            if (s == e) return new DayNightSplit(0, 0);

            int total = (e > s) ? (e - s) : (1440 - s + e);

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

    public List<ScheduleAssignmentGroupDTO> getAllScheduleGroupsWithFilters(
            String status,
            String shiftName,
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate) {

        log.info("🔍 Buscando grupos con filtros - Status: {}, Shift: {}, Employee: {}, Fechas: {} - {}",
                status, shiftName, employeeId, startDate, endDate);

        // 1) Traer todos y sincronizar estado efectivo
        List<ScheduleAssignmentGroup> allGroups = groupRepository.findAll();
        for (ScheduleAssignmentGroup group : allGroups) {
            syncStatusWithDates(group);
        }

        // 2) Filtrar por status / empleado / fechas (versión "safe" ante nulls)
        List<ScheduleAssignmentGroup> filteredGroups = allGroups.stream()
                .filter(group -> filterByStatus(group, status))
                .filter(group -> filterByEmployee(group, employeeId))
                .filter(group -> filterByDateRangeSafe(group, startDate, endDate))  // <= usa el helper de abajo
                .collect(Collectors.toList());

        if (filteredGroups.isEmpty()) {
            log.info("✅ Encontrados 0 grupos que cumplen los criterios");
            return Collections.emptyList();
        }

        // 3) Convertir a DTOs, calcular horas y **SIEMPRE** setear employeeName
// 3) Convertir a DTOs, calcular horas y **SIEMPRE** setear employeeName
        List<ScheduleAssignmentGroupDTO> result = filteredGroups.stream()
                .map(group -> {
                    try {
                        List<EmployeeSchedule> schedules =
                                scheduleRepository.findAllByIdWithShift(group.getEmployeeScheduleIds());

                        // Filtro por turno si llega shiftName
                        if (shiftName != null && !shiftName.trim().isEmpty() && !"TODOS".equalsIgnoreCase(shiftName)) {
                            final String sn = shiftName.trim();
                            schedules = schedules.stream()
                                    .filter(s -> {
                                        String display = getShiftDisplayName(s.getShift());
                                        return display != null && sn.equalsIgnoreCase(display.trim());
                                    })
                                    .collect(Collectors.toList());
                            if (schedules.isEmpty()) return null;
                        }

                        HoursCalculation calc = calculateHours(schedules);
                        ScheduleAssignmentGroupDTO dto = convertToDTO(group, schedules, calc);



                        return dto;

                    } catch (Exception e) {
                        log.error("Error procesando grupo {}: {}", group.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());


        log.info("✅ Encontrados {} grupos que cumplen los criterios", result.size());
        return result;
    }

    // ====== FILTRO DE FECHAS "SAFE" (maneja periodos con end null) ======
    private boolean filterByDateRangeSafe(ScheduleAssignmentGroup group, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return true;

        LocalDate groupStart = toLocalDateOrMin(group.getPeriodStart()); // null -> MIN
        LocalDate groupEnd   = toLocalDateOrMax(group.getPeriodEnd());   // null -> MAX

        if (startDate != null && endDate == null) {
            return !groupEnd.isBefore(startDate);
        }
        if (startDate == null /* && endDate != null */) {
            return !groupStart.isAfter(endDate);
        }
        return !groupStart.isAfter(endDate) && !groupEnd.isBefore(startDate);
    }

    private LocalDate toLocalDateOrMin(Date date) {
        if (date == null) return LocalDate.MIN;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private LocalDate toLocalDateOrMax(Date date) {
        if (date == null) return LocalDate.MAX;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ====== RESOLVER NOMBRE SIN LLAMAR AL MICRO: desde los schedules / relación employee ======

    // Métodos auxiliares de filtrado
    private boolean filterByStatus(ScheduleAssignmentGroup group, String status) {
        if (status == null || status.trim().isEmpty() || "TODOS".equalsIgnoreCase(status)) {
            return true;
        }
        String effectiveStatus = getEffectiveStatus(group);
        return status.equalsIgnoreCase(effectiveStatus);
    }

    private boolean filterByEmployee(ScheduleAssignmentGroup group, Long employeeId) {
        if (employeeId == null) {
            return true;
        }
        return Objects.equals(group.getEmployeeId(), employeeId);
    }

    private boolean filterByDateRange(ScheduleAssignmentGroup group, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }

        LocalDate groupStart = convertToLocalDate(group.getPeriodStart());
        LocalDate groupEnd = convertToLocalDate(group.getPeriodEnd());

        // Si solo se especifica fecha inicio
        if (startDate != null && endDate == null) {
            return !groupEnd.isBefore(startDate);
        }

        // Si solo se especifica fecha fin
        if (startDate == null && endDate != null) {
            return !groupStart.isAfter(endDate);
        }

        // Si se especifican ambas fechas - verificar solapamiento
        return !groupStart.isAfter(endDate) && !groupEnd.isBefore(startDate);
    }

    public List<Map<String, String>> getAvailableStatuses() {
        // Obtener estados únicos de los grupos existentes
        List<String> uniqueStatuses = groupRepository.findAll()
                .stream()
                .map(group -> getEffectiveStatus(group))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<Map<String, String>> statusOptions = new ArrayList<>();
        statusOptions.add(Map.of("label", "Todos", "value", "TODOS"));

        // Mapear códigos a etiquetas
        Map<String, String> statusLabels = Map.of(
                "ACTIVE", "Activos",
                "INACTIVE", "Inactivos"
        );

        uniqueStatuses.forEach(status -> {
            statusOptions.add(Map.of(
                    "label", statusLabels.getOrDefault(status, status),
                    "value", status
            ));
        });

        return statusOptions;
    }

    private String getShiftDisplayName(Shifts shift) {
        if (shift == null) return null;
        try {
            String n = shift.getName();
            if (n != null && !n.isBlank()) return n;
        } catch (Exception ignored) {}

        try {
            String n = (String) shift.getClass().getMethod("getShiftName").invoke(shift);
            if (n != null && !n.isBlank()) return n;
        } catch (Exception ignored) {}

        try {
            String n = (String) shift.getClass().getMethod("getTitle").invoke(shift);
            if (n != null && !n.isBlank()) return n;
        } catch (Exception ignored) {}

        return null;
    }
}

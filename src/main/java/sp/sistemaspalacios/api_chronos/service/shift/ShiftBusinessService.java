package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.shift.ShiftBusinessDTOs;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftsRepository;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration.GeneralConfigurationService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShiftBusinessService {

    private final ValidationService validationService;
    private final ShiftsRepository shiftsRepository;
    private final ShiftDetailRepository shiftDetailRepository;
    private final GeneralConfigurationService generalConfigurationService;

    // Mapeo de días
    private final Map<String, Integer> DAY_MAPPING = Map.of(
            "monday", 1, "tuesday", 2, "wednesday", 3, "thursday", 4,
            "friday", 5, "saturday", 6, "sunday", 7
    );

    public ShiftBusinessService(ValidationService validationService,
                                ShiftsRepository shiftsRepository,
                                ShiftDetailRepository shiftDetailRepository,
                                GeneralConfigurationService generalConfigurationService) {
        this.validationService = validationService;
        this.shiftsRepository = shiftsRepository;
        this.shiftDetailRepository = shiftDetailRepository;
        this.generalConfigurationService = generalConfigurationService;
    }

    // ==========================================
    // VALIDACIÓN DE FORMULARIO COMPLETO
    // ==========================================

    public ShiftBusinessDTOs.ShiftFormValidationResponse validateShiftForm(
            ShiftBusinessDTOs.ValidateShiftFormRequest request) {

        ShiftBusinessDTOs.ShiftFormValidationResponse response = new ShiftBusinessDTOs.ShiftFormValidationResponse();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            // 1. Validaciones básicas del formulario
            validateBasicFormFields(request, errors);

            // 2. Validar que haya detalles
            if (request.getTempShiftDetails() == null || request.getTempShiftDetails().isEmpty()) {
                errors.add("Debe añadir al menos un día con horarios");
            }

            // 3. Calcular y validar horas totales
            if (request.getTempShiftDetails() != null && !request.getTempShiftDetails().isEmpty()) {
                double totalHours = calculateTotalHours(request.getTempShiftDetails());
                response.setCurrentTotalHours(totalHours);

                if (request.getWeeklyHoursLimit() != null) {
                    response.setWeeklyHoursLimit(request.getWeeklyHoursLimit());

                    // Validar límites semanales
                    String completionStatus = determineCompletionStatus(totalHours, request.getWeeklyHoursLimit());
                    response.setCompletionStatus(completionStatus);

                    if ("EXCEEDED".equals(completionStatus)) {
                        errors.add(String.format("El total de horas (%.1fh) excede el límite máximo de %.1fh",
                                totalHours, request.getWeeklyHoursLimit()));
                    } else if ("INCOMPLETE".equals(completionStatus)) {
                        double minRecommended = request.getWeeklyHoursLimit() * 0.45;
                        if (totalHours < minRecommended) {
                            warnings.add(String.format("El turno tiene pocas horas. Mínimo recomendado: %.1fh", minRecommended));
                        }
                    }
                }
            }

            response.setValid(errors.isEmpty());
            response.setErrors(errors);
            response.setWarnings(warnings);

        } catch (Exception e) {
            errors.add("Error interno: " + e.getMessage());
            response.setValid(false);
            response.setErrors(errors);
        }

        return response;
    }

    // ==========================================
    // AÑADIR DETALLE DE TURNO
    // ==========================================

    public ShiftBusinessDTOs.AddShiftDetailResponse addShiftDetail(
            ShiftBusinessDTOs.AddShiftDetailRequest request) {

        ShiftBusinessDTOs.AddShiftDetailResponse response = new ShiftBusinessDTOs.AddShiftDetailResponse();
        List<String> warnings = new ArrayList<>();

        try {
            // 1. Validar datos básicos
            if (request.getSelectedDays() == null || request.getTimeRanges() == null) {
                response.setSuccess(false);
                response.setMessage("Datos incompletos: días seleccionados o rangos de tiempo");
                return response;
            }

            // 2. Construir nuevos detalles
            List<ShiftBusinessDTOs.ShiftDetailData> newDetails = buildNewShiftDetails(request);

            if (newDetails.isEmpty()) {
                response.setSuccess(false);
                response.setMessage("Configure al menos un horario válido antes de añadir");
                return response;
            }

            // 3. Validar límites semanales
            // 3. Validar límites semanales (AHORA con horas NETAS)
            List<ShiftBusinessDTOs.ShiftDetailData> allDetails = new ArrayList<>(request.getExistingDetails());
            allDetails.addAll(newDetails);

            if (request.getWeeklyHoursLimit() != null) {
                double tolerance = 0.01;
                double totalNetHours = calculateNetHours(allDetails);

                if (totalNetHours > request.getWeeklyHoursLimit() + tolerance) {
                    response.setSuccess(false);
                    response.setMessage(String.format(
                            "El total neto (%.2fh) excedería el límite de %.2fh",
                            totalNetHours, request.getWeeklyHoursLimit()
                    ));
                    return response;
                }

                // devolver al front el total neto después de añadir
                response.setTotalHoursAfterAdd(totalNetHours);
            }


            // 4. Validar cada nuevo detalle usando ValidationService
            for (ShiftBusinessDTOs.ShiftDetailData detail : newDetails) {
                Map<String, Object> validationResult = validationService.validateTimeRange(
                        detail.getPeriod(),
                        detail.getStartTime(),
                        detail.getEndTime(),
                        request.getHoursPerDay()
                );

                if (!(Boolean) validationResult.get("isValid")) {
                    response.setSuccess(false);
                    response.setMessage("Error en validación: " + validationResult.get("error"));
                    return response;
                }

                // Añadir advertencias si existen
                if (validationResult.containsKey("warning")) {
                    warnings.add((String) validationResult.get("warning"));
                }
            }

            // 5. Éxito
            response.setSuccess(true);
            response.setMessage("Detalles añadidos exitosamente");
            response.setNewShiftDetails(allDetails);
            response.setWarnings(warnings);

        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage("Error interno: " + e.getMessage());
        }

        return response;
    }

    // ==========================================
    // GUARDAR TURNO COMPLETO
    // ==========================================
    /** Horas NETAS = horas de trabajo - (minutos de break / 60) */
    private double calculateNetHours(List<ShiftBusinessDTOs.ShiftDetailData> details) {
        double workHours = calculateTotalHours(details);
        int totalBreakMinutes = calculateTotalBreakMinutes(details);
        double breakHours = totalBreakMinutes / 60.0;
        double net = workHours - breakHours;
        return net < 0 ? 0.0 : net;
    }

    public Map<String, Object> saveCompleteShift(ShiftBusinessDTOs.SaveCompleteShiftRequest request) {
        Map<String, Object> response = new HashMap<>();
        double tolerance = 0.01;

        try {
            // 0) Validaciones básicas de formulario
            List<String> errors = new ArrayList<>();
            if (request.getShiftname() == null || request.getShiftname().trim().isEmpty()) {
                errors.add("El nombre del turno es obligatorio");
            }
            if (request.getTypeShift() == null || request.getTypeShift().trim().isEmpty()) {
                errors.add("La descripción del turno es obligatoria");
            }
            if (request.getDependencyId() == null || request.getDependencyId() <= 0) {
                errors.add("Debe seleccionar una dependencia válida");
            }
            if (request.getShiftDetails() == null || request.getShiftDetails().isEmpty()) {
                errors.add("No hay detalles de turno para guardar");
            }
            if (!errors.isEmpty()) {
                response.put("success", false);
                response.put("errorType", "VALIDATION_ERROR");
                response.put("error", String.join("; ", errors));
                return response;
            }

            // 1) Calcular horas WORK / BREAK / NETAS antes de persistir
            double workHours = calculateTotalHours(request.getShiftDetails());
            int totalBreakMinutes = calculateTotalBreakMinutes(request.getShiftDetails());
            double breakHours = totalBreakMinutes / 60.0;
            double netHours = Math.max(0.0, workHours - breakHours);

            // 2) Obtener límite semanal desde configuración (preferido) o desde el request
            Double configuredLimit = null;
            try {
                String weeklyCfg = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();
                configuredLimit = parseConfigurationHours(weeklyCfg);
            } catch (Exception ignore) {}

            double weeklyLimit = (configuredLimit != null && configuredLimit > 0)
                    ? configuredLimit
                    : parseConfigurationHours(request.getWeeklyHours());

            // 3) BLOQUEAR si excede el límite
            if (weeklyLimit > 0 && netHours > weeklyLimit + tolerance) {
                response.put("success", false);
                response.put("errorType", "LIMIT_EXCEEDED");
                response.put("error", String.format(
                        "El total neto de horas (%.2fh) excede el límite semanal de %.2fh",
                        netHours, weeklyLimit
                ));
                response.put("scheduledHours", workHours);
                response.put("breakHours", breakHours);
                response.put("netHours", netHours);
                response.put("requiredHours", weeklyLimit);
                response.put("missingHours", 0);
                return response;
            }

            // 4) Persistir turno principal
            Shifts shift = new Shifts();
            shift.setName(request.getShiftname());
            shift.setDescription(request.getTypeShift());
            shift.setDependencyId(request.getDependencyId());
            shift.setTimeBreak((long) totalBreakMinutes);
            shift.setCreatedAt(new Date());

            Shifts savedShift = shiftsRepository.save(shift);

            // 5) Persistir detalles
            List<ShiftDetail> shiftDetails = new ArrayList<>();
            for (ShiftBusinessDTOs.ShiftDetailData detailData : request.getShiftDetails()) {
                ShiftDetail detail = new ShiftDetail();
                detail.setShift(savedShift);
                detail.setDayOfWeek(detailData.getDayOfWeek());
                detail.setStartTime(convertTo24Hour(detailData.getStartTime()));
                detail.setEndTime(convertTo24Hour(detailData.getEndTime()));

                if (detailData.getBreakStartTime() != null && detailData.getBreakEndTime() != null) {
                    detail.setBreakStartTime(convertTo24Hour(detailData.getBreakStartTime()));
                    detail.setBreakEndTime(convertTo24Hour(detailData.getBreakEndTime()));
                    detail.setBreakMinutes(detailData.getBreakDuration());
                }

                setConfigurationValues(detail);
                detail.setCreatedAt(new Date());
                shiftDetails.add(detail);
            }
            shiftDetailRepository.saveAll(shiftDetails);

            // 6) Respuesta
            response.put("success", true);
            response.put("shiftId", savedShift.getId());
            response.put("message", "Turno creado exitosamente");
            response.put("scheduledHours", workHours);         // horas brutas
            response.put("breakHours", breakHours);            // horas de break
            response.put("netHours", netHours);                // horas netas
            response.put("requiredHours", weeklyLimit);        // límite semanal
            response.put("missingHours", weeklyLimit > 0 ? Math.max(0, weeklyLimit - netHours) : 0);

        } catch (Exception e) {
            response.put("success", false);
            response.put("errorType", "INTERNAL_ERROR");
            response.put("error", "Error creando turno: " + e.getMessage());
        }

        return response;
    }

    // ==========================================
    // MÉTODOS AUXILIARES PRIVADOS
    // ==========================================
    private double parseConfigurationHours(Object value) {
        if (value == null) return 0.0;

        // Si viene como número ya parseado
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        // Parsear como String
        String str = value.toString().trim();
        if (str.isEmpty()) return 0.0;

        try {
            // Formato "HH:MM"
            if (str.matches("^\\d{1,3}:\\d{2}$")) {
                String[] parts = str.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours + (minutes / 60.0);
            }

            // Formato decimal con punto o coma: "44.5" / "44,5"
            str = str.replace(',', '.');
            return Double.parseDouble(str);
        } catch (Exception ignored) {
            return 0.0;
        }
    }
    private void validateBasicFormFields(ShiftBusinessDTOs.ValidateShiftFormRequest request, List<String> errors) {
        if (request.getShiftname() == null || request.getShiftname().trim().isEmpty()) {
            errors.add("El nombre del turno es obligatorio");
        }

        if (request.getTypeShift() == null || request.getTypeShift().trim().isEmpty()) {
            errors.add("La descripción del turno es obligatoria");
        }

        if (request.getDependencyId() == null || request.getDependencyId() <= 0) {
            errors.add("Debe seleccionar una dependencia válida");
        }
    }

    private String determineCompletionStatus(double totalHours, double weeklyLimit) {
        double tolerance = 0.1;
        double minRecommended = weeklyLimit * 0.45;

        if (totalHours == 0) return "EMPTY";
        if (totalHours > weeklyLimit + tolerance) return "EXCEEDED";
        if (Math.abs(totalHours - weeklyLimit) <= tolerance) return "COMPLETE";
        if (totalHours < minRecommended) return "INCOMPLETE";

        return "INCOMPLETE";
    }

    private List<ShiftBusinessDTOs.ShiftDetailData> buildNewShiftDetails(
            ShiftBusinessDTOs.AddShiftDetailRequest request) {

        List<ShiftBusinessDTOs.ShiftDetailData> newDetails = new ArrayList<>();
        List<String> periods = Arrays.asList("Mañana", "Tarde", "Noche");

        // Iterar días seleccionados
        request.getSelectedDays().entrySet().stream()
                .filter(entry -> entry.getValue()) // Solo días seleccionados (true)
                .forEach(dayEntry -> {
                    String dayName = dayEntry.getKey();
                    Integer dayNumber = DAY_MAPPING.get(dayName);

                    if (dayNumber != null && !isDayAlreadyAdded(dayNumber, request.getExistingDetails())) {
                        // Para cada período configurado
                        periods.forEach(period -> {
                            ShiftBusinessDTOs.TimeRangeData timeRange = request.getTimeRanges().get(period);

                            if (timeRange != null && isValidTimeRange(timeRange)) {
                                ShiftBusinessDTOs.ShiftDetailData detail = new ShiftBusinessDTOs.ShiftDetailData();
                                detail.setDayOfWeek(dayNumber);
                                detail.setStartTime(timeRange.getStart());
                                detail.setEndTime(timeRange.getEnd());
                                detail.setPeriod(period);

                                // Añadir break si existe
                                if (request.getBreakRanges() != null) {
                                    ShiftBusinessDTOs.TimeRangeData breakRange = request.getBreakRanges().get(period);
                                    if (breakRange != null && isValidTimeRange(breakRange)) {
                                        detail.setBreakStartTime(breakRange.getStart());
                                        detail.setBreakEndTime(breakRange.getEnd());
                                        detail.setBreakDuration(calculateBreakDuration(breakRange.getStart(), breakRange.getEnd()));
                                    }
                                }

                                newDetails.add(detail);
                            }
                        });
                    }
                });

        return newDetails;
    }

    private boolean isDayAlreadyAdded(Integer dayNumber, List<ShiftBusinessDTOs.ShiftDetailData> existingDetails) {
        return existingDetails.stream()
                .anyMatch(detail -> detail.getDayOfWeek().equals(dayNumber));
    }

    private boolean isValidTimeRange(ShiftBusinessDTOs.TimeRangeData range) {
        return range.getStart() != null && range.getEnd() != null &&
                !range.getStart().trim().isEmpty() && !range.getEnd().trim().isEmpty();
    }

    private double calculateTotalHours(List<ShiftBusinessDTOs.ShiftDetailData> details) {
        return details.stream()
                .mapToDouble(this::calculateHoursFromDetail)
                .sum();
    }

    private double calculateHoursFromDetail(ShiftBusinessDTOs.ShiftDetailData detail) {
        try {
            String startTime24h = convertTo24Hour(detail.getStartTime());
            String endTime24h = convertTo24Hour(detail.getEndTime());

            LocalTime start = LocalTime.parse(startTime24h);
            LocalTime end = LocalTime.parse(endTime24h);

            long duration;
            if (end.isBefore(start)) {
                // Cruza medianoche
                duration = ChronoUnit.MINUTES.between(start, LocalTime.MAX) +
                        ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
            } else {
                duration = ChronoUnit.MINUTES.between(start, end);
            }

            return duration / 60.0;

        } catch (Exception e) {
            return 0.0;
        }
    }

    private int calculateTotalBreakMinutes(List<ShiftBusinessDTOs.ShiftDetailData> details) {
        return details.stream()
                .mapToInt(detail -> detail.getBreakDuration() != null ? detail.getBreakDuration() : 0)
                .sum();
    }

    private int calculateBreakDuration(String startTime, String endTime) {
        try {
            LocalTime start = LocalTime.parse(convertTo24Hour(startTime));
            LocalTime end = LocalTime.parse(convertTo24Hour(endTime));
            return (int) ChronoUnit.MINUTES.between(start, end);
        } catch (Exception e) {
            return 0;
        }
    }

    private String convertTo24Hour(String time12h) {
        if (time12h == null || time12h.trim().isEmpty()) {
            return "";
        }

        try {
            // Si ya está en formato 24h, devolverlo tal como está
            if (time12h.matches("^\\d{1,2}:\\d{2}$")) {
                return time12h;
            }

            // Convertir de 12h AM/PM a 24h
            String cleaned = time12h.trim().toUpperCase().replaceAll("\\s+", " ");
            String[] parts = cleaned.split("\\s+");
            String timePart = parts[0];
            String amPm = parts[1];

            String[] timeParts = timePart.split(":");
            int hours = Integer.parseInt(timeParts[0]);
            int minutes = Integer.parseInt(timeParts[1]);

            if ("AM".equals(amPm) && hours == 12) {
                hours = 0;
            } else if ("PM".equals(amPm) && hours != 12) {
                hours += 12;
            }

            return String.format("%02d:%02d", hours, minutes);

        } catch (Exception e) {
            return time12h; // Devolver original si hay error
        }
    }

    private void setConfigurationValues(ShiftDetail shiftDetail) {
        try {
            String breakValue = generalConfigurationService.getByType("BREAK").getValue();
            String weeklyHours = generalConfigurationService.getByType("WEEKLY_HOURS").getValue();
            String hoursPerDay = generalConfigurationService.getByType("DAILY_HOURS").getValue();
            String nightStart = generalConfigurationService.getByType("NIGHT_START").getValue();

            shiftDetail.setBreakMinutes(Integer.parseInt(breakValue));
            shiftDetail.setWeeklyHours(weeklyHours);
            shiftDetail.setNightHoursStart(nightStart);
            shiftDetail.setHoursPerDay(hoursPerDay);

        } catch (Exception e) {
            // Usar valores por defecto si hay error
            shiftDetail.setBreakMinutes(0);
            shiftDetail.setWeeklyHours("40:00");
            shiftDetail.setNightHoursStart("19:00");
            shiftDetail.setHoursPerDay("8:00");
        }
    }

    // ==========================================
    // NUEVOS MÉTODOS PARA LÓGICA COMPLETA EN BACKEND
    // ==========================================

    /**
     * Obtiene estado por defecto del formulario con configuración del backend
     */
    public Map<String, Object> getDefaultFormState() {
        Map<String, Object> defaultState = new HashMap<>();

        try {
            // Períodos configurables
            List<String> periods = Arrays.asList("Mañana", "Tarde", "Noche");

            // Días de la semana
            Map<String, Boolean> selectedDays = new LinkedHashMap<>();
            DAY_MAPPING.keySet().forEach(day -> selectedDays.put(day, false));
            selectedDays.put("allWeek", false);

            // Rangos de tiempo vacíos
            Map<String, Map<String, String>> timeRanges = new HashMap<>();
            periods.forEach(period -> {
                Map<String, String> range = new HashMap<>();
                range.put("start", "");
                range.put("end", "");
                timeRanges.put(period, range);
            });

            // Estado de validación inicial
            Map<String, Object> validation = new HashMap<>();
            validation.put("isValid", true);
            validation.put("errors", new ArrayList<>());
            validation.put("periodValidations", new HashMap<>());
            validation.put("globalErrors", new ArrayList<>());

            // Datos del formulario
            defaultState.put("shiftname", "");
            defaultState.put("typeShift", "");
            defaultState.put("dependence", "");
            defaultState.put("tempShiftDetails", new ArrayList<>());
            defaultState.put("showTimePanel", false);
            defaultState.put("selectedDays", selectedDays);
            defaultState.put("timeRanges", timeRanges);
            defaultState.put("validation", validation);
            defaultState.put("success", true);

        } catch (Exception e) {
            defaultState.put("success", false);
            defaultState.put("error", "Error generando estado por defecto: " + e.getMessage());
        }

        return defaultState;
    }

    /**
     * Calcula el estado de completitud de un turno
     */
    public Map<String, Object> calculateCompletionStatus(List<Map<String, Object>> shiftDetailsRaw, Double weeklyHoursLimit) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Convertir datos raw a ShiftDetailData
            List<ShiftBusinessDTOs.ShiftDetailData> shiftDetails = shiftDetailsRaw.stream()
                    .map(this::convertRawToShiftDetailData)
                    .collect(Collectors.toList());

            double totalHours = calculateTotalHours(shiftDetails);

            String status;
            String message;
            String color;
            String icon;
            boolean canSave;

            if (weeklyHoursLimit == null || weeklyHoursLimit <= 0) {
                status = "NO_CONFIG";
                message = "Configuración no disponible";
                color = "#6b7280";
                icon = "pi-exclamation-triangle";
                canSave = false;
            } else {
                double difference = weeklyHoursLimit - totalHours;
                double minRecommendedHours = weeklyHoursLimit * 0.45;

                if (totalHours == 0) {
                    status = "EMPTY";
                    message = "Sin configurar";
                    color = "#6b7280";
                    icon = "pi-clock";
                    canSave = false;
                } else if (totalHours < minRecommendedHours) {
                    status = "INCOMPLETE";
                    message = String.format("Faltan %.1f horas", difference);
                    color = "#f59e0b";
                    icon = "pi-exclamation-triangle";
                    canSave = false;
                } else if (Math.abs(difference) <= 0.1) {
                    status = "COMPLETE";
                    message = "Turno completo - Listo para guardar";
                    color = "#10b981";
                    icon = "pi-check-circle";
                    canSave = true;
                } else if (totalHours > weeklyHoursLimit) {
                    status = "EXCEEDED";
                    message = String.format("Excede por %.1f horas", Math.abs(difference));
                    color = "#dc2626";
                    icon = "pi-times-circle";
                    canSave = false;
                } else {
                    status = "INCOMPLETE";
                    message = String.format("Faltan %.1f horas", difference);
                    color = "#f59e0b";
                    icon = "pi-exclamation-triangle";
                    canSave = false;
                }
            }

            double completionPercentage = (weeklyHoursLimit != null && weeklyHoursLimit > 0)
                    ? Math.min((totalHours / weeklyHoursLimit) * 100, 100) : 0;

            response.put("success", true);
            response.put("status", status);
            response.put("message", message);
            response.put("color", color);
            response.put("icon", icon);
            response.put("canSave", canSave);
            response.put("currentHours", totalHours);
            response.put("weeklyHoursLimit", weeklyHoursLimit);
            response.put("completionPercentage", completionPercentage);
            response.put("difference", weeklyHoursLimit != null ? weeklyHoursLimit - totalHours : 0);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Error calculando estado de completitud: " + e.getMessage());
        }

        return response;
    }

    /**
     * Formatea tabla de detalles de turno
     */
    public Map<String, Object> formatShiftTable(List<ShiftBusinessDTOs.ShiftDetailData> shiftDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (shiftDetails.isEmpty()) {
                response.put("success", true);
                response.put("tableHtml", "<p style='text-align: center; color: #6b7280;'>No hay configuraciones añadidas</p>");
                response.put("groupedDetails", new HashMap<>());
                return response;
            }

            // Agrupar por día
            Map<Integer, List<ShiftBusinessDTOs.ShiftDetailData>> groupedByDay =
                    shiftDetails.stream().collect(Collectors.groupingBy(ShiftBusinessDTOs.ShiftDetailData::getDayOfWeek));

            // Generar HTML de tabla
            StringBuilder tableHtml = new StringBuilder();
            tableHtml.append("<table style='width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px;'>")
                    .append("<thead><tr style='background: #f8f9fa;'>")
                    .append("<th style='border: 1px solid #dee2e6; padding: 8px; text-align: left;'>Día</th>")
                    .append("<th style='border: 1px solid #dee2e6; padding: 8px; text-align: left;'>Período</th>")
                    .append("<th style='border: 1px solid #dee2e6; padding: 8px; text-align: left;'>Horario</th>")
                    .append("<th style='border: 1px solid #dee2e6; padding: 8px; text-align: left;'>Break</th>")
                    .append("<th style='border: 1px solid #dee2e6; padding: 8px; text-align: left;'>Horas Turno</th>")
                    .append("</tr></thead><tbody>");

            String[] dayNames = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

            groupedByDay.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Integer dayNumber = entry.getKey();
                        List<ShiftBusinessDTOs.ShiftDetailData> dayDetails = entry.getValue();
                        String dayName = dayNumber < dayNames.length ? dayNames[dayNumber] : "Día " + dayNumber;

                        for (int i = 0; i < dayDetails.size(); i++) {
                            ShiftBusinessDTOs.ShiftDetailData detail = dayDetails.get(i);
                            double workHours = calculateHoursFromDetail(detail);
                            String breakInfo = formatBreakInfo(detail);

                            tableHtml.append("<tr>")
                                    .append("<td style='border: 1px solid #dee2e6; padding: 8px;'>")
                                    .append(i == 0 ? dayName : "").append("</td>")
                                    .append("<td style='border: 1px solid #dee2e6; padding: 8px;'>")
                                    .append(detail.getPeriod() != null ? detail.getPeriod() : "").append("</td>")
                                    .append("<td style='border: 1px solid #dee2e6; padding: 8px;'>")
                                    .append(detail.getStartTime()).append(" - ").append(detail.getEndTime()).append("</td>")
                                    .append("<td style='border: 1px solid #dee2e6; padding: 8px; font-family: monospace;'>")
                                    .append(breakInfo).append("</td>")
                                    .append("<td style='border: 1px solid #dee2e6; padding: 8px; font-weight: bold; color: #28a745;'>")
                                    .append(String.format("%.1fh", workHours)).append("</td>")
                                    .append("</tr>");
                        }
                    });

            tableHtml.append("</tbody></table>");

            response.put("success", true);
            response.put("tableHtml", tableHtml.toString());
            response.put("groupedDetails", groupedByDay);
            response.put("totalHours", calculateTotalHours(shiftDetails));
            response.put("totalBreakMinutes", calculateTotalBreakMinutes(shiftDetails));

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Error formateando tabla: " + e.getMessage());
            response.put("tableHtml", "<p style='color: #dc2626;'>Error generando tabla</p>");
        }

        return response;
    }

    /**
     * Valida y mapea dependencia
     */
    public Map<String, Object> validateAndMapDependency(Object dependencyData) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (dependencyData == null) {
                response.put("isValid", false);
                response.put("error", "Datos de dependencia son nulos");
                return response;
            }

            // Caso String (p.ej., viene "0", "", "Seleccione...")
            if (dependencyData instanceof String) {
                String raw = ((String) dependencyData).trim();
                if (raw.isEmpty() || raw.equals("0")) {
                    response.put("isValid", false);
                    response.put("error", "Debe seleccionar una dependencia válida");
                    return response;
                }
                // Si quieres intentar parsear un ID enviado como string:
                try {
                    Long parsed = Long.valueOf(raw);
                    if (parsed > 0) {
                        response.put("isValid", true);
                        response.put("dependencyId", parsed);
                        return response;
                    }
                } catch (NumberFormatException ignored) {}
                response.put("isValid", false);
                response.put("error", "Formato de dependencia inválido");
                return response;
            }

            // Caso Map
            if (dependencyData instanceof Map) {
                Map<?, ?> depMap = (Map<?, ?>) dependencyData;
                if (depMap.isEmpty()) {
                    response.put("isValid", false);
                    response.put("error", "Debe seleccionar una dependencia válida");
                    return response;
                }

                Object idObj = depMap.get("id");
                Object codeObj = depMap.get("code");

                Long dependencyId = null;
                if (idObj != null) {
                    dependencyId = tryParseLong(idObj.toString());
                } else if (codeObj != null) {
                    dependencyId = tryParseLong(codeObj.toString());
                }

                if (dependencyId == null || dependencyId <= 0) {
                    response.put("isValid", false);
                    response.put("error", "ID de dependencia inválido");
                    return response;
                }

                response.put("isValid", true);
                response.put("dependencyId", dependencyId);
                response.put("dependencyName", depMap.get("name"));
                return response;
            }

            // Otros tipos no soportados
            response.put("isValid", false);
            response.put("error", "Formato de dependencia no soportado");

        } catch (Exception e) {
            response.put("isValid", false);
            response.put("error", "Error validando dependencia: " + e.getMessage());
        }

        return response;
    }

    private Long tryParseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }


    /**
     * Convierte múltiples formatos de tiempo
     */
    public Map<String, Object> convertTimeFormats(List<String> times, String targetFormat) {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, String> conversions = new HashMap<>();
            List<String> errors = new ArrayList<>();

            for (String time : times) {
                try {
                    if (time == null || time.trim().isEmpty()) {
                        conversions.put(time, "");
                        continue;
                    }

                    String convertedTime;
                    if ("24h".equals(targetFormat)) {
                        convertedTime = convertTo24Hour(time);
                    } else {
                        convertedTime = convertTo12Hour(time);
                    }

                    conversions.put(time, convertedTime);

                } catch (Exception e) {
                    errors.add("Error convirtiendo '" + time + "': " + e.getMessage());
                    conversions.put(time, time); // Mantener original
                }
            }

            response.put("success", errors.isEmpty());
            response.put("conversions", conversions);
            response.put("errors", errors);
            response.put("targetFormat", targetFormat);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Error en conversión de formatos: " + e.getMessage());
        }

        return response;
    }

    /**
     * Verifica superposiciones de tiempo
     */
    public Map<String, Object> checkTimeOverlaps(String newStart, String newEnd,
                                                 List<Integer> selectedDays,
                                                 List<Map<String, Object>> existingShifts) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<String> conflicts = new ArrayList<>();
            String[] dayNames = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

            for (Integer day : selectedDays) {
                String dayName = day < dayNames.length ? dayNames[day] : "Día " + day;

                for (Map<String, Object> shift : existingShifts) {
                    Object dayOfWeekObj = shift.get("dayOfWeek");
                    if (dayOfWeekObj != null && Integer.valueOf(dayOfWeekObj.toString()).equals(day)) {
                        String existingStart = (String) shift.get("startTime");
                        String existingEnd = (String) shift.get("endTime");

                        if (hasTimeOverlap(newStart, newEnd, existingStart, existingEnd)) {
                            conflicts.add(String.format("Conflicto en %s: %s-%s se superpone con %s-%s",
                                    dayName, newStart, newEnd, existingStart, existingEnd));
                        }
                    }
                }
            }

            response.put("success", true);
            response.put("hasOverlaps", !conflicts.isEmpty());
            response.put("conflicts", conflicts);
            response.put("conflictCount", conflicts.size());

        } catch (Exception e) {
            response.put("success", false);
            response.put("hasOverlaps", false);
            response.put("conflicts", new ArrayList<>());
            response.put("error", "Error verificando superposiciones: " + e.getMessage());
        }

        return response;
    }

    // ==========================================
    // MÉTODOS AUXILIARES PRIVADOS ADICIONALES
    // ==========================================

    private ShiftBusinessDTOs.ShiftDetailData convertRawToShiftDetailData(Map<String, Object> raw) {
        ShiftBusinessDTOs.ShiftDetailData detail = new ShiftBusinessDTOs.ShiftDetailData();

        if (raw.get("dayOfWeek") != null) {
            detail.setDayOfWeek(Integer.valueOf(raw.get("dayOfWeek").toString()));
        }
        detail.setStartTime((String) raw.get("startTime"));
        detail.setEndTime((String) raw.get("endTime"));
        detail.setPeriod((String) raw.get("period"));
        detail.setBreakStartTime((String) raw.get("breakStartTime"));
        detail.setBreakEndTime((String) raw.get("breakEndTime"));

        if (raw.get("breakDuration") != null) {
            detail.setBreakDuration(Integer.valueOf(raw.get("breakDuration").toString()));
        }

        return detail;
    }

    private String formatBreakInfo(ShiftBusinessDTOs.ShiftDetailData detail) {
        if (detail.getBreakStartTime() == null || detail.getBreakEndTime() == null) {
            return "Sin break";
        }

        int breakDuration = detail.getBreakDuration() != null ? detail.getBreakDuration() : 0;
        return String.format("%s - %s (%dm)", detail.getBreakStartTime(), detail.getBreakEndTime(), breakDuration);
    }

    private String convertTo12Hour(String time24h) {
        try {
            if (time24h == null || time24h.trim().isEmpty()) {
                return "";
            }

            // Si ya está en formato 12h, devolverlo
            if (time24h.toUpperCase().contains("AM") || time24h.toUpperCase().contains("PM")) {
                return time24h;
            }

            LocalTime time = LocalTime.parse(time24h);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
            return time.format(formatter).toUpperCase();

        } catch (Exception e) {
            return time24h; // Devolver original si hay error
        }
    }

    private boolean hasTimeOverlap(String start1, String end1, String start2, String end2) {
        try {
            int start1Min = timeToMinutes(start1);
            int end1Min = timeToMinutes(end1);
            int start2Min = timeToMinutes(start2);
            int end2Min = timeToMinutes(end2);

            if (start1Min == -1 || end1Min == -1 || start2Min == -1 || end2Min == -1) {
                return false;
            }

            // Ajustar para cruces de medianoche
            if (end1Min < start1Min) end1Min += 24 * 60;
            if (end2Min < start2Min) end2Min += 24 * 60;

            return !(end1Min <= start2Min || start1Min >= end2Min);

        } catch (Exception e) {
            return false;
        }
    }

    private int timeToMinutes(String timeStr) {
        try {
            if (timeStr == null || timeStr.trim().isEmpty()) {
                return -1;
            }

            String time = timeStr.trim().toUpperCase();

            // Formato 24h
            if (time.matches("^\\d{1,2}:\\d{2}$")) {
                String[] parts = time.split(":");
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }

            // Formato 12h AM/PM
            if (time.matches("^\\d{1,2}:\\d{2}\\s*(AM|PM)$")) {
                String[] parts = time.replace(" AM", "").replace(" PM", "").split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);

                if (time.contains("AM") && hours == 12) {
                    hours = 0;
                } else if (time.contains("PM") && hours != 12) {
                    hours += 12;
                }

                return hours * 60 + minutes;
            }

            return -1;

        } catch (Exception e) {
            return -1;
        }
    }
}
package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.dto.ShiftBusinessDTOs;
import sp.sistemaspalacios.api_chronos.service.shift.ShiftBusinessService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/shift-business")
public class ShiftBusinessController {

    private final ShiftBusinessService shiftBusinessService;

    public ShiftBusinessController(ShiftBusinessService shiftBusinessService) {
        this.shiftBusinessService = shiftBusinessService;
    }

    // ==========================================
    // VALIDACIÓN DE FORMULARIO COMPLETO
    // ==========================================
    /**
     * POST /api/shift-business/validate-form
     */
    @PostMapping(value = "/validate-form")
    public ResponseEntity<ShiftBusinessDTOs.ShiftFormValidationResponse> validateShiftForm(
            @RequestBody ShiftBusinessDTOs.ValidateShiftFormRequest request) {
        try {
            ShiftBusinessDTOs.ShiftFormValidationResponse response =
                    shiftBusinessService.validateShiftForm(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ShiftBusinessDTOs.ShiftFormValidationResponse errorResponse =
                    new ShiftBusinessDTOs.ShiftFormValidationResponse();
            errorResponse.setValid(false);
            errorResponse.setErrors(java.util.Arrays.asList("Error interno: " + e.getMessage()));
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // AÑADIR DETALLE DE TURNO (PRE-GUARDADO)
    // ==========================================
    /**
     * POST /api/shift-business/add-detail
     * No persiste; devuelve la lista actualizada para que el front la muestre.
     */
    @PostMapping(value = "/add-detail", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShiftBusinessDTOs.AddShiftDetailResponse> addShiftDetail(
            @RequestBody ShiftBusinessDTOs.AddShiftDetailRequest request) {


        try {
            ShiftBusinessDTOs.AddShiftDetailResponse response = shiftBusinessService.addShiftDetail(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ShiftBusinessDTOs.AddShiftDetailResponse errorResponse = new ShiftBusinessDTOs.AddShiftDetailResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Error interno: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }


    // ==========================================
    // GUARDAR TURNO COMPLETO (PERSISTE)
    // ==========================================
    /**
     * POST /api/shift-business/save-complete
     */
    @PostMapping(value = "/save-complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> saveComplete(@RequestBody ShiftBusinessDTOs.SaveCompleteShiftRequest request) {
        Map<String, Object> res = shiftBusinessService.saveCompleteShift(request);
        if (Boolean.TRUE.equals(res.get("success"))) {
            return ResponseEntity.ok(res);
        } else {
            // devolver 400 con el payload de error (incluye errorType)
            return ResponseEntity.badRequest().body(res);
        }
    }


    @PostMapping("/format-table")
    public ResponseEntity<Map<String, Object>> formatShiftTable(
            @RequestBody Map<String, Object> request) {

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shiftDetailsRaw =
                    (List<Map<String, Object>>) request.get("shiftDetails");

            if (shiftDetailsRaw == null || shiftDetailsRaw.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("tableHtml", "<p style='text-align: center; color: #6b7280;'>No hay configuraciones añadidas</p>");
                return ResponseEntity.ok(response);
            }

            // Convertir datos raw a ShiftDetailData
            List<ShiftBusinessDTOs.ShiftDetailData> shiftDetails = shiftDetailsRaw.stream()
                    .map(raw -> {
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
                    })
                    .collect(Collectors.toList());

            // Usar el método existente formatShiftTable del service
            Map<String, Object> result = shiftBusinessService.formatShiftTable(shiftDetails);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error formateando tabla: " + e.getMessage());
            errorResponse.put("tableHtml", "<p style='color: #dc2626;'>Error generando tabla</p>");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    // Método auxiliar para convertir datos raw (agregar como método privado)
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
    // ==========================================
    // ESTADO POR DEFECTO DEL FORMULARIO (BACKEND)
    // ==========================================
    /**
     * GET /api/shift-business/reset-form-state
     */
    @GetMapping(value = "/reset-form-state", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resetFormState() {
        try {
            Map<String, Object> defaultState = shiftBusinessService.getDefaultFormState();
            return ResponseEntity.ok(defaultState);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error obteniendo estado por defecto: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // ESTADO DE COMPLETITUD
    // ==========================================
    /**
     * POST /api/shift-business/calculate-completion-status
     */
    @PostMapping(value = "/calculate-completion-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> calculateCompletionStatus(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shiftDetails =
                    (List<Map<String, Object>>) request.get("shiftDetails");

            Double weeklyHoursLimit = null;
            Object limitObj = request.get("weeklyHoursLimit");
            if (limitObj instanceof Number) {
                weeklyHoursLimit = ((Number) limitObj).doubleValue();
            }

            Map<String, Object> response = shiftBusinessService.calculateCompletionStatus(
                    shiftDetails, weeklyHoursLimit);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error calculando estado: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // TABLA FORMATEADA
    // ==========================================
    /**
     * POST /api/shift-business/format-shift-table
     */
    @PostMapping(value = "/format-shift-table", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> formatShiftTable(
            @RequestBody List<ShiftBusinessDTOs.ShiftDetailData> shiftDetails) {
        try {
            Map<String, Object> response = shiftBusinessService.formatShiftTable(shiftDetails);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error formateando tabla: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // VALIDAR/MAPEAR DEPENDENCIA
    // ==========================================
    /**
     * POST /api/shift-business/validate-dependency
     */
    @PostMapping(value = "/validate-dependency", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> validateDependency(
            @RequestBody Map<String, Object> dependencyData) {
        try {
            Map<String, Object> response = shiftBusinessService.validateAndMapDependency(dependencyData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("isValid", false);
            errorResponse.put("error", "Error validando dependencia: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // CONVERTIR FORMATOS DE HORA
    // ==========================================
    /**
     * POST /api/shift-business/convert-time-formats
     */
    @PostMapping(value = "/convert-time-formats", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> convertTimeFormats(
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> times = (List<String>) request.get("times");
            String targetFormat = (String) request.get("targetFormat"); // "12h" or "24h"
            Map<String, Object> response = shiftBusinessService.convertTimeFormats(times, targetFormat);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error convirtiendo formatos: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // SUPERPOSICIONES DE TIEMPO
    // ==========================================
    /**
     * POST /api/shift-business/check-time-overlaps
     */
    @PostMapping(value = "/check-time-overlaps", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> checkTimeOverlaps(
            @RequestBody Map<String, Object> request) {
        try {
            String newStart = (String) request.get("newStart");
            String newEnd   = (String) request.get("newEnd");

            @SuppressWarnings("unchecked")
            List<Integer> selectedDays = (List<Integer>) request.get("selectedDays");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingShifts =
                    (List<Map<String, Object>>) request.get("existingShifts");

            Map<String, Object> response = shiftBusinessService.checkTimeOverlaps(
                    newStart, newEnd, selectedDays, existingShifts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("hasOverlaps", false);
            errorResponse.put("conflicts", java.util.Collections.emptyList());
            errorResponse.put("error", "Error verificando superposiciones: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ==========================================
    // DÍAS DISPONIBLES
    // ==========================================
    /**
     * POST /api/shift-business/validate-days-available
     */
    @PostMapping(value = "/validate-days-available", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> validateDaysAvailable(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> selectedDays = (Map<String, Boolean>) request.get("selectedDays");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingDetails =
                    (List<Map<String, Object>>) request.get("existingDetails");

            if (selectedDays == null || existingDetails == null) {
                response.put("isValid", false);
                response.put("error", "Parámetros incompletos");
                return ResponseEntity.badRequest().body(response);
            }

            boolean hasAvailableDays = selectedDays.entrySet().stream()
                    .filter(Map.Entry::getValue) // Solo días seleccionados
                    .anyMatch(entry -> {
                        Integer dayNumber = getDayNumber(entry.getKey());
                        if (dayNumber == null) return false;
                        return existingDetails.stream().noneMatch(detail -> {
                            Object d = detail.get("dayOfWeek");
                            if (d instanceof Number) {
                                return dayNumber.equals(((Number) d).intValue());
                            }
                            try {
                                return dayNumber.equals(Integer.valueOf(String.valueOf(d)));
                            } catch (Exception ignored) {
                                return false;
                            }
                        });
                    });

            response.put("isValid", true);
            response.put("hasAvailableDays", hasAvailableDays);
            response.put("message", hasAvailableDays ?
                    "Hay días disponibles para añadir" :
                    "Todos los días seleccionados ya están añadidos");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("isValid", false);
            response.put("error", "Error validando días: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ==========================================
    // ESTADÍSTICAS BÁSICAS
    // ==========================================
    /**
     * POST /api/shift-business/calculate-stats
     */
    @PostMapping(value = "/calculate-stats", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> calculateShiftStats(
            @RequestBody List<ShiftBusinessDTOs.ShiftDetailData> shiftDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            double totalHours = shiftDetails.stream()
                    .mapToDouble(this::calculateHoursFromDetail)
                    .sum();

            int totalBreakMinutes = shiftDetails.stream()
                    .mapToInt(d -> d.getBreakDuration() != null ? d.getBreakDuration() : 0)
                    .sum();

            long uniqueDays = shiftDetails.stream()
                    .map(ShiftBusinessDTOs.ShiftDetailData::getDayOfWeek)
                    .distinct()
                    .count();

            response.put("success", true);
            response.put("totalHours", totalHours);
            response.put("totalBreakMinutes", totalBreakMinutes);
            response.put("totalBreakHours", totalBreakMinutes / 60.0);
            response.put("uniqueDays", uniqueDays);
            response.put("totalDetails", shiftDetails.size());
            response.put("averageHoursPerDay", uniqueDays > 0 ? totalHours / uniqueDays : 0);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Error calculando estadísticas: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES PRIVADOS (idénticos a tu versión)
    // ==========================================
    private Integer getDayNumber(String dayName) {
        Map<String, Integer> dayMapping = Map.of(
                "monday", 1, "tuesday", 2, "wednesday", 3, "thursday", 4,
                "friday", 5, "saturday", 6, "sunday", 7
        );
        return dayMapping.get(dayName);
    }

    private double calculateHoursFromDetail(ShiftBusinessDTOs.ShiftDetailData detail) {
        try {
            String startTime = detail.getStartTime();
            String endTime = detail.getEndTime();
            if (startTime == null || endTime == null) return 0.0;

            int startMinutes = timeToMinutes(startTime);
            int endMinutes = timeToMinutes(endTime);
            if (startMinutes == -1 || endMinutes == -1) return 0.0;

            if (endMinutes < startMinutes) endMinutes += 24 * 60; // cruza medianoche
            return (endMinutes - startMinutes) / 60.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int timeToMinutes(String time) {
        try {
            time = time.trim().toUpperCase();
            if (time.matches("^\\d{1,2}:\\d{2}$")) {
                String[] parts = time.split(":");
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
            if (time.matches("^\\d{1,2}:\\d{2}\\s*(AM|PM)$")) {
                String base = time.replace(" AM", "").replace(" PM", "");
                String[] parts = base.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                if (time.contains("AM") && hours == 12) hours = 0;
                else if (time.contains("PM") && hours != 12) hours += 12;
                return hours * 60 + minutes;
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}

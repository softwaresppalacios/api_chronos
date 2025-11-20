package sp.sistemaspalacios.api_chronos.controller.shift;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sp.sistemaspalacios.api_chronos.service.shift.ValidationService;

import java.util.Map;

@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * Valida un rango de tiempo específico
     * POST /api/validation/time-range
     */
    @PostMapping("/time-range")
    public ResponseEntity<Map<String, Object>> validateTimeRange(@RequestBody TimeRangeValidationRequest request) {
        try {
            Map<String, Object> result = validationService.validateTimeRange(
                    request.getPeriod(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getHoursPerDay()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isValid", false,
                    "error", e.getMessage(),
                    "hours", 0.0
            ));
        }
    }

    /**
     * Valida múltiples rangos de tiempo del día
     * POST /api/validation/daily-ranges
     */
    @PostMapping("/daily-ranges")
    public ResponseEntity<Map<String, Object>> validateDailyRanges(@RequestBody DailyRangesValidationRequest request) {
        try {
            Map<String, Object> result = validationService.validateDailyTimeRanges(
                    request.getTimeRanges(),
                    request.getHoursPerDay()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isValid", false,
                    "error", e.getMessage(),
                    "totalHours", 0.0
            ));
        }
    }

    /**
     * Valida un break específico
     * POST /api/validation/break
     */
    @PostMapping("/break")
    public ResponseEntity<Map<String, Object>> validateBreak(@RequestBody BreakValidationRequest request) {
        try {
            Map<String, Object> result = validationService.validateBreak(
                    request.getPeriod(),
                    request.getBreakStart(),
                    request.getBreakEnd(),
                    request.getWorkStart(),
                    request.getWorkEnd()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isValid", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Calcula horas totales de una lista de detalles
     * POST /api/validation/calculate-hours
     */
    @PostMapping("/calculate-hours")
    public ResponseEntity<Map<String, Object>> calculateHours(@RequestBody HoursCalculationRequest request) {
        try {
            Map<String, Object> result = validationService.calculateTotalHours(request.getShiftDetails());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "totalHours", 0.0,
                    "totalMinutes", 0
            ));
        }
    }

    /**
     * Convierte formato 12h AM/PM a 24h
     * POST /api/validation/convert-time
     */
    @PostMapping("/convert-time")
    public ResponseEntity<Map<String, Object>> convertTime(@RequestBody TimeConversionRequest request) {
        try {
            Map<String, Object> result = validationService.convertTimeFormat(
                    request.getTime12h(),
                    request.getTargetFormat()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "convertedTime", ""
            ));
        }
    }

    // ==========================================
    // CLASES INTERNAS PARA REQUEST BODY
    // ==========================================

    public static class TimeRangeValidationRequest {
        private String period;
        private String startTime;
        private String endTime;
        private Double hoursPerDay;

        // Getters y Setters
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public Double getHoursPerDay() { return hoursPerDay; }
        public void setHoursPerDay(Double hoursPerDay) { this.hoursPerDay = hoursPerDay; }
    }

    public static class DailyRangesValidationRequest {
        private Map<String, TimeRangeData> timeRanges;
        private Double hoursPerDay;

        public Map<String, TimeRangeData> getTimeRanges() { return timeRanges; }
        public void setTimeRanges(Map<String, TimeRangeData> timeRanges) { this.timeRanges = timeRanges; }

        public Double getHoursPerDay() { return hoursPerDay; }
        public void setHoursPerDay(Double hoursPerDay) { this.hoursPerDay = hoursPerDay; }

        public static class TimeRangeData {
            private String start;
            private String end;

            public String getStart() { return start; }
            public void setStart(String start) { this.start = start; }

            public String getEnd() { return end; }
            public void setEnd(String end) { this.end = end; }
        }
    }

    public static class BreakValidationRequest {
        private String period;
        private String breakStart;
        private String breakEnd;
        private String workStart;
        private String workEnd;

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public String getBreakStart() { return breakStart; }
        public void setBreakStart(String breakStart) { this.breakStart = breakStart; }

        public String getBreakEnd() { return breakEnd; }
        public void setBreakEnd(String breakEnd) { this.breakEnd = breakEnd; }

        public String getWorkStart() { return workStart; }
        public void setWorkStart(String workStart) { this.workStart = workStart; }

        public String getWorkEnd() { return workEnd; }
        public void setWorkEnd(String workEnd) { this.workEnd = workEnd; }
    }

    public static class HoursCalculationRequest {
        private java.util.List<ShiftDetailData> shiftDetails;

        public java.util.List<ShiftDetailData> getShiftDetails() { return shiftDetails; }
        public void setShiftDetails(java.util.List<ShiftDetailData> shiftDetails) { this.shiftDetails = shiftDetails; }

        public static class ShiftDetailData {
            private String startTime;
            private String endTime;
            private String breakStartTime;
            private String breakEndTime;

            public String getStartTime() { return startTime; }
            public void setStartTime(String startTime) { this.startTime = startTime; }

            public String getEndTime() { return endTime; }
            public void setEndTime(String endTime) { this.endTime = endTime; }

            public String getBreakStartTime() { return breakStartTime; }
            public void setBreakStartTime(String breakStartTime) { this.breakStartTime = breakStartTime; }

            public String getBreakEndTime() { return breakEndTime; }
            public void setBreakEndTime(String breakEndTime) { this.breakEndTime = breakEndTime; }
        }
    }

    public static class TimeConversionRequest {
        private String time12h;
        private String targetFormat; // "24h" o "12h"

        public String getTime12h() { return time12h; }
        public void setTime12h(String time12h) { this.time12h = time12h; }

        public String getTargetFormat() { return targetFormat; }
        public void setTargetFormat(String targetFormat) { this.targetFormat = targetFormat; }
    }
}

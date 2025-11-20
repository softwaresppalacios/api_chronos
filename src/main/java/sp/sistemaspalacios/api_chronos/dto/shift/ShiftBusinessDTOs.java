package sp.sistemaspalacios.api_chronos.dto.shift;

import java.util.List;
import java.util.Map;

public class ShiftBusinessDTOs {

    /**
     * DTO para validar formulario de turno completo
     */
    public static class ValidateShiftFormRequest {
        private String shiftname;
        private String typeShift;
        private Long dependencyId;

        private List<ShiftDetailData> tempShiftDetails;
        private Double weeklyHoursLimit;
        private Double hoursPerDay;
        private String nightHoursStart;

        // Getters y Setters
        public String getShiftname() { return shiftname; }
        public void setShiftname(String shiftname) { this.shiftname = shiftname; }

        public String getTypeShift() { return typeShift; }
        public void setTypeShift(String typeShift) { this.typeShift = typeShift; }

        public Long getDependencyId() { return dependencyId; }
        public void setDependencyId(Long dependencyId) { this.dependencyId = dependencyId; }

        public List<ShiftDetailData> getTempShiftDetails() { return tempShiftDetails; }
        public void setTempShiftDetails(List<ShiftDetailData> tempShiftDetails) { this.tempShiftDetails = tempShiftDetails; }

        public Double getWeeklyHoursLimit() { return weeklyHoursLimit; }
        public void setWeeklyHoursLimit(Double weeklyHoursLimit) { this.weeklyHoursLimit = weeklyHoursLimit; }

        public Double getHoursPerDay() { return hoursPerDay; }
        public void setHoursPerDay(Double hoursPerDay) { this.hoursPerDay = hoursPerDay; }

        public String getNightHoursStart() { return nightHoursStart; }
        public void setNightHoursStart(String nightHoursStart) { this.nightHoursStart = nightHoursStart; }
    }

    /**
     * DTO para añadir detalle de turno con validación
     */
    public static class AddShiftDetailRequest {
        private Map<String, Boolean> selectedDays; // monday: true, tuesday: false, etc
        private Map<String, TimeRangeData> timeRanges; // Mañana: {start, end}, Tarde: {start, end}
        private Map<String, TimeRangeData> breakRanges; // Breaks por período
        private List<ShiftDetailData> existingDetails;
        private Double weeklyHoursLimit;
        private Double hoursPerDay;

        // Getters y Setters
        public Map<String, Boolean> getSelectedDays() { return selectedDays; }
        public void setSelectedDays(Map<String, Boolean> selectedDays) { this.selectedDays = selectedDays; }

        public Map<String, TimeRangeData> getTimeRanges() { return timeRanges; }
        public void setTimeRanges(Map<String, TimeRangeData> timeRanges) { this.timeRanges = timeRanges; }

        public Map<String, TimeRangeData> getBreakRanges() { return breakRanges; }
        public void setBreakRanges(Map<String, TimeRangeData> breakRanges) { this.breakRanges = breakRanges; }

        public List<ShiftDetailData> getExistingDetails() { return existingDetails; }
        public void setExistingDetails(List<ShiftDetailData> existingDetails) { this.existingDetails = existingDetails; }

        public Double getWeeklyHoursLimit() { return weeklyHoursLimit; }
        public void setWeeklyHoursLimit(Double weeklyHoursLimit) { this.weeklyHoursLimit = weeklyHoursLimit; }

        public Double getHoursPerDay() { return hoursPerDay; }
        public void setHoursPerDay(Double hoursPerDay) { this.hoursPerDay = hoursPerDay; }
    }

    /**
     * DTO para guardar turno completo
     */
    public static class SaveCompleteShiftRequest {
        private String shiftname;
        private String typeShift;
        private Long dependencyId;
        private List<ShiftDetailData> shiftDetails;
        private String weeklyHours;
        private String hoursPerDay;
        private String nightHoursStart;

        // Getters y Setters
        public String getShiftname() { return shiftname; }
        public void setShiftname(String shiftname) { this.shiftname = shiftname; }

        public String getTypeShift() { return typeShift; }
        public void setTypeShift(String typeShift) { this.typeShift = typeShift; }

        public Long getDependencyId() { return dependencyId; }
        public void setDependencyId(Long dependencyId) { this.dependencyId = dependencyId; }

        public List<ShiftDetailData> getShiftDetails() { return shiftDetails; }
        public void setShiftDetails(List<ShiftDetailData> shiftDetails) { this.shiftDetails = shiftDetails; }

        public String getWeeklyHours() { return weeklyHours; }
        public void setWeeklyHours(String weeklyHours) { this.weeklyHours = weeklyHours; }

        public String getHoursPerDay() { return hoursPerDay; }
        public void setHoursPerDay(String hoursPerDay) { this.hoursPerDay = hoursPerDay; }

        public String getNightHoursStart() { return nightHoursStart; }
        public void setNightHoursStart(String nightHoursStart) { this.nightHoursStart = nightHoursStart; }
    }

    // ==========================================
    // DTOs PARA RESPONSES
    // ==========================================

    /**
     * DTO para respuesta de validación de formulario
     */
    public static class ShiftFormValidationResponse {
        private boolean isValid;
        private List<String> errors;
        private List<String> warnings;
        private Double currentTotalHours;
        private Double weeklyHoursLimit;
        private String completionStatus; // COMPLETE, INCOMPLETE, EXCEEDED

        // Getters y Setters
        public boolean isValid() { return isValid; }
        public void setValid(boolean valid) { isValid = valid; }

        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }

        public Double getCurrentTotalHours() { return currentTotalHours; }
        public void setCurrentTotalHours(Double currentTotalHours) { this.currentTotalHours = currentTotalHours; }

        public Double getWeeklyHoursLimit() { return weeklyHoursLimit; }
        public void setWeeklyHoursLimit(Double weeklyHoursLimit) { this.weeklyHoursLimit = weeklyHoursLimit; }

        public String getCompletionStatus() { return completionStatus; }
        public void setCompletionStatus(String completionStatus) { this.completionStatus = completionStatus; }
    }

    /**
     * DTO para respuesta de añadir detalle
     */
    public static class AddShiftDetailResponse {
        private boolean success;
        private String message;
        private List<ShiftDetailData> newShiftDetails;
        private Double totalHoursAfterAdd;
        private List<String> warnings;

        // Getters y Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public List<ShiftDetailData> getNewShiftDetails() { return newShiftDetails; }
        public void setNewShiftDetails(List<ShiftDetailData> newShiftDetails) { this.newShiftDetails = newShiftDetails; }

        public Double getTotalHoursAfterAdd() { return totalHoursAfterAdd; }
        public void setTotalHoursAfterAdd(Double totalHoursAfterAdd) { this.totalHoursAfterAdd = totalHoursAfterAdd; }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }

    // ==========================================
    // DTOs AUXILIARES
    // ==========================================

    /**
     * DTO para datos de horarios
     */
    public static class TimeRangeData {
        private String start;
        private String end;

        public TimeRangeData() {}

        public TimeRangeData(String start, String end) {
            this.start = start;
            this.end = end;
        }

        // Getters y Setters
        public String getStart() { return start; }
        public void setStart(String start) { this.start = start; }

        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end; }
    }

    /**
     * DTO para detalle de turno
     */
    public static class ShiftDetailData {
        private Integer dayOfWeek;
        private String startTime;
        private String endTime;
        private String period; // Mañana, Tarde, Noche
        private String breakStartTime;
        private String breakEndTime;
        private Integer breakDuration; // minutos

        public ShiftDetailData() {}

        // Getters y Setters
        public Integer getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }

        public String getBreakStartTime() { return breakStartTime; }
        public void setBreakStartTime(String breakStartTime) { this.breakStartTime = breakStartTime; }

        public String getBreakEndTime() { return breakEndTime; }
        public void setBreakEndTime(String breakEndTime) { this.breakEndTime = breakEndTime; }

        public Integer getBreakDuration() { return breakDuration; }
        public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }
    }
}
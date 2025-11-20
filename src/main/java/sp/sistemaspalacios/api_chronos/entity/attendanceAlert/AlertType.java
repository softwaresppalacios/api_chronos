package sp.sistemaspalacios.api_chronos.entity.attendanceAlert;

public enum AlertType {
    ON_TIME,              // Llegó puntual
    LATE_MINOR,           // Tarde menor (configurable)
    LATE_MODERATE,        // Tarde moderado (configurable)
    LATE_SEVERE,          // Tarde grave (configurable)
    EARLY_MODERATE,       // Temprano moderado (configurable)
    EARLY_EXCESSIVE       // Temprano excesivo (configurable)
}
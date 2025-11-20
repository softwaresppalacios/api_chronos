package sp.sistemaspalacios.api_chronos.entity.attendanceAlert;

public enum AlertAction {
    NOTIFY,               // Solo notificar
    WARN,                 // Advertir al empleado
    BLOCK,                // Bloquear marcación
    REQUIRE_APPROVAL      // Requiere aprobación GH
}
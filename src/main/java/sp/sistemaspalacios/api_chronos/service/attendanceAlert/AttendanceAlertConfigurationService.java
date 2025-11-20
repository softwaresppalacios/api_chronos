package sp.sistemaspalacios.api_chronos.service.attendanceAlert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AlertAction;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AlertType;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AttendanceAlertConfiguration;
import sp.sistemaspalacios.api_chronos.repository.attendanceAlert.AttendanceAlertConfigurationRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAlertConfigurationService {

    private final AttendanceAlertConfigurationRepository repository;

    public List<AttendanceAlertConfiguration> getAllActiveConfigurations() {
        return repository.findAllActiveOrderedByPriority();
    }

    public AttendanceAlertConfiguration getByAlertType(AlertType alertType) {
        return repository.findByAlertType(alertType).orElse(null);
    }

    @Transactional
    public AttendanceAlertConfiguration saveConfiguration(AttendanceAlertConfiguration config) {
        if (config.getAlertType() == null) {
            throw new IllegalArgumentException("El tipo de alerta es requerido");
        }
        if (config.getThresholdMinutes() == null || config.getThresholdMinutes() < 0) {
            throw new IllegalArgumentException("El umbral debe ser un número positivo");
        }
        if (config.getAction() == null) {
            throw new IllegalArgumentException("La acción es requerida");
        }

        return repository.save(config);
    }

    @Transactional
    public AttendanceAlertConfiguration toggleActive(Long id, Boolean isActive) {
        AttendanceAlertConfiguration config = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada"));

        config.setIsActive(isActive);
        return repository.save(config);
    }

    @Transactional
    public void deleteConfiguration(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void initializeDefaultConfigurations() {
        log.info("🔧 Inicializando configuraciones de alerta por defecto...");

        createIfNotExists(AlertType.ON_TIME, 0,
                "✅ Marcación puntual registrada",
                AlertAction.NOTIFY, false, 1);

        createIfNotExists(AlertType.LATE_MINOR, 5,
                "⚠️ Llegó {minutes} minutos tarde. Primera advertencia.",
                AlertAction.WARN, true, 2);

        createIfNotExists(AlertType.LATE_MODERATE, 20,
                "🚨 Llegó {minutes} minutos tarde. Debe presentarse en Gestión Humana.",
                AlertAction.REQUIRE_APPROVAL, true, 3);

        createIfNotExists(AlertType.LATE_SEVERE, 999,
                "🚫 TARDANZA GRAVE: Llegó {minutes} minutos tarde. ACCESO BLOQUEADO.",
                AlertAction.BLOCK, true, 4);

        createIfNotExists(AlertType.EARLY_MODERATE, 10,
                "ℹ️ Llegó {minutes} minutos antes de su horario programado.",
                AlertAction.NOTIFY, true, 5);

        createIfNotExists(AlertType.EARLY_EXCESSIVE, 60,
                "⚠️ Llegó {minutes} minutos antes ({hours} horas). Se notificará a supervisión.",
                AlertAction.NOTIFY, true, 6);

        log.info("✅ Configuraciones de alerta inicializadas");
    }

    private void createIfNotExists(
            AlertType alertType,
            Integer thresholdMinutes,
            String messageTemplate,
            AlertAction action,
            Boolean sendNotification,
            Integer priority
    ) {
        if (repository.findByAlertType(alertType).isEmpty()) {
            AttendanceAlertConfiguration config = new AttendanceAlertConfiguration();
            config.setAlertType(alertType);
            config.setThresholdMinutes(thresholdMinutes);
            config.setMessageTemplate(messageTemplate);
            config.setAction(action);
            config.setSendNotification(sendNotification);
            config.setIsActive(true);
            config.setPriority(priority);

            repository.save(config);
            log.info("  ➕ Creada: {}", alertType);
        }
    }
}
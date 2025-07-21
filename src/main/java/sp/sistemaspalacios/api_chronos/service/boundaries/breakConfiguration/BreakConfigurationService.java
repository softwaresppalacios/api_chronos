package sp.sistemaspalacios.api_chronos.service.boundaries.breakConfiguration;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.boundaries.breakConfiguration.BreakConfiguration;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.boundaries.breakConfiguration.BreakConfigurationRepository;

import java.util.List;
import java.util.Optional;

@Service
public class BreakConfigurationService {

    private final BreakConfigurationRepository breakConfigurationRepository;

    public BreakConfigurationService(BreakConfigurationRepository breakConfigurationRepository) {
        this.breakConfigurationRepository = breakConfigurationRepository;
    }
    /**
     * 🔹 Obtener la configuración actual de break (solo debe haber una)
     */
    public BreakConfiguration getCurrentBreakConfiguration() {
        Optional<BreakConfiguration> config = breakConfigurationRepository.findLatestBreakConfiguration();
        if (config.isEmpty()) {
            throw new ResourceNotFoundException("No hay configuración de descanso establecida. Debe configurar el tiempo de descanso primero.");
        }
        return config.get();
    }

    /**
     * 🔹 Guardar o actualizar configuración de break
     * LÓGICA: Si existe configuración, se ACTUALIZA. Si no existe, se CREA.
     */
    public BreakConfiguration saveOrUpdateBreakConfiguration(Integer minutes) {
        validateBreakMinutes(minutes);

        Optional<BreakConfiguration> existingConfig = breakConfigurationRepository.findLatestBreakConfiguration();

        if (existingConfig.isPresent()) {
            // 🔸 ACTUALIZAR configuración existente
            BreakConfiguration config = existingConfig.get();
            config.setMinutes(minutes);
            return breakConfigurationRepository.save(config);
        } else {
            // 🔸 CREAR nueva configuración
            BreakConfiguration newConfig = new BreakConfiguration(minutes);
            return breakConfigurationRepository.save(newConfig);
        }
    }

    /**
     * 🔹 Obtener todas las configuraciones (para debug/admin)
     */
    public List<BreakConfiguration> getAllBreakConfigurations() {
        return breakConfigurationRepository.findAll();
    }

    /**
     * 🔹 Eliminar configuración por ID (uso administrativo)
     */
    public void deleteBreakConfiguration(Long id) {
        BreakConfiguration config = breakConfigurationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Configuración de break con ID " + id + " no encontrada"));
        breakConfigurationRepository.delete(config);
    }

    /**
     * 🔹 Verificar si existe configuración de break
     */
    public boolean hasBreakConfiguration() {
        return breakConfigurationRepository.findLatestBreakConfiguration().isPresent();
    }

    /**
     * 🔹 Obtener los minutos configurados (método helper)
     */
    public Integer getCurrentBreakMinutes() {
        BreakConfiguration config = getCurrentBreakConfiguration();
        return config.getMinutes();
    }

    /**
     * 🔹 Validaciones de negocio
     */
    private void validateBreakMinutes(Integer minutes) {
        if (minutes == null) {
            throw new IllegalArgumentException("Los minutos de descanso son obligatorios");
        }
        if (minutes < 0) {
            throw new IllegalArgumentException("Los minutos de descanso no pueden ser negativos");
        }
        if (minutes > 480) { // Máximo 8 horas (480 minutos)
            throw new IllegalArgumentException("Los minutos de descanso no pueden exceder 480 minutos (8 horas)");
        }
    }
}
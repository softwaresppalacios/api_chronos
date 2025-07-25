package sp.sistemaspalacios.api_chronos.service.boundaries.generalConfiguration;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.boundaries.generalConfiguration.GeneralConfiguration;
import sp.sistemaspalacios.api_chronos.repository.boundaries.generalConfiguration.GeneralConfigurationRepository;

@Service
@RequiredArgsConstructor
public class GeneralConfigurationService {

    private final GeneralConfigurationRepository repository;

    /**
     * 🔹 Obtener la configuración por tipo
     */
    public GeneralConfiguration getByType(String type) {
        return repository.findByType(type)
                .orElseThrow(() -> new IllegalArgumentException("No hay configuración para: " + type));
    }

    /**
     * 🔹 Guardar o actualizar una configuración
     */


    public GeneralConfiguration saveOrUpdate(String type, String rawValue) {
        validateConfiguration(type, rawValue); // ⬅️ Agregamos validación

        GeneralConfiguration existing = repository.findByType(type).orElse(null);

        if (existing == null) {
            existing = new GeneralConfiguration();
            existing.setType(type);
        }

        existing.setValue(rawValue); // actualiza valor
        return repository.save(existing); // guarda
    }

    private void validateConfiguration(String type, String rawValue) {
        try {
            // DAILY_HOURS: mínimo 5:00
            if ("DAILY_HOURS".equalsIgnoreCase(type)) {
                String[] parts = rawValue.split(":");
                int hours = Integer.parseInt(parts[0]);
                if (hours < 5) {
                    throw new IllegalArgumentException("El valor mínimo para DAILY_HOURS debe ser 5:00 (5 horas).");
                }
            }
// NIGHT_START: debe ser >= 19:00
            if ("NIGHT_START".equalsIgnoreCase(type)) {
                if (!rawValue.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                    throw new IllegalArgumentException("El formato de hora nocturna es inválido. Usa HH:mm, por ejemplo '19:00'.");
                }

                String[] parts = rawValue.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);

                if (hour < 19) {
                    throw new IllegalArgumentException("La jornada nocturna solo puede comenzar a partir de las 19:00 (7:00 PM).");
                }
            }

            // BREAK: mínimo 30 minutos — acepta "30", "30 minutes", "00:30"
            if ("BREAK".equalsIgnoreCase(type)) {
                int totalMinutes;

                if (rawValue.matches("^\\d+$")) {
                    // ✅ Caso "30"
                    totalMinutes = Integer.parseInt(rawValue);
                } else if (rawValue.toLowerCase().contains("minute")) {
                    // ✅ Caso "30 minutes"
                    totalMinutes = Integer.parseInt(rawValue.replaceAll("[^0-9]", ""));
                } else if (rawValue.contains(":")) {
                    // ✅ Caso "00:30"
                    String[] parts = rawValue.split(":");
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);
                    totalMinutes = hours * 60 + minutes;
                } else {
                    throw new IllegalArgumentException("Formato no válido para BREAK. Usa un número como '30', '30 minutes' o '00:30'.");
                }

                if (totalMinutes < 30) {
                    throw new IllegalArgumentException("El tiempo mínimo de descanso (BREAK) debe ser 30 minutos.");
                }
            }

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Formato inválido. Usa '30', '30 minutes' o '00:30'.");
        }
    }


}

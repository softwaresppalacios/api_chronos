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
        GeneralConfiguration existing = repository.findByType(type).orElse(null);

        if (existing == null) {
            existing = new GeneralConfiguration();
            existing.setType(type);
        }

        existing.setValue(rawValue); // actualiza valor
        return repository.save(existing); // guarda
    }


}

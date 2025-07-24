package sp.sistemaspalacios.api_chronos.repository.boundaries.generalConfiguration;

import org.springframework.data.jpa.repository.JpaRepository;
import sp.sistemaspalacios.api_chronos.entity.boundaries.generalConfiguration.GeneralConfiguration;

import java.util.Optional;

public interface GeneralConfigurationRepository extends JpaRepository<GeneralConfiguration, Long> {
    Optional<GeneralConfiguration> findByType(String type);
    void deleteByType(String type);
}
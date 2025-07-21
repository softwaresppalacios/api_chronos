
package sp.sistemaspalacios.api_chronos.repository.boundaries.breakConfiguration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.boundaries.breakConfiguration.BreakConfiguration;

import java.util.Optional;

@Repository
public interface BreakConfigurationRepository extends JpaRepository<BreakConfiguration, Long> {

    /**
     * Encuentra la configuración de break más reciente (solo debe haber una)
     * Ordenada por fecha de creación descendente
     */
    @Query("SELECT bc FROM BreakConfiguration bc ORDER BY bc.createdAt DESC")
    Optional<BreakConfiguration> findLatestBreakConfiguration();

    /**
     * Encuentra la primera configuración (debería ser la única)
     */
    Optional<BreakConfiguration> findFirstByOrderByCreatedAtDesc();
}
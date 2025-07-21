package sp.sistemaspalacios.api_chronos.repository.NightHours;

import org.springframework.data.jpa.repository.JpaRepository;
import sp.sistemaspalacios.api_chronos.entity.boundaries.nightHours.NightHours;

public interface NightHoursRepository extends JpaRepository<NightHours, Long> {
    NightHours findTopByOrderByCreatedAtDesc();
}

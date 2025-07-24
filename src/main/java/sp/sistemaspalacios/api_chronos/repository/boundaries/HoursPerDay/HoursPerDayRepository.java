package sp.sistemaspalacios.api_chronos.repository.boundaries.HoursPerDay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sp.sistemaspalacios.api_chronos.entity.boundaries.hoursPerDay.HoursPerDay;

import java.util.Optional;

public interface HoursPerDayRepository extends JpaRepository<HoursPerDay, Long> {

    @Query("SELECT h FROM HoursPerDay h ORDER BY h.createdAt DESC LIMIT 1")
    Optional<HoursPerDay> findLatest();
}
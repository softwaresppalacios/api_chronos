package sp.sistemaspalacios.api_chronos.repository.boundaries.weeklyHours;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.boundaries.weeklyHours.WeeklyHours;

import java.util.Optional;

@Repository
public interface WeeklyHoursRepository extends JpaRepository<WeeklyHours, Long> {

    Optional<WeeklyHours> findByHours(String hours);

    Optional<WeeklyHours> findFirstByOrderByIdAsc();



}
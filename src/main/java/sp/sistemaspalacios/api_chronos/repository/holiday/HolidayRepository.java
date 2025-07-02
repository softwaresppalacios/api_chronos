package sp.sistemaspalacios.api_chronos.repository.holiday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.holiday.Holiday;
@Repository

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
}

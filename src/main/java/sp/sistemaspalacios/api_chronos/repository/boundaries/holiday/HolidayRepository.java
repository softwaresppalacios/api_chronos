package sp.sistemaspalacios.api_chronos.repository.boundaries.holiday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.holiday.Holiday;

import java.time.LocalDate;
import java.util.Optional;

@Repository

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    boolean existsByHolidayDate(LocalDate holidayDate);

    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);
}
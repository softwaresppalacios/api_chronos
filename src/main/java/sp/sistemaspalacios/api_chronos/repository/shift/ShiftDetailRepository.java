package sp.sistemaspalacios.api_chronos.repository.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftDetailRepository extends JpaRepository<ShiftDetail, Long> {
    List<ShiftDetail> findByShiftId(Long shiftId);

    Optional<ShiftDetail> findByShiftIdAndDayOfWeek(Long shiftId, int dayOfWeek);
    List<ShiftDetail> findByShiftIdAndDayOfWeek(Long shiftId, Integer dayOfWeek);


}


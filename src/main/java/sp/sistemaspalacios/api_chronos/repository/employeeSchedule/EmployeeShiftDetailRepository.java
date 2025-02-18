package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeShiftDetail;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeShiftDetailRepository extends JpaRepository<EmployeeShiftDetail, Long> {
    List<EmployeeShiftDetail> findByEmployeeScheduleId(Long employeeScheduleId);
    Optional<EmployeeShiftDetail> findByEmployeeScheduleIdAndDayOfWeek(Long employeeScheduleId, Integer dayOfWeek);
}


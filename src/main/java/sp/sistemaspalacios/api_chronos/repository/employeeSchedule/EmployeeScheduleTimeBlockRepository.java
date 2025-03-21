package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;
@Repository
public interface EmployeeScheduleTimeBlockRepository extends JpaRepository<EmployeeScheduleTimeBlock, Long> {
}

package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleTimeBlock;

import java.util.List;

@Repository
public interface EmployeeScheduleTimeBlockRepository extends JpaRepository<EmployeeScheduleTimeBlock, Long> {
    List<EmployeeScheduleTimeBlock> findByEmployeeScheduleDayIdIn(List<Long> dayIds);
    @Modifying
    @Transactional
    void deleteByEmployeeScheduleDayId(Long employeeScheduleDayId);

}

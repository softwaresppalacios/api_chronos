package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;

import java.util.List;

@Repository
public interface EmployeeScheduleDayRepository extends JpaRepository<EmployeeScheduleDay, Long> {
    List<EmployeeScheduleDay> findByEmployeeSchedule_Id(Long employeeScheduleId);
    List<EmployeeScheduleDay> findByEmployeeScheduleId(Long employeeScheduleId);
}
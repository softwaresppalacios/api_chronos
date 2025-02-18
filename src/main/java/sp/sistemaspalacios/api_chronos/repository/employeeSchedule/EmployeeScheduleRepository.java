package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import java.util.List;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {
    List<EmployeeSchedule> findByEmployeeId(Long employeeId);
    List<EmployeeSchedule> findByShiftId(Long shiftId);
}


package sp.sistemaspalacios.api_chronos.repository.employeeAttendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeAttendanceRepository extends JpaRepository<EmployeeAttendance, Long> {
    List<EmployeeAttendance> findByEmployeeSchedule(EmployeeSchedule employeeSchedule);
    Optional<EmployeeAttendance> findTopByEmployeeScheduleOrderByTimestampDesc(EmployeeSchedule employeeSchedule);
}

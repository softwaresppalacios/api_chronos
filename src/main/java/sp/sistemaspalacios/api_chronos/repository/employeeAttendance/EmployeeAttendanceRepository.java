package sp.sistemaspalacios.api_chronos.repository.employeeAttendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeAttendance.EmployeeAttendance;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeAttendanceRepository extends JpaRepository<EmployeeAttendance, Long> {
    List<EmployeeAttendance> findByEmployeeSchedule(EmployeeSchedule employeeSchedule);
    Optional<EmployeeAttendance> findTopByEmployeeScheduleOrderByTimestampDesc(EmployeeSchedule employeeSchedule);

    @Query("SELECT ea FROM EmployeeAttendance ea " +
            "WHERE ea.employeeSchedule = :schedule " +
            "AND DATE(ea.timestamp) = :date " +
            "ORDER BY ea.timestamp ASC")
    List<EmployeeAttendance> findByEmployeeScheduleAndDate(
            EmployeeSchedule schedule,
            Date date
    );
}

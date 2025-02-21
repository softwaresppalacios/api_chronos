package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {
    List<EmployeeSchedule> findByEmployeeId(Long employeeId);

    List<EmployeeSchedule> findByShiftId(Long shiftId);

    List<EmployeeSchedule> findByEmployeeIdIn(List<Long> employeeIds);

    @Query("SELECT es FROM EmployeeSchedule es JOIN FETCH es.shift WHERE es.id = :id")
    Optional<EmployeeSchedule> findByIdWithShift(@Param("id") Long id);

    @Query("SELECT es FROM EmployeeSchedule es WHERE FUNCTION('DATE', es.startDate) >= :startDate AND FUNCTION('DATE', es.endDate) <= :endDate")
    List<EmployeeSchedule> findByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT e FROM EmployeeSchedule e WHERE e.startDate >= :startDate AND e.endDate IS NULL")
    List<EmployeeSchedule> findByStartDateAndNullEndDate(@Param("startDate") Date startDate);

}


package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {
    List<EmployeeSchedule> findByEmployeeId(Long employeeId);
    List<EmployeeSchedule> findByShiftId(Long shiftId);
    List<EmployeeSchedule> findByEmployeeIdIn(List<Long> employeeIds);
    @Query("SELECT es FROM EmployeeSchedule es JOIN FETCH es.shift WHERE es.id = :id")
    Optional<EmployeeSchedule> findByIdWithShift(@Param("id") Long id);

}


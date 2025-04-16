package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;

import java.util.List;

@Repository
public interface EmployeeScheduleDayRepository extends JpaRepository<EmployeeScheduleDay, Long> {

    // Consulta nativa para buscar días por schedule ID usando el array day_ids
    @Query(value = "SELECT esd.* FROM employee_schedule_days esd " +
            "JOIN employee_schedules es ON esd.id = ANY(es.day_ids) " +
            "WHERE es.id = :scheduleId", nativeQuery = true)
    List<EmployeeScheduleDay> findDaysByScheduleId(@Param("scheduleId") Long scheduleId);

    // Método para buscar múltiples días por sus IDs (útil para cargar los días del array)
    List<EmployeeScheduleDay> findByIdIn(List<Long> dayIds);

    List<EmployeeScheduleDay> findByDaysParentIdIn(List<Long> daysParentIds);









}
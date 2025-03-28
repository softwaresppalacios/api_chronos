package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {
    List<EmployeeSchedule> findByEmployeeId(Long employeeId);

    List<EmployeeSchedule> findByShiftId(Long shiftId);


    @Query("SELECT es FROM EmployeeSchedule es JOIN FETCH es.shift WHERE es.id = :id")
    Optional<EmployeeSchedule> findByIdWithShift(@Param("id") Long id);

    @Query("SELECT es FROM EmployeeSchedule es WHERE FUNCTION('DATE', es.startDate) >= :startDate AND FUNCTION('DATE', es.endDate) <= :endDate")
    List<EmployeeSchedule> findByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT e FROM EmployeeSchedule e WHERE e.startDate >= :startDate AND e.endDate IS NULL")
    List<EmployeeSchedule> findByStartDateAndNullEndDate(@Param("startDate") Date startDate);




    // Consulta para verificar existencia
    @Query("SELECT CASE WHEN COUNT(es) > 0 THEN true ELSE false END " +
            "FROM EmployeeSchedule es " +
            "WHERE es.employeeId = :employeeId " +
            "AND :date BETWEEN es.startDate AND es.endDate")
    boolean existsByEmployeeIdAndDate(@Param("employeeId") Long employeeId,
                                      @Param("date") Date date);

    // Nueva consulta para buscar por múltiples IDs
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId IN :employeeIds")
    List<EmployeeSchedule> findByEmployeeIds(@Param("employeeIds") List<Long> employeeIds);


    List<EmployeeSchedule> findByDaysParentId(Long daysParentId);
    List<EmployeeSchedule> findByEmployeeIdAndDaysParentId(Long employeeId, Long daysParentId);
    List<EmployeeSchedule> findByStartDateBetween(Date startDate, Date endDate);


    // O si necesitas información del empleado (JOIN)
    // Opción 2: Con JOIN al empleado (asegúrate que es.employee exista)
    //@Query("SELECT es FROM EmployeeSchedule es JOIN FETCH es.employee WHERE es.employee.id IN :employeeIds")
    //List<EmployeeSchedule> findSchedulesForEmployees(@Param("employeeIds") List<Long> employeeIds);

    // Opción 3: Similar pero con otro nombre
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId IN :employeeIds")
    List<EmployeeSchedule> findSchedulesForEmployees(@Param("employeeIds") List<Long> employeeIds);

    @Query("SELECT DISTINCT es FROM EmployeeSchedule es " +
            "LEFT JOIN FETCH es.days d " +
            "LEFT JOIN FETCH d.timeBlocks " +
            "WHERE es.employeeId IN :employeeIds")
    List<EmployeeSchedule> findByEmployeeIdInWithDaysAndTimeBlocks(@Param("employeeIds") List<Long> employeeIds);











    @Query("SELECT DISTINCT es FROM EmployeeSchedule es " +
            "LEFT JOIN FETCH es.days d " +
            "WHERE es.employeeId IN :employeeIds AND " +
            "EXISTS (SELECT 1 FROM EmployeeScheduleTimeBlock t WHERE t.employeeScheduleDay = d)")
    List<EmployeeSchedule> findByEmployeeIdIn(@Param("employeeIds") List<Long> employeeIds);










    @Query("SELECT DISTINCT es FROM EmployeeSchedule es " +
            "LEFT JOIN FETCH es.days d " +
            "WHERE es.employeeId IN :employeeIds " +
            "ORDER BY es.id, d.date")
    List<EmployeeSchedule> findByEmployeeIdInWithDays(@Param("employeeIds") List<Long> employeeIds);

    // Consulta adicional para cargar los timeBlocks en batch
    @Query("SELECT d FROM EmployeeScheduleDay d " +
            "LEFT JOIN FETCH d.timeBlocks t " +
            "WHERE d.employeeSchedule.id IN :scheduleIds " +
            "ORDER BY d.date, t.startTime")
    List<EmployeeScheduleDay> findDaysWithTimeBlocksByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);
}









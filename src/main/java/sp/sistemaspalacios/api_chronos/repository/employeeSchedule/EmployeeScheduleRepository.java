package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;

import java.util.Date;
import java.util.List;

@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {

    @Query(value = """
            SELECT es.id, es.created_at, es.employee_id, es.end_date, 
                   es.shift_id, es.start_date, es.updated_at, es.days_parent_id
            FROM chronos.employee_schedules es 
            WHERE es.employee_id = :employeeId
            """, nativeQuery = true)
    List<EmployeeSchedule> findByEmployeeId(@Param("employeeId") Long employeeId);








    // Agregar estos métodos al EmployeeScheduleRepository

    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId = :employeeId " +
            "AND (es.endDate IS NULL OR es.endDate >= CURRENT_DATE)")
    List<EmployeeSchedule> findActiveByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT es FROM EmployeeSchedule es LEFT JOIN FETCH es.shift s LEFT JOIN FETCH s.shiftDetails " +
            "WHERE es.id IN :ids")
    List<EmployeeSchedule> findAllByIdWithShift(@Param("ids") List<Long> ids);

    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId = :employeeId " +
            "AND es.startDate <= :endDate AND (es.endDate IS NULL OR es.endDate >= :startDate)")
    List<EmployeeSchedule> findByEmployeeIdAndDateRange(
            @Param("employeeId") Long employeeId,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate);



    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employeeId IN :employeeIds")
    List<EmployeeSchedule> findByEmployeeIdIn(@Param("employeeIds") List<Long> employeeIds);

    @Query("SELECT es FROM EmployeeSchedule es LEFT JOIN FETCH es.shift s LEFT JOIN FETCH s.shiftDetails " +
            "LEFT JOIN FETCH es.days d LEFT JOIN FETCH d.timeBlocks " +
            "WHERE es.employeeId = :employeeId")
    List<EmployeeSchedule> findByEmployeeIdWithDetails(@Param("employeeId") Long employeeId);


    @Query("SELECT es FROM EmployeeSchedule es WHERE es.shift.id = :shiftId")
    List<EmployeeSchedule> findByShiftId(@Param("shiftId") Long shiftId);

    @Query("SELECT es FROM EmployeeSchedule es WHERE es.startDate >= :startDate AND es.endDate <= :endDate")
    List<EmployeeSchedule> findByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);




    @Query("SELECT d FROM EmployeeScheduleDay d " +
            "LEFT JOIN FETCH d.timeBlocks tb " +
            "LEFT JOIN FETCH d.employeeSchedule es " +
            "WHERE es.id IN :scheduleIds " +
            "ORDER BY d.date, tb.startTime")
    List<EmployeeScheduleDay> findDaysWithTimeBlocksByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);


    // Agregar este método en EmployeeScheduleRepository
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.shift.id IN :shiftIds")
    List<EmployeeSchedule> findByShiftIdIn(@Param("shiftIds") List<Long> shiftIds);




    @Query("""
  SELECT es FROM EmployeeSchedule es
  WHERE es.startDate <= :endDate
    AND (es.endDate IS NULL OR es.endDate >= :startDate)
""")
    List<EmployeeSchedule> findOverlapping(@Param("dependencyId") Long dependencyId, // puedes dejarlo o quitarlo
                                           @Param("startDate") java.time.LocalDate startDate,
                                           @Param("endDate")   java.time.LocalDate endDate);





}



package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeSchedule;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.EmployeeScheduleDay;

import java.time.LocalDate;
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
    List<EmployeeSchedule> findByShiftId(Long shiftId);

    // CONSULTA OPTIMIZADA PRINCIPAL - SIN JOINs innecesarios
    @Query("SELECT es FROM EmployeeSchedule es " +
            "WHERE es.shift.id IN (" +
            "  SELECT s.id FROM Shifts s WHERE s.dependencyId = :dependencyId" +
            ")")
    List<EmployeeSchedule> findByDependencyId(@Param("dependencyId") Long dependencyId);

    // CONSULTA ULTRA OPTIMIZADA - SOLO IDs
    @Query("SELECT es.id FROM EmployeeSchedule es " +
            "WHERE es.shift.id IN (" +
            "  SELECT s.id FROM Shifts s WHERE s.dependencyId = :dependencyId" +
            ")")
    List<Long> findIdsByDependencyId(@Param("dependencyId") Long dependencyId);

    // CONSULTAS CON FILTROS - OPTIMIZADAS
    @Query("SELECT es FROM EmployeeSchedule es " +
            "WHERE es.shift.id IN (" +
            "  SELECT s.id FROM Shifts s WHERE s.dependencyId = :dependencyId" +
            ") " +
            "AND es.shift.id = :shiftId")
    List<EmployeeSchedule> findByDependencyIdAndShiftId(
            @Param("dependencyId") Long dependencyId,
            @Param("shiftId") Long shiftId);

    @Query("SELECT es FROM EmployeeSchedule es " +
            "WHERE es.shift.id IN (" +
            "  SELECT s.id FROM Shifts s WHERE s.dependencyId = :dependencyId" +
            ") " +
            "AND (:startDate IS NULL OR es.startDate >= :startDate) " +
            "AND (:endDate IS NULL OR COALESCE(es.endDate, es.startDate) <= :endDate)")
    List<EmployeeSchedule> findByDependencyIdAndDateRangeNoTime(
            @Param("dependencyId") Long dependencyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // CONSULTA NATIVA SUPER RÁPIDA (usar esta cuando no necesites relaciones)
    @Query(value = """
        SELECT es.* 
        FROM employee_schedule es 
        INNER JOIN shifts s ON es.shift_id = s.id 
        WHERE s.dependency_id = :dependencyId
        """, nativeQuery = true)
    List<EmployeeSchedule> findByDependencyIdNative(@Param("dependencyId") Long dependencyId);

    // MÉTODOS AUXILIARES OPTIMIZADOS
    @Query("SELECT es FROM EmployeeSchedule es JOIN FETCH es.shift WHERE es.id IN :ids")
    List<EmployeeSchedule> findAllByIdWithShift(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT es FROM EmployeeSchedule es " +
            "LEFT JOIN FETCH es.days d " +
            "WHERE es.employeeId IN :employeeIds " +
            "ORDER BY es.id, d.date")
    List<EmployeeSchedule> findByEmployeeIdInWithDays(@Param("employeeIds") List<Long> employeeIds);

    @Query("SELECT d FROM EmployeeScheduleDay d " +
            "LEFT JOIN FETCH d.timeBlocks t " +
            "WHERE d.employeeSchedule.id IN :scheduleIds " +
            "ORDER BY d.date, t.startTime")
    List<EmployeeScheduleDay> findDaysWithTimeBlocksByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);

    // CONSULTAS DE FECHAS SIMPLES
    @Query("SELECT es FROM EmployeeSchedule es WHERE FUNCTION('DATE', es.startDate) >= :startDate AND FUNCTION('DATE', es.endDate) <= :endDate")
    List<EmployeeSchedule> findByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Query("SELECT e FROM EmployeeSchedule e WHERE e.startDate >= :startDate AND e.endDate IS NULL")
    List<EmployeeSchedule> findByStartDateAndNullEndDate(@Param("startDate") Date startDate);

    // ELIMINAR TODAS LAS CONSULTAS COMPLEJAS CON TIMEBLOCKS POR AHORA
    // Las consultas que hacen JOIN con timeBlocks son las que causan lentitud
}
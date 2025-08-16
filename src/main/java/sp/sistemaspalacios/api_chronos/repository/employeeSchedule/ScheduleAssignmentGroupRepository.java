package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.ScheduleAssignmentGroup;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleAssignmentGroupRepository extends JpaRepository<ScheduleAssignmentGroup, Long> {

    // Buscar grupos activos de un empleado
    List<ScheduleAssignmentGroup> findByEmployeeIdAndStatus(Long employeeId, String status);

    // Buscar todos los grupos de un empleado
    List<ScheduleAssignmentGroup> findByEmployeeId(Long employeeId);

    // Buscar grupos que se solapen con un rango de fechas
    @Query("SELECT sag FROM ScheduleAssignmentGroup sag " +
            "WHERE sag.employeeId = :employeeId " +
            "AND sag.status = 'ACTIVE' " +
            "AND ((sag.periodStart <= :endDate AND sag.periodEnd >= :startDate))")
    List<ScheduleAssignmentGroup> findOverlappingGroups(
            @Param("employeeId") Long employeeId,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate
    );

    // Buscar un grupo que contenga un employee_schedule específico
    @Query("SELECT sag FROM ScheduleAssignmentGroup sag " +
            "JOIN sag.employeeScheduleIds scheduleId " +
            "WHERE scheduleId = :scheduleId")
    Optional<ScheduleAssignmentGroup> findByEmployeeScheduleId(@Param("scheduleId") Long scheduleId);

    // Buscar grupos en un período específico
    @Query("SELECT sag FROM ScheduleAssignmentGroup sag " +
            "WHERE sag.periodStart >= :startDate " +
            "AND sag.periodEnd <= :endDate " +
            "ORDER BY sag.employeeId, sag.periodStart")
    List<ScheduleAssignmentGroup> findByPeriod(
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate
    );
}
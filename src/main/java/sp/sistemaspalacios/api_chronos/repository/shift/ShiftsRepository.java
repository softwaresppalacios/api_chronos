package sp.sistemaspalacios.api_chronos.repository.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;

import java.util.List;

@Repository
public interface ShiftsRepository extends JpaRepository<Shifts, Long> {
    List<Shifts> findByDependencyId(Long dependencyId);

    Shifts findByDependencyIdAndId(Long dependencyId, Long id);

    @Query(value = "SELECT DISTINCT s.id, s.name, s.description, s.dependency_id " +
            "FROM shifts s " +
            "INNER JOIN shift_details sd ON s.id = sd.shift_id " +
            "WHERE s.id IN ( " +
            "    SELECT shift_id FROM shift_details " +
            "    GROUP BY shift_id, day_of_week " +
            "    HAVING COUNT(*) > 1 " +
            ") " +
            "AND ( " +
            "    sd.hours_per_day != :dailyHours " +
            "    OR sd.break_minutes != :breakMinutes " +
            "    OR sd.night_hours_start != :nightStart " +
            "    OR sd.weekly_hours != :weeklyHours " +
            ") " +
            "GROUP BY s.id, s.name, s.description, s.dependency_id",
            nativeQuery = true)
    List<Object[]> findOutdatedMultipleJornadaShifts(
            @Param("dailyHours") String dailyHours,
            @Param("breakMinutes") int breakMinutes,
            @Param("nightStart") String nightStart,
            @Param("weeklyHours") String weeklyHours
    );

    /**
     * Encuentra IDs de turnos que tienen múltiples jornadas para el mismo día
     * (Sin importar si coinciden o no con la configuración)
     */
    @Query(value = "SELECT DISTINCT sd.shift_id " +
            "FROM shift_details sd " +
            "GROUP BY sd.shift_id, sd.day_of_week " +
            "HAVING COUNT(*) > 1",
            nativeQuery = true)
    List<Long> findShiftIdsWithMultipleJornadasPerDay();

}

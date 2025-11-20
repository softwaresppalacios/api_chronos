package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.HolidayExemption;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayExemptionRepository extends JpaRepository<HolidayExemption, Long> {

    // Buscar excepciones por empleado y fecha específica
    List<HolidayExemption> findByEmployeeIdAndHolidayDate(Long employeeId, LocalDate holidayDate);

    // Buscar todas las excepciones de un empleado
    List<HolidayExemption> findByEmployeeId(Long employeeId);

    // Verificar si existe una excepción para empleado y fecha
    boolean existsByEmployeeIdAndHolidayDate(Long employeeId, LocalDate holidayDate);


    @org.springframework.data.jpa.repository.Query(value = """
        SELECT he.*
        FROM chronos.holiday_exemptions he
        WHERE he.employee_id = :employeeId
          AND he.schedule_assignment_group_id IS NULL
          AND he.holiday_date BETWEEN to_date(:start, 'YYYY-MM-DD') AND to_date(:end, 'YYYY-MM-DD')
        """, nativeQuery = true)
    List<HolidayExemption> findPendingByEmployeeIdAndNullGroupBetweenDatesAsString(
            @org.springframework.data.repository.query.Param("employeeId") Long employeeId,
            @org.springframework.data.repository.query.Param("start") String start,
            @org.springframework.data.repository.query.Param("end") String end
    );
}
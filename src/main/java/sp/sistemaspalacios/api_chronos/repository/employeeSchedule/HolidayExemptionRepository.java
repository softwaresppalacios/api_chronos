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
}
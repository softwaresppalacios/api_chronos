package sp.sistemaspalacios.api_chronos.repository.employeeSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.OvertimeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface OvertimeTypeRepository extends JpaRepository<OvertimeType, Long> {

    // Buscar por código
    Optional<OvertimeType> findByCode(String code);

    // Buscar por código y que esté activo
    Optional<OvertimeType> findByCodeAndActiveTrue(String code);

    // Obtener todos los activos
    List<OvertimeType> findByActiveTrue();

    // Verificar si existe un código
    boolean existsByCode(String code);
}
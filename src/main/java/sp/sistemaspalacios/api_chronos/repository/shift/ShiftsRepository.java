package sp.sistemaspalacios.api_chronos.repository.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.shift.Shifts;

import java.util.List;

@Repository
public interface ShiftsRepository extends JpaRepository<Shifts, Long> {
    List<Shifts> findByDependencyId(Long dependencyId);

    Shifts findByDependencyIdAndId(Long dependencyId, Long id);

}

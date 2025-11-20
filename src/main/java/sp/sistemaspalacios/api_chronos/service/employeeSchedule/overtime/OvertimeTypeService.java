package sp.sistemaspalacios.api_chronos.service.employeeSchedule.overtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.overtime.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.OvertimeType;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.OvertimeTypeRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OvertimeTypeService {

    private final OvertimeTypeRepository repository;

    /** Obtener todos los tipos ACTIVOS */
    public List<OvertimeTypeDTO> getAllActiveTypes() {
        return repository.findByActiveTrue()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** Obtener por código (solo activos) */
    public OvertimeTypeDTO getByCode(String code) {
        return repository.findByCodeAndActiveTrue(code)
                .map(this::toDTO)
                .orElse(null);
    }



    /** Mapper Entity -> DTO */
    private OvertimeTypeDTO toDTO(OvertimeType entity) {
        return OvertimeTypeDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .displayName(entity.getDisplayName())
                .percentage(entity.getPercentage())
                .active(entity.isActive())
                .build();
    }
}

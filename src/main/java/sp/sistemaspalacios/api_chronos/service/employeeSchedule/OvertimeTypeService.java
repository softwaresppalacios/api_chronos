package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.OvertimeTypeDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.OvertimeType;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.OvertimeTypeRepository;

import java.math.BigDecimal;
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

    /** Obtener entity por código (solo activos) para uso interno */
    public OvertimeType getEntityByCode(String code) {
        return repository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de recargo no encontrado o inactivo: " + code));
    }

    /** Crear un nuevo tipo de recargo (operación explícita, NO automática) */
    @Transactional
    public OvertimeTypeDTO createNewType(String code, String displayName, BigDecimal percentage) {
        validateInputs(code, displayName, percentage);
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("Ya existe un tipo con el código: " + code);
        }
        OvertimeType type = OvertimeType.builder()
                .code(code)
                .displayName(displayName)
                .percentage(percentage)
                .active(true)
                .build();
        return toDTO(repository.save(type));
    }

    /** Activar/Desactivar un tipo existente (por código) */
    @Transactional
    public OvertimeTypeDTO toggleActive(String code) {
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));

        type.setActive(!type.isActive());
        return toDTO(repository.save(type));
    }

    /** Actualizar porcentaje por código */
    @Transactional
    public OvertimeTypeDTO updatePercentage(String code, BigDecimal newPercentage) {
        if (newPercentage == null || newPercentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El porcentaje no puede ser negativo ni nulo");
        }
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));

        type.setPercentage(newPercentage);
        return toDTO(repository.save(type));
    }

    /** Obtener TODOS los tipos (activos e inactivos) */
    public List<OvertimeTypeDTO> getAllTypes() {
        return repository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** Verificar existencia por código */
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    /** Eliminar tipo (sin restricción de "básicos") */
    @Transactional
    public void deleteType(String code) {
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));
        repository.delete(type);
    }

    /** Validaciones simples de entrada */
    private void validateInputs(String code, String displayName, BigDecimal percentage) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código es obligatorio");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("El nombre visible es obligatorio");
        }
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El porcentaje no puede ser negativo ni nulo");
        }
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

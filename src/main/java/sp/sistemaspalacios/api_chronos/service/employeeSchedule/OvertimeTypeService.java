package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import jakarta.annotation.PostConstruct;
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

    /**
     * Inicializar datos por defecto cuando arranca la aplicación
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultTypes() {
        // Solo crear si la tabla está vacía
        if (repository.count() == 0) {
            createDefaultTypes();
        }
    }

    /**
     * Crear tipos de recargo por defecto
     */
    private void createDefaultTypes() {
        // Valores según la ley colombiana
        saveIfNotExists("REGULAR_DIURNA", "Regular Diurna", new BigDecimal("0.00"));
        saveIfNotExists("REGULAR_NOCTURNA", "Regular Nocturna", new BigDecimal("0.35"));
        saveIfNotExists("EXTRA_DIURNA", "Extra Diurna", new BigDecimal("0.25"));
        saveIfNotExists("EXTRA_NOCTURNA", "Extra Nocturna", new BigDecimal("0.75"));
        saveIfNotExists("DOMINICAL_DIURNA", "Dominical Diurna", new BigDecimal("0.75"));
        saveIfNotExists("DOMINICAL_NOCTURNA", "Dominical Nocturna", new BigDecimal("1.10"));
        saveIfNotExists("FESTIVO_DIURNA", "Festivo Diurna", new BigDecimal("1.75"));
        saveIfNotExists("FESTIVO_NOCTURNA", "Festivo Nocturna", new BigDecimal("2.10"));
    }

    /**
     * Guardar si no existe
     */
    private void saveIfNotExists(String code, String displayName, BigDecimal percentage) {
        if (!repository.existsByCode(code)) {
            OvertimeType type = OvertimeType.builder()
                    .code(code)
                    .displayName(displayName)
                    .percentage(percentage)
                    .active(true)
                    .build();
            repository.save(type);
        }
    }

    /**
     * Obtener todos los tipos activos
     */
    public List<OvertimeTypeDTO> getAllActiveTypes() {
        return repository.findByActiveTrue()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtener por código
     */
    public OvertimeTypeDTO getByCode(String code) {
        return repository.findByCodeAndActiveTrue(code)
                .map(this::toDTO)
                .orElse(null);
    }

    /**
     * Obtener entity por código (para uso interno)
     */
    public OvertimeType getEntityByCode(String code) {
        return repository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de recargo no encontrado: " + code));
    }

    /**
     * Actualizar porcentaje
     */
    @Transactional
    public OvertimeTypeDTO updatePercentage(String code, BigDecimal newPercentage) {
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));

        type.setPercentage(newPercentage);
        return toDTO(repository.save(type));
    }

    /**
     * Convertir Entity a DTO
     */
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
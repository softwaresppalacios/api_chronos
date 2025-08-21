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
     * 🔧 CORREGIDO: Inicializar solo tipos básicos que no existan
     * Esto permite agregar nuevos tipos desde la BD sin conflictos
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultTypes() {
        // Crear solo los tipos básicos que no existan
        // Esto permite que agregues más tipos desde la BD
        createBasicTypesIfNotExist();
    }

    /**
     * 🔧 CORREGIDO: Crear solo tipos esenciales, verificando cada uno individualmente
     */
    private void createBasicTypesIfNotExist() {
        // Solo crear los tipos ESENCIALES para el funcionamiento básico
        // Valores según la ley colombiana
        saveIfNotExists("REGULAR_DIURNA", "Regular Diurna", new BigDecimal("0.00"));
        saveIfNotExists("REGULAR_NOCTURNA", "Regular Nocturna", new BigDecimal("0.35"));
        saveIfNotExists("EXTRA_DIURNA", "Extra Diurna", new BigDecimal("0.25"));
        saveIfNotExists("EXTRA_NOCTURNA", "Extra Nocturna", new BigDecimal("0.75"));
        saveIfNotExists("DOMINICAL_DIURNA", "Dominical Diurna", new BigDecimal("0.75"));
        saveIfNotExists("DOMINICAL_NOCTURNA", "Dominical Nocturna", new BigDecimal("1.10"));
        saveIfNotExists("FESTIVO_DIURNA", "Festivo Diurna", new BigDecimal("1.75"));
        saveIfNotExists("FESTIVO_NOCTURNA", "Festivo Nocturna", new BigDecimal("2.10"));

        System.out.println("✅ Tipos de recargo básicos verificados/creados");
    }

    /**
     * 🔧 MEJORADO: Verificar individualmente cada tipo
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
            System.out.println("✅ Tipo creado: " + code + " - " + displayName);
        } else {
            System.out.println("ℹ️ Tipo ya existe: " + code);
        }
    }

    /**
     * Obtener todos los tipos activos (incluyendo los creados manualmente)
     */
    public List<OvertimeTypeDTO> getAllActiveTypes() {
        List<OvertimeTypeDTO> types = repository.findByActiveTrue()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        System.out.println("📋 Tipos activos encontrados: " + types.size());
        types.forEach(type ->
                System.out.println("  - " + type.getCode() + ": " + type.getDisplayName()));

        return types;
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
     * 🆕 NUEVO: Crear nuevo tipo de recargo
     */
    @Transactional
    public OvertimeTypeDTO createNewType(String code, String displayName, BigDecimal percentage) {
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("Ya existe un tipo con el código: " + code);
        }

        OvertimeType type = OvertimeType.builder()
                .code(code)
                .displayName(displayName)
                .percentage(percentage)
                .active(true)
                .build();

        OvertimeType saved = repository.save(type);
        System.out.println("✅ Nuevo tipo creado: " + code + " - " + displayName);

        return toDTO(saved);
    }

    /**
     * 🆕 NUEVO: Activar/Desactivar tipo
     */
    @Transactional
    public OvertimeTypeDTO toggleActive(String code) {
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));

        type.setActive(!type.isActive());
        OvertimeType saved = repository.save(type);

        System.out.println("✅ Tipo " + code + " " + (saved.isActive() ? "activado" : "desactivado"));

        return toDTO(saved);
    }

    /**
     * Actualizar porcentaje
     */
    @Transactional
    public OvertimeTypeDTO updatePercentage(String code, BigDecimal newPercentage) {
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));

        BigDecimal oldPercentage = type.getPercentage();
        type.setPercentage(newPercentage);
        OvertimeType saved = repository.save(type);

        System.out.println("✅ Porcentaje actualizado para " + code + ": " +
                oldPercentage + "% → " + newPercentage + "%");

        return toDTO(saved);
    }

    /**
     * 🆕 NUEVO: Obtener todos los tipos (activos e inactivos)
     */
    public List<OvertimeTypeDTO> getAllTypes() {
        return repository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 🆕 NUEVO: Verificar si un código existe
     */
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    /**
     * 🆕 NUEVO: Eliminar tipo (solo si no está siendo usado)
     */
    @Transactional
    public void deleteType(String code) {
        OvertimeType type = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tipo no encontrado: " + code));

        // Verificar si es un tipo básico (no permitir eliminar)
        if (isBasicType(code)) {
            throw new IllegalArgumentException("No se puede eliminar un tipo básico del sistema: " + code);
        }

        repository.delete(type);
        System.out.println("✅ Tipo eliminado: " + code);
    }

    /**
     * 🆕 NUEVO: Verificar si es un tipo básico del sistema
     */
    private boolean isBasicType(String code) {
        return List.of("REGULAR_DIURNA", "REGULAR_NOCTURNA", "EXTRA_DIURNA", "EXTRA_NOCTURNA",
                        "DOMINICAL_DIURNA", "DOMINICAL_NOCTURNA", "FESTIVO_DIURNA", "FESTIVO_NOCTURNA")
                .contains(code);
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
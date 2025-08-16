package sp.sistemaspalacios.api_chronos.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeTypeDTO {
    private Long id;
    private String code;         // p.ej. "EXTRA_NOCTURNA"
    private String displayName;  // p.ej. "Extra con jornada nocturna"
    private BigDecimal percentage; // p.ej. 0.75
    private boolean active;
}
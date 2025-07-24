package sp.sistemaspalacios.api_chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoursPerDayDTO {
    private Long id;
    private Integer hoursPerDay;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
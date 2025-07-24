package sp.sistemaspalacios.api_chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NightHoursDTO {
    private Long id;
    private LocalTime startNight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
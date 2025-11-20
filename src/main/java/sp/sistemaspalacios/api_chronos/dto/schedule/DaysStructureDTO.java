package sp.sistemaspalacios.api_chronos.dto.schedule;

import lombok.Data;
import sp.sistemaspalacios.api_chronos.dto.schedule.DayDTO;

import java.util.List;

@Data
public class DaysStructureDTO {
    private Long id;
    private List<DayDTO> items;
}
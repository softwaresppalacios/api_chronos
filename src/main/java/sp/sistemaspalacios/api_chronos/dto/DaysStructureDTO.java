package sp.sistemaspalacios.api_chronos.dto;

import lombok.Data;

import java.util.List;

@Data
public class DaysStructureDTO {
    private Long id;
    private List<DayDTO> items;
}
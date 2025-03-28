package sp.sistemaspalacios.api_chronos.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DayDTO {
    private Long id;
    private Date date;
    private Integer dayOfWeek;
    private List<TimeBlockDTO> timeBlocks;
}
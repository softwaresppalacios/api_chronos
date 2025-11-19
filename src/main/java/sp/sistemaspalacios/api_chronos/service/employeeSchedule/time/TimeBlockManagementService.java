package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleDayRepository;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.EmployeeScheduleTimeBlockRepository;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeScheduleService;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeBlockManagementService {

    private final EmployeeScheduleTimeBlockRepository timeBlockRepository;
    private final EmployeeScheduleDayRepository employeeScheduleDayRepository;
    private final EmployeeScheduleService employeeScheduleService;


}
package sp.sistemaspalacios.api_chronos.service.employeeSchedule.time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.service.employeeSchedule.core.EmployeeScheduleService;

import java.text.SimpleDateFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleResponseService {

    private final EmployeeScheduleService employeeScheduleService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");




}
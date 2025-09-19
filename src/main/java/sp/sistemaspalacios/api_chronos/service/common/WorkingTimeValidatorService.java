package sp.sistemaspalacios.api_chronos.service.common;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkingTimeValidatorService {

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() { return new ValidationResult(true, List.of()); }
        public static ValidationResult fail(List<String> errs) { return new ValidationResult(false, errs); }
    }

    private final TimeService timeService;
    private final WorkingTimeCalculatorService calculator;

    public WorkingTimeValidatorService(TimeService timeService,
                                       WorkingTimeCalculatorService calculator) {
        this.timeService = timeService;
        this.calculator = calculator;
    }


    /** Validación de solapes para una lista de intervalos. */
    public ValidationResult validateNoOverlap(List<WorkingTimeCalculatorService.Interval> intervals) {
        List<String> errors = new ArrayList<>();
        if (intervals == null || intervals.size() <= 1) return ValidationResult.ok();

        for (int i = 0; i < intervals.size(); i++) {
            for (int j = i + 1; j < intervals.size(); j++) {
                var a = intervals.get(i);
                var b = intervals.get(j);
                if (calculator.overlaps(a, b)) {
                    errors.add("Solape entre " + fmt(a) + " y " + fmt(b));
                }
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private String fmt(WorkingTimeCalculatorService.Interval it) {
        return timeService.to12h(timeService.format(it.start())) + " - " +
                timeService.to12h(timeService.format(it.end()));
    }

}

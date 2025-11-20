package sp.sistemaspalacios.api_chronos.service.common;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;

@Service
public class WorkingTimeCalculatorService {

    // ÚNICA definición del record
    public record Interval(LocalTime start, LocalTime end) {}


    /** Duración de un intervalo soportando cruce de medianoche. */
    public Duration duration(LocalTime start, LocalTime end) {
        int s = start.getHour() * 60 + start.getMinute();
        int e = end.getHour() * 60 + end.getMinute();
        int diff = e - s;
        if (diff <= 0) diff += 24 * 60;
        return Duration.ofMinutes(diff);
    }


    public boolean overlaps(Interval a, Interval b) {
        int a1 = a.start().getHour() * 60 + a.start().getMinute();
        int a2 = a.end().getHour() * 60 + a.end().getMinute();
        int b1 = b.start().getHour() * 60 + b.start().getMinute();
        int b2 = b.end().getHour() * 60 + b.end().getMinute();
        if (a2 <= a1) a2 += 24 * 60;
        if (b2 <= b1) b2 += 24 * 60;
        return !(a2 <= b1 || b2 <= a1);
    }


}

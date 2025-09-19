package sp.sistemaspalacios.api_chronos.service.common;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class TimeService {

    // Acepta 24h: "HH:mm"
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    // Acepta 12h: "hh:mm a" (AM/PM con o sin espacios, mayúsc/minúsc)
    private static final DateTimeFormatter F12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);

    private static final int MINUTES_PER_DAY = 24 * 60;

    /** Convierte LocalTime a minutos desde 00:00. */
    public int toMinutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    public Duration minutesToDuration(long minutes) {
        if (minutes < 0) minutes = 0;
        return Duration.ofMinutes(minutes);
    }

    public int normalizeMinutes(int minutes) {
        int m = minutes % MINUTES_PER_DAY;
        return m < 0 ? m + MINUTES_PER_DAY : m;
    }

    /** Parsea "HH:mm" estrictamente. */
    public LocalTime parse(String hhmm) {
        if (hhmm == null) throw new IllegalArgumentException("Hora nula");
        try {
            return LocalTime.parse(hhmm.trim(), HH_MM);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Hora inválida (HH:mm): " + hhmm);
        }
    }

    /** Formatea a "HH:mm". */
    public String format(LocalTime t) {
        return (t == null) ? null : t.format(HH_MM);
    }

    /** Date -> LocalDate. */
    public LocalDate toLocalDate(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Normaliza cualquier entrada (12h o 24h, con/ sin espacio en AM/PM)
     * a "HH:mm" para guardar en BD.
     */
    public String normalizeTimeForDatabase(String input) {
        if (input == null || input.isBlank()) return null;  // Cambiar de "" a null
        try {
            LocalTime t = parseAny(input);
            return format(t);
        } catch (Exception e) {
            System.err.println("Error parsing time '" + input + "': " + e.getMessage());
            return null;  // En caso de error, devolver null en lugar de fallar
        }
    }
    /** Calcula horas entre dos tiempos (acepta 12h o 24h, soporta cruce medianoche). */
    public double calculateHoursBetween(String start, String end) {
        LocalTime s = parseAny(start);
        LocalTime e = parseAny(end);
        int sm = toMinutes(s);
        int em = toMinutes(e);
        int diff = em - sm;
        if (diff <= 0) diff += 24 * 60;
        return diff / 60.0;
    }

    /** ¿Solapan dos rangos? (acepta 12h/24h, soporta medianoche). */
    public boolean timeOverlaps(String s1, String e1, String s2, String e2) {
        LocalTime a1 = parseAny(s1), b1 = parseAny(e1);
        LocalTime a2 = parseAny(s2), b2 = parseAny(e2);
        int sA = toMinutes(a1), eA = toMinutes(b1);
        int sB = toMinutes(a2), eB = toMinutes(b2);
        if (eA <= sA) eA += 24 * 60;
        if (eB <= sB) eB += 24 * 60;
        return !(eA <= sB || eB <= sA);
    }


    public LocalTime parseAny(String raw) {
        if (raw == null) throw new IllegalArgumentException("Hora nula");

        String s = raw.trim().toUpperCase(Locale.US);

        // Aceptar también sin espacio entre hora y AM/PM (p.ej. "07:00AM")
        if (s.matches("^\\d{1,2}:\\d{2}(AM|PM)$")) {
            // inserta un espacio antes de AM/PM para cumplir "hh:mm a"
            s = s.replaceAll("(AM|PM)$", " $1");
        }

        // Intenta 12h primero si trae AM/PM
        if (s.contains("AM") || s.contains("PM")) {
            return LocalTime.parse(s, F12);
        }

        // Si no trae AM/PM, asume 24h
        return LocalTime.parse(s, HH_MM);
    }

    /** Formatea "HH:mm" a "hh:mm AM/PM". */
    public String to12h(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return "";
        LocalTime t = parse(hhmm);
        return t.format(F12).toUpperCase(Locale.US);
    }
}

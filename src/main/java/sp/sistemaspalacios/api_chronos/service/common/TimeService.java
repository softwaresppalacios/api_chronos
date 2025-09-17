package sp.sistemaspalacios.api_chronos.service.common;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.regex.Pattern;

@Service
public class TimeService {

    private static final Pattern MILITARY_TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):([0-5][0-9])$");
    private static final Pattern AMPM_TIME_PATTERN = Pattern.compile("^([0]?[1-9]|1[0-2]):[0-5][0-9]\\s*(AM|PM)$");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ===== CONVERSIÓN DE FECHAS =====

    public LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) return ((java.sql.Date) date).toLocalDate();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public Date toDate(LocalDate localDate) {
        if (localDate == null) return null;
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public String formatDate(Date date) {
        return date != null ? toLocalDate(date).format(DATE_FMT) : null;
    }

    // ===== CONVERSIÓN DE TIEMPO =====

    public String normalizeTimeForDatabase(String time) {
        if (time == null) return "00:00:00";
        String t = time.trim();
        if (t.matches("^\\d{2}:\\d{2}:\\d{2}$")) return t;        // HH:mm:ss
        if (t.matches("^\\d{1}:\\d{2}$")) return "0" + t + ":00"; // H:mm -> 0H:mm:00
        if (t.matches("^\\d{2}:\\d{2}$")) return t + ":00";       // HH:mm -> HH:mm:00
        if (t.matches("^\\d{2}$"))        return t + ":00:00";    // HH -> HH:00:00
        return "00:00:00";
    }

    public String convertAmPmTo24h(String time12h) {
        if (time12h == null || time12h.trim().isEmpty()) {
            throw new IllegalArgumentException("Tiempo no puede estar vacío");
        }

        String cleaned = time12h.trim().toUpperCase().replaceAll("\\s+", " ");

        if (!AMPM_TIME_PATTERN.matcher(cleaned).matches()) {
            // Si ya está en formato 24h, retornar tal como está
            if (MILITARY_TIME_PATTERN.matcher(cleaned).matches()) {
                return cleaned;
            }
            throw new IllegalArgumentException("Formato inválido: " + time12h);
        }

        String[] parts = cleaned.split("\\s+");
        String timePart = parts[0];
        String amPm = parts[1];

        String[] timeParts = timePart.split(":");
        int hours = Integer.parseInt(timeParts[0]);
        int minutes = Integer.parseInt(timeParts[1]);

        if ("AM".equals(amPm) && hours == 12) {
            hours = 0;
        } else if ("PM".equals(amPm) && hours != 12) {
            hours += 12;
        }

        return String.format("%02d:%02d", hours, minutes);
    }

    public String convert24hToAmPm(String time24h) {
        if (!MILITARY_TIME_PATTERN.matcher(time24h).matches()) {
            throw new IllegalArgumentException("Formato 24h inválido: " + time24h);
        }

        LocalTime time = LocalTime.parse(time24h);
        return time.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }

    // ===== CÁLCULOS DE TIEMPO =====

    public int toMinutes(String timeStr) {
        String normalized = normalizeTimeForDatabase(timeStr);
        String[] parts = normalized.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    public double calculateHoursBetween(String startTime, String endTime) {
        try {
            String start24h = convertAmPmTo24h(startTime);
            String end24h = convertAmPmTo24h(endTime);

            LocalTime start = LocalTime.parse(start24h);
            LocalTime end = LocalTime.parse(end24h);

            long minutes = calculateMinutesBetween(start, end);
            return minutes / 60.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public long calculateMinutesBetween(LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            // Cruza medianoche
            return ChronoUnit.MINUTES.between(start, LocalTime.MAX) +
                    ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
        } else {
            return ChronoUnit.MINUTES.between(start, end);
        }
    }

    // ===== VALIDACIONES =====

    public boolean isValidMilitaryTime(String time) {
        return time != null && MILITARY_TIME_PATTERN.matcher(time).matches();
    }

    public boolean timeOverlaps(String s1, String e1, String s2, String e2) {
        try {
            String ns1 = normalizeTimeForDatabase(s1);
            String ne1 = normalizeTimeForDatabase(e1);
            String ns2 = normalizeTimeForDatabase(s2);
            String ne2 = normalizeTimeForDatabase(e2);

            int a1 = toMinutes(ns1), a2 = toMinutes(ne1);
            int b1 = toMinutes(ns2), b2 = toMinutes(ne2);

            if (a2 <= a1) a2 += 1440; // soporta cruce nocturno
            if (b2 <= b1) b2 += 1440;

            return a1 < b2 && b1 < a2;
        } catch (Exception ex) {
            return false;
        }
    }
}
package app.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/** Formatos cortos para tablas y tarjetas (C14): fechas sin segundos ni nanosegundos y duraciones legibles. */
public final class Formats {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formats() {
    }

    /** 18/11/2026 14:05; cadena vacia si no hay fecha. */
    public static String shortDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    /** 18/11/2026; cadena vacia si no hay fecha. */
    public static String shortDate(TemporalAccessor value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return DATE.format(ldt.toLocalDate());
        return DATE.format(LocalDate.from(value));
    }

    /** 45 s, 3 min 20 s, 1 h 05 min. */
    public static String duration(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        if (s < 60) return s + " s";
        if (s < 3600) return (s / 60) + " min " + String.format("%02d", s % 60) + " s";
        return (s / 3600) + " h " + String.format("%02d", (s % 3600) / 60) + " min";
    }
}

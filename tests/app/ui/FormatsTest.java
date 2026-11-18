package app.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** C14: fechas cortas, duraciones y notas legibles. */
class FormatsTest {
    @Test
    void fechaCortaSinSegundosNiNanosegundos() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 18, 14, 5, 33, 123_456_789);
        assertEquals("18/11/2026 14:05", Formats.shortDateTime(t));
        assertEquals("18/11/2026", Formats.shortDate(t));
        assertEquals("", Formats.shortDateTime(null));
        assertEquals("", Formats.shortDate(null));
    }

    @Test
    void duraciones() {
        assertEquals("0 s", Formats.duration(-3));
        assertEquals("45 s", Formats.duration(44.6));
        assertEquals("3 min 20 s", Formats.duration(200));
        assertEquals("1 h 05 min", Formats.duration(3900));
    }

    @Test
    void notaConUnDecimal() {
        assertEquals("4.2", Formats.score(4.2));
        assertEquals("5.0", Formats.score(5.0));
        assertEquals("3.3", Formats.score(3.3333333));
        assertEquals("", Formats.score(null));
    }
}

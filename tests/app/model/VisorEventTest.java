package app.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** C05: cada linea del visor se valida con un analizador JSON real. */
class VisorEventTest {
    private static final UUID S = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void seleccionValida() {
        String line = "{\"evento\": \"seleccion\", \"t\": 1763467200.125, \"objeto_id\": \"ABD_HIGADO\", "
                + "\"ficha_id\": \"ABD_HIGADO\", \"origen\": \"clic\", \"capa\": 1, \"modelo\": \"abdominal\"}";
        VisorEvent e = assertInstanceOf(VisorEvent.Valid.class, VisorEvent.parse(line, S, NOW)).event();
        assertEquals("seleccion", e.event());
        assertEquals("ABD_HIGADO", e.objectId());
        assertEquals("ABD_HIGADO", e.fichaId());
        assertEquals("clic", e.origin());
        assertEquals("abdominal", e.model());
        assertEquals(Instant.ofEpochMilli(1763467200125L), e.emittedAt());
        assertEquals(S, e.session());
        assertEquals(line.length(), e.bytes());
    }

    @Test
    void inicioYCierreSinTUsanLaHoraDeLlegada() {
        VisorEvent e = assertInstanceOf(VisorEvent.Valid.class,
                VisorEvent.parse("{\"evento\":\"cierre\",\"modelo\":\"inguinal\",\"duracion_s\":3.5}", S, NOW)).event();
        assertEquals(NOW, e.emittedAt());
        assertInstanceOf(VisorEvent.Valid.class, VisorEvent.parse("{\"evento\":\"inicio\",\"objetos\":25}", S, NOW));
    }

    @Test
    void cuentaBytesEnUtf8() {
        String line = "{\"evento\":\"inicio\",\"modelo\":\"torácica\"}";
        VisorEvent e = assertInstanceOf(VisorEvent.Valid.class, VisorEvent.parse(line, S, NOW)).event();
        assertEquals(line.length() + 1, e.bytes(), "la a con tilde ocupa 2 bytes en UTF-8");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"evento\": \"seleccion\"",                              // JSON cortado (cierre abrupto)
            "{'evento': 'inicio'}",                                     // comillas simples
            "{\"evento\": \"inicio\"} {\"evento\": \"cierre\"}",        // dos objetos en una linea
            "{\"evento\": \"borrar_todo\"}",                            // evento desconocido
            "{\"evento\": 3}",                                          // tipo incorrecto
            "{\"t\": 1.0}",                                             // sin evento
            "{\"evento\": \"seleccion\", \"objeto_id\": \"X\"}",        // seleccion sin ficha
            "{\"evento\": \"seleccion\", \"objeto_id\": \"\", \"ficha_id\": \"F\"}",
            "{\"evento\": \"inicio\", \"t\": \"ayer\"}",                // t no numerico
            "{\"evento\": \"inicio\", \"t\": -5}",
            "{\"evento\": \"inicio\", \"modelo\": \"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}",
    })
    void lineasDeEventoMalFormadasSeDescartan(String line) {
        assertInstanceOf(VisorEvent.Malformed.class, VisorEvent.parse(line, S, NOW), line);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Warning: In vtkOpenGLRenderWindow", "[1, 2, 3]", "Traceback (most recent call last):"})
    void lineasVaciasYTextoNormalNoSonEventos(String line) {
        assertInstanceOf(VisorEvent.PlainOutput.class, VisorEvent.parse(line, S, NOW), line);
    }

    @Test
    void nullEsSalidaVacia() {
        assertInstanceOf(VisorEvent.PlainOutput.class, VisorEvent.parse(null, S, NOW));
    }
}

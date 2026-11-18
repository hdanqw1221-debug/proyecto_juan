package app.model;

import app.util.JsonSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Evento del visor 3D: una linea JSON de su salida estandar (C05). Ejemplo:
 * {"evento": "seleccion", "t": 1763460000.123, "objeto_id": "ABD_HIGADO", "ficha_id": "ABD_HIGADO",
 * "origen": "clic", "capa": 1, "modelo": "abdominal"}
 *
 * @param bytes tamano de la linea en UTF-8 (para las pruebas del canal, T06)
 */
public record VisorEvent(UUID session, String event, String model, String objectId, String fichaId, String origin,
                         Instant emittedAt, int bytes) {

    public static final Set<String> EVENTS = Set.of("inicio", "seleccion", "cierre");

    /** Resultado de analizar una linea: evento valido, salida normal del proceso o linea de evento mal formada. */
    public sealed interface Line permits Valid, PlainOutput, Malformed {
    }

    public record Valid(VisorEvent event) implements Line {
    }

    public record PlainOutput(String text) implements Line {
    }

    public record Malformed(String text, String reason) implements Line {
    }

    /**
     * Clasifica una linea de la salida del visor. Las lineas que empiezan por "{" se tratan como eventos y se
     * validan con un analizador JSON estricto; el resto (avisos de VTK, trazas) es salida normal.
     *
     * @param now instante a usar si el evento no trae el campo "t"
     */
    public static Line parse(String line, UUID session, Instant now) {
        if (line == null || line.isBlank()) {
            return new PlainOutput(line == null ? "" : line);
        }
        String trimmed = line.strip();
        if (!trimmed.startsWith("{")) {
            return new PlainOutput(line);
        }
        JsonElement root;
        try {
            root = JsonSupport.parseStrict(trimmed);
        } catch (IllegalArgumentException e) {
            return new Malformed(line, e.getMessage());
        }
        if (!root.isJsonObject()) {
            return new Malformed(line, "no es un objeto JSON");
        }
        JsonObject o = root.getAsJsonObject();
        String event = text(o, "evento");
        if (event == null || !EVENTS.contains(event)) {
            return new Malformed(line, "campo 'evento' ausente o desconocido");
        }
        String objectId = text(o, "objeto_id");
        String fichaId = text(o, "ficha_id");
        if (event.equals("seleccion") && (isBlank(objectId) || isBlank(fichaId))) {
            return new Malformed(line, "una seleccion requiere 'objeto_id' y 'ficha_id'");
        }
        if (tooLong(objectId) || tooLong(fichaId) || tooLong(text(o, "modelo")) || tooLong(text(o, "origen"))) {
            return new Malformed(line, "algun identificador supera 60 caracteres");
        }
        Instant emitted = now;
        JsonElement t = o.get("t");
        if (t != null && !t.isJsonNull()) {
            if (!JsonSupport.isNumber(t)) {
                return new Malformed(line, "'t' debe ser un numero (segundos desde 1970)");
            }
            double seconds = t.getAsDouble();
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 32503680000d) {
                return new Malformed(line, "'t' fuera de rango");
            }
            long millis = Math.round(seconds * 1000d);
            emitted = Instant.ofEpochMilli(millis);
        }
        return new Valid(new VisorEvent(session, event, text(o, "modelo"), objectId, fichaId, text(o, "origen"),
                emitted, line.getBytes(StandardCharsets.UTF_8).length));
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return JsonSupport.isString(e) ? e.getAsString() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean tooLong(String s) {
        return s != null && s.length() > 60;
    }
}

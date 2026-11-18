package app.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;

/**
 * Analisis JSON estricto (RFC 8259) con Gson. Sustituye a las expresiones regulares que se usaban para leer la
 * configuracion de las practicas (C06) y los eventos del visor 3D (C05).
 */
public final class JsonSupport {
    private JsonSupport() {
    }

    /**
     * Analiza un documento JSON completo en modo estricto: rechaza comillas simples, claves sin comillas, comas
     * finales, comentarios y cualquier contenido despues del valor raiz.
     *
     * @throws IllegalArgumentException si el texto no es JSON valido
     */
    public static JsonElement parseStrict(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto JSON esta vacio");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Hay contenido despues del valor JSON");
            }
            return root;
        } catch (JsonParseException | IOException | IllegalStateException e) {
            String detail = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
            throw new IllegalArgumentException("JSON no valido: " + detail, e);
        }
    }

    /** Analiza un objeto JSON; devuelve null si el texto no es un objeto JSON valido. */
    public static JsonObject parseObjectOrNull(String text) {
        try {
            JsonElement e = parseStrict(text);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Busca una clave en el objeto y, si no esta, en sus objetos anidados (primero en profundidad). */
    public static JsonElement find(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        if (object.has(key)) {
            return object.get(key);
        }
        for (var entry : object.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                JsonElement found = find(entry.getValue().getAsJsonObject(), key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    public static boolean isBoolean(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
    }

    public static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }
}

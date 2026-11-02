package app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Edits the visible practice fields while retaining any additional JSON settings. */
public final class SimulationConfig {
    private final LinkedHashMap<String, String> values;

    private SimulationConfig(LinkedHashMap<String, String> values) {
        this.values = values;
    }

    public static SimulationConfig fromJson(String json) {
        return new SimulationConfig(parseObject(json));
    }

    public String string(String key, String fallback) {
        String value = values.get(key);
        return value != null && value.startsWith("\"") ? decode(value) : fallback;
    }

    public int integer(String key, int fallback) {
        try {
            return Integer.parseInt(values.get(key));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean bool(String key, boolean fallback) {
        String value = values.get(key);
        return "true".equals(value) ? true : "false".equals(value) ? false : fallback;
    }

    public List<String> strings(String key) {
        String raw = values.get(key);
        if (raw == null || !raw.strip().startsWith("[")) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : splitValues(raw.strip(), '[', ']')) {
            if (!item.isBlank()) result.add(decode(item));
        }
        return result;
    }

    public String scenario(String key, String fallback) {
        String raw = values.get("escenario");
        if (raw == null || !raw.strip().startsWith("{")) return fallback;
        String value = parseObject(raw).get(key);
        return value != null && value.startsWith("\"") ? decode(value) : fallback;
    }

    public void putString(String key, String value) {
        values.put(key, quote(value));
    }

    public void putInt(String key, int value) {
        values.put(key, Integer.toString(value));
    }

    public void putBoolean(String key, boolean value) {
        values.put(key, Boolean.toString(value));
    }

    public void putStrings(String key, List<String> items) {
        values.put(key, "[" + String.join(", ", items.stream().map(SimulationConfig::quote).toList()) + "]");
    }

    public void putScenario(String title, String description) {
        String raw = values.get("escenario");
        LinkedHashMap<String, String> scenario = raw != null && raw.strip().startsWith("{")
                ? parseObject(raw) : new LinkedHashMap<>();
        scenario.put("titulo", quote(title));
        scenario.put("descripcion", quote(description));
        values.put("escenario", encodeObject(scenario));
    }

    public String toJson() {
        return encodeObject(values);
    }

    private static LinkedHashMap<String, String> parseObject(String json) {
        if (json == null) throw new IllegalArgumentException("La configuracion de la practica esta vacia.");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String member : splitValues(json.strip(), '{', '}')) {
            if (member.isBlank()) continue;
            int colon = delimiter(member, ':');
            if (colon < 0) throw new IllegalArgumentException("La configuracion de la practica no es valida.");
            String key = member.substring(0, colon).strip();
            String value = member.substring(colon + 1).strip();
            if (!key.startsWith("\"") || !key.endsWith("\"") || value.isEmpty()) {
                throw new IllegalArgumentException("La configuracion de la practica no es valida.");
            }
            result.put(decode(key), value);
        }
        return result;
    }

    private static List<String> splitValues(String raw, char open, char close) {
        if (raw.length() < 2 || raw.charAt(0) != open || raw.charAt(raw.length() - 1) != close) {
            throw new IllegalArgumentException("La configuracion de la practica no es valida.");
        }
        List<String> result = new ArrayList<>();
        String body = raw.substring(1, raw.length() - 1);
        int start = 0, depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && quoted) { escaped = true; continue; }
            if (c == '"') { quoted = !quoted; continue; }
            if (quoted) continue;
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                result.add(body.substring(start, i).strip());
                start = i + 1;
            }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("La configuracion de la practica no es valida.");
        result.add(body.substring(start).strip());
        return result;
    }

    private static int delimiter(String text, char wanted) {
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && quoted) { escaped = true; continue; }
            if (c == '"') { quoted = !quoted; continue; }
            if (c == wanted && !quoted) return i;
        }
        return -1;
    }

    private static String encodeObject(Map<String, String> entries) {
        List<String> parts = new ArrayList<>();
        entries.forEach((key, value) -> parts.add(quote(key) + ": " + value));
        return "{" + String.join(", ", parts) + "}";
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 32 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        return out.append('"').toString();
    }

    private static String decode(String raw) {
        String value = raw.strip();
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw new IllegalArgumentException("La configuracion de la practica no es valida.");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (c != '\\') { out.append(c); continue; }
            if (++i >= value.length() - 1) throw new IllegalArgumentException("Texto JSON incompleto.");
            char escape = value.charAt(i);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 >= value.length()) throw new IllegalArgumentException("Unicode JSON incompleto.");
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Texto JSON no valido.");
            }
        }
        return out.toString();
    }
}

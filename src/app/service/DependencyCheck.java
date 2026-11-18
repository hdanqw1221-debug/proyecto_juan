package app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprobacion de las versiones de las dependencias Python del visor 3D contra {@code requeriments.txt} (C10).
 * Las versiones mas recientes de VTK y PyQt5 pueden fallar; se prueba con las del archivo de requisitos y, si el
 * equipo tiene otras, el mensaje dice cual es la diferencia y como instalar las probadas.
 *
 * Es logica pura (sin procesos): las versiones instaladas se le pasan, de modo que se prueba con versiones simuladas.
 */
public final class DependencyCheck {
    /** Paquetes cuya version se exige para abrir el visor (los demas del archivo se ignoran). */
    public static final List<String> REQUIRED = List.of("PyQt5", "vtk");

    private static final Pattern LINE = Pattern.compile(
            "^\\s*([A-Za-z0-9_.\\-]+)(?:\\[[^]]*])?\\s*([<>=!~][^;#]*)?(?:;\\s*([^#]*))?(?:#.*)?$");
    private static final Pattern MARKER = Pattern.compile("python_version\\s*(<=|>=|==|!=|<|>)\\s*[\"']([0-9.]+)[\"']");

    private DependencyCheck() {
    }

    /** Requisitos aplicables a la version de Python indicada (por ejemplo "3.14.4"): nombre (minusculas) -> condicion. */
    public static Map<String, String> parseRequirements(List<String> lines, String pythonVersion) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String marker = m.group(3);
            if (marker != null && !marker.isBlank() && !markerApplies(marker, pythonVersion)) {
                continue;
            }
            String spec = m.group(2) == null ? "" : m.group(2).replaceAll("\\s+", "");
            result.put(m.group(1).toLowerCase(Locale.ROOT), spec);
        }
        return result;
    }

    private static boolean markerApplies(String marker, String pythonVersion) {
        Matcher m = MARKER.matcher(marker);
        if (!m.find()) {
            return true;  // marcador que no entendemos: se aplica el requisito
        }
        // python_version solo compara mayor.minor
        int[] have = firstTwo(pythonVersion);
        int[] want = firstTwo(m.group(2));
        int c = compare(have, want);
        return switch (m.group(1)) {
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "==" -> c == 0;
            default -> c != 0;
        };
    }

    private static int[] firstTwo(String v) {
        int[] parts = parse(v);
        return new int[]{parts.length > 0 ? parts[0] : 0, parts.length > 1 ? parts[1] : 0};
    }

    /** Problemas encontrados (lista vacia si todo coincide). Una version ausente (null) cuenta como paquete no instalado. */
    public static List<String> check(Map<String, String> requirements, Map<String, String> installed) {
        List<String> problems = new ArrayList<>();
        for (String name : REQUIRED) {
            String spec = requirements.get(name.toLowerCase(Locale.ROOT));
            if (spec == null) {
                continue;  // el archivo no lo exige para esta version de Python
            }
            String version = installed.get(name);
            if (version == null || version.isBlank()) {
                problems.add(name + " no esta instalado (se necesita " + describe(spec) + ")");
            } else if (!satisfies(version, spec)) {
                problems.add(name + " " + version + " no cumple " + describe(spec));
            }
        }
        return problems;
    }

    /** Mensaje para el usuario: que version hay, cual se probo y como instalarla. */
    public static String message(List<String> problems, String requirementsFile) {
        return "Las versiones de las bibliotecas del visor 3D no son las probadas:\n- " + String.join("\n- ", problems)
                + "\nInstala las versiones indicadas en " + requirementsFile
                + " (python -m pip install -r requeriments.txt). Las versiones mas recientes de VTK y PyQt5 pueden fallar.";
    }

    public static boolean satisfies(String version, String spec) {
        if (spec == null || spec.isBlank()) {
            return true;
        }
        int[] v = parse(version);
        for (String clause : spec.split(",")) {
            Matcher m = Pattern.compile("^(==|>=|<=|!=|~=|<|>)(.+)$").matcher(clause.strip());
            if (!m.matches()) {
                continue;
            }
            int[] wanted = parse(m.group(2));
            int c = compare(v, wanted);
            boolean ok = switch (m.group(1)) {
                case "==" -> c == 0;
                case ">=" -> c >= 0;
                case "<=" -> c <= 0;
                case "<" -> c < 0;
                case ">" -> c > 0;
                case "!=" -> c != 0;
                default -> c >= 0 && v.length > 0 && wanted.length > 0 && v[0] == wanted[0];  // ~=
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String describe(String spec) {
        return spec.isBlank() ? "cualquier version" : spec.replace(",", " y ");
    }

    /** "5.15.11" -> [5, 15, 11]; ignora sufijos como "rc1" o "+cu12". */
    static int[] parse(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String piece : version.strip().split("\\.")) {
            Matcher m = Pattern.compile("^(\\d+)").matcher(piece);
            if (!m.find()) {
                break;
            }
            parts.add(Integer.parseInt(m.group(1)));
        }
        return parts.stream().mapToInt(Integer::intValue).toArray();
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** Salida del sondeo "py=3.14.4;PyQt5=5.15.11;vtk=9.6.2" -> mapa (los paquetes que faltan no aparecen). */
    public static Map<String, String> parseProbeOutput(String output) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : output.split("\\R")) {
            if (!line.startsWith("VERSIONES:")) {
                continue;
            }
            for (String pair : line.substring("VERSIONES:".length()).split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && !pair.substring(eq + 1).isBlank() && !pair.substring(eq + 1).equals("NO")) {
                    result.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
                }
            }
        }
        return result;
    }

    /** Codigo Python que imprime las versiones instaladas (cada importacion por separado: una que falla no oculta las demas). */
    public static final String PROBE_CODE = String.join("\n",
            "import sys",
            "v = {'py': '.'.join(map(str, sys.version_info[:3]))}",
            "try:",
            "    from PyQt5.QtCore import PYQT_VERSION_STR; v['PyQt5'] = PYQT_VERSION_STR",
            "except Exception: v['PyQt5'] = 'NO'",
            "try:",
            "    import vtk; v['vtk'] = vtk.vtkVersion.GetVTKVersion()",
            "except Exception: v['vtk'] = 'NO'",
            "print('VERSIONES:' + ';'.join(k + '=' + x for k, x in v.items()))");
}

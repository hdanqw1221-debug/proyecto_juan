package app.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Causa de que el visor 3D no se abra o se cierre mal, y mensaje para el usuario (C13). Antes el mensaje era siempre
 * «verifica la aceleracion grafica», aunque faltara Python, una biblioteca o los archivos del modelo.
 *
 * Las reglas son logica pura sobre los datos de la ejecucion (codigo de salida, salida de consola, eventos recibidos),
 * de modo que cada rama se prueba sin abrir ninguna ventana.
 */
public final class VisorDiagnostico {
    public enum Causa {
        PYTHON_NO_ENCONTRADO,
        DEPENDENCIAS_FALTANTES,
        VERSIONES_INCORRECTAS,
        ARCHIVOS_AUSENTES,
        ERROR_AL_LANZAR,
        CIERRE_ANORMAL
    }

    public record Diagnostico(Causa causa, String titulo, String mensaje) {
    }

    /** Archivos que el visor necesita ademas de su script, relativos a la carpeta de los visores. */
    static final List<String> ARCHIVOS_DEL_MODELO = List.of(
            Visor3DService.MANIFIESTO,
            "cavidades/modelos/obj",
            "cavidades/datos/fichas_anatomicas.json");

    private static final Pattern MODULO = Pattern.compile("(?:ModuleNotFoundError|ImportError): No module named '([^']+)'");
    private static final Pattern GRAFICOS = Pattern.compile(
            "(?i)(opengl|\\bwgl|\\bglx\\b|glsl|shader|graphics|render ?window|could not (create|initiali[sz]e).*(context|window)|display)");
    private static final int LINEAS_DE_SALIDA = 6;

    private VisorDiagnostico() {
    }

    // ------------------------------------------------------------------ antes de abrir
    /** Archivos del modelo que faltan (vacio si esta todo). */
    public static List<String> archivosAusentes(Path script, Path carpeta) {
        List<String> faltan = new ArrayList<>();
        if (script == null || !Files.isRegularFile(script)) {
            faltan.add(script == null ? "script del visor" : script.getFileName().toString());
        }
        if (carpeta != null) {
            for (String relativo : ARCHIVOS_DEL_MODELO) {
                if (!Files.exists(carpeta.resolve(relativo))) {
                    faltan.add(relativo);
                }
            }
        }
        return faltan;
    }

    public static Diagnostico archivosAusentes(List<String> faltan) {
        return new Diagnostico(Causa.ARCHIVOS_AUSENTES, "Archivos del modelo no encontrados",
                "Faltan archivos del modelo 3D en este equipo:\n- " + String.join("\n- ", faltan)
                        + "\nReinstala la aplicacion o restaura la carpeta aplicacion_tres_d.");
    }

    /**
     * El Python con PyQt5, VTK y numpy no esta disponible: distingue que no haya ningun Python de que haya uno al que
     * le faltan bibliotecas.
     */
    public static Diagnostico sinEntornoPython(boolean hayAlgunPython, String bibliotecasFaltantes) {
        if (!hayAlgunPython) {
            return new Diagnostico(Causa.PYTHON_NO_ENCONTRADO, "Python no encontrado",
                    "No se encontro Python en este equipo (ni el entorno .venv del proyecto, ni el runtime del paquete, "
                            + "ni uno instalado en el sistema).\nInstala Python 3.14 o pide al administrador que use la opcion "
                            + "«Instalar dependencias».");
        }
        String que = bibliotecasFaltantes == null || bibliotecasFaltantes.isBlank() ? "PyQt5, VTK o numpy" : bibliotecasFaltantes;
        return new Diagnostico(Causa.DEPENDENCIAS_FALTANTES, "Faltan bibliotecas de Python",
                "Hay Python, pero no tiene todas las bibliotecas del visor 3D (" + que + ").\n"
                        + "Instalalas con: python -m pip install -r requeriments.txt (o con la opcion «Instalar dependencias»).");
    }

    // ------------------------------------------------------------------ despues de ejecutar
    /**
     * Diagnostico de una ejecucion que no termino bien.
     *
     * @param codigo   codigo de salida del proceso (-1 si no llego a lanzarse)
     * @param salida   ultimas lineas de la consola del visor
     * @param abierto  el visor aviso de que su ventana se abrio (evento "inicio")
     * @param cerrado  el visor aviso de su cierre normal (evento "cierre")
     * @param error    excepcion al lanzar o al leer el proceso, si la hubo
     * @param registro carpeta donde el visor escribe su registro de fallos (faulthandler), o null
     */
    public static Diagnostico deEjecucion(int codigo, String salida, boolean abierto, boolean cerrado,
                                          Exception error, Path registro) {
        String texto = salida == null ? "" : salida;
        if (error instanceof IllegalStateException e && e.getMessage() != null
                && e.getMessage().startsWith("Las versiones de las bibliotecas")) {
            return new Diagnostico(Causa.VERSIONES_INCORRECTAS, "Versiones no probadas", e.getMessage());
        }
        Matcher modulo = MODULO.matcher(texto);
        if (modulo.find()) {
            return new Diagnostico(Causa.DEPENDENCIAS_FALTANTES, "Falta una biblioteca de Python",
                    "El visor no pudo importar la biblioteca «" + modulo.group(1) + "».\n"
                            + "Instala las dependencias con: python -m pip install -r requeriments.txt "
                            + "(o con la opcion «Instalar dependencias»).");
        }
        if (error != null) {
            return new Diagnostico(Causa.ERROR_AL_LANZAR, "No se pudo iniciar el visor",
                    "No se pudo iniciar el proceso del visor: " + error.getMessage());
        }

        StringBuilder m = new StringBuilder();
        m.append(abierto ? "El visor se cerro de forma inesperada durante la sesion"
                : "El visor no llego a abrir su ventana");
        m.append(" (codigo de salida ").append(codigo).append(descripcionDelCodigo(codigo)).append(").");
        boolean violacion = (codigo & 0xFFFFFFFFL) == 0xC0000005L;
        if (violacion || GRAFICOS.matcher(texto).find()) {
            m.append("\nPuede deberse a los graficos: actualiza el controlador de la tarjeta de video o prueba en un equipo "
                    + "con aceleracion grafica.");
        }
        String ultimas = ultimasLineas(texto);
        if (!ultimas.isBlank()) {
            m.append("\n\nUltimas lineas del visor:\n").append(ultimas);
        }
        if (registro != null) {
            m.append("\n\nRegistro de fallos: ").append(registro).append(java.io.File.separator).append("visor_<modelo>.log");
        }
        return new Diagnostico(Causa.CIERRE_ANORMAL, abierto ? "El visor se cerro" : "El visor no se abrio", m.toString());
    }

    static String descripcionDelCodigo(int codigo) {
        long sinSigno = codigo & 0xFFFFFFFFL;
        String hex = " = 0x" + Long.toHexString(sinSigno).toUpperCase(Locale.ROOT);
        if (sinSigno == 0xC0000005L) return hex + ", violacion de acceso a memoria";
        if (sinSigno == 0xC0000374L) return hex + ", corrupcion del monton";
        if (sinSigno == 0xC00000FDL) return hex + ", desbordamiento de pila";
        if (sinSigno == 0xC0000409L) return hex + ", fallo de seguridad de pila";
        if (codigo < 0 || sinSigno > 0xFFFF) return hex;
        return "";
    }

    private static String ultimasLineas(String salida) {
        List<String> lineas = new ArrayList<>();
        for (String linea : salida.split("\\R")) {
            if (!linea.isBlank()) {
                lineas.add(linea.strip());
            }
        }
        int desde = Math.max(0, lineas.size() - LINEAS_DE_SALIDA);
        return String.join("\n", lineas.subList(desde, lineas.size()));
    }

    // ------------------------------------------------------------------ con acceso al equipo
    /** Diagnostico previo a abrir el visor (archivos que faltan), o null si esta todo. */
    public static Diagnostico previo(Path script) {
        List<String> faltan = archivosAusentes(script, Visor3DService.carpetaModelos());
        return faltan.isEmpty() ? null : archivosAusentes(faltan);
    }

    /** Diagnostico cuando {@link Visor3DService#python()} no encontro un Python listo. */
    public static Diagnostico sinPython() {
        boolean hayAlguno = false;
        for (List<String> candidato : Visor3DService.candidatosPython()) {
            if (Visor3DService.ejecutaCodigo(candidato, "pass")) {
                hayAlguno = true;
                break;
            }
        }
        return sinEntornoPython(hayAlguno, null);
    }

    public static Diagnostico deResultado(Visor3DService.Resultado r) {
        return deEjecucion(r.codigoSalida(), r.salida(), r.abierto(), r.cerrado(), r.error(), null);
    }
}

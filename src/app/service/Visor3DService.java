package app.service;

import app.model.VisorEvent;
import app.util.JsonSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Modelos 3D de las cavidades del equino: visores Python de aplicacion_tres_d/aplicacion_tres_d.
 *
 * Es el unico punto que conoce el catalogo de modelos, la carpeta donde estan, el Python con el
 * que se ejecutan (entorno .venv, runtime embebido del paquete o Python del sistema) y los eventos
 * JSON que el visor escribe por la salida estandar ("inicio", "seleccion" y "cierre").
 */
public final class Visor3DService {
    /** Escena compartida por los cuatro visores (sin ella no se puede abrir ninguno). */
    public static final String MANIFIESTO = "cavidades/modelos/manifiesto.json";

    public record Modelo(String script, String titulo, String descripcion, String objetivo, String escenario) {
    }

    public static final List<Modelo> MODELOS = List.of(
            new Modelo("cavidad_toracica.py", "Cavidad toracica",
                    "Desde lejos ves la cavidad toracica completa; al acercarte se separa en pulmones, corazon, "
                            + "traquea, grandes vasos y diafragma. Haz clic en una pieza para ver su ficha.",
                    "Reconocer pulmones y corazon junto con grandes vasos y diafragma",
                    "La cavidad toracica se separa en sus componentes al acercarse."),
            new Modelo("cavidad_abdominal.py", "Cavidad abdominal",
                    "Al acercarte la cavidad abdominal se separa en tubo digestivo, higado, bazo, pancreas, "
                            + "rinones y vejiga. Haz clic en una pieza para ver su ficha.",
                    "Reconocer el tubo digestivo con sus glandulas anexas y el aparato urinario",
                    "La cavidad abdominal se separa en sus componentes al acercarse."),
            new Modelo("region_inguinal.py", "Region inguinal",
                    "Pared inguinal y escroto: al acercarte se separan los anillos inguinales, el cordon "
                            + "espermatico, el testiculo y el epididimo. Haz clic en una pieza para ver su ficha.",
                    "Reconocer el conducto inguinal y el cordon espermatico hasta el testiculo",
                    "La region inguinal y el escroto se separan en sus componentes al acercarse."),
            new Modelo("cavidades_equinas.py", "Cavidades del equino (vista general)",
                    "Las tres cavidades a la vez, con filtros por cavidad y la ficha anatomica de cada estructura.",
                    "Identificar las visceras de las cavidades toracica y abdominal y de la region inguinal",
                    "Exploracion de las visceras del equino con seleccion por clic y ficha anatomica.")
    );

    private static final String[] CARPETAS = {
            "app/aplicacion_tres_d/aplicacion_tres_d",
            "aplicacion_tres_d/aplicacion_tres_d",
            "aplicacion_tres_d"
    };
    private static final String DEPENDENCIAS = "import PyQt5, vtk, numpy";
    private static final long ESPERA_SONDEO_S = 25;  // la primera importacion de VTK puede tardar
    private static final int LINEAS_SALIDA = 40;

    private static volatile List<String> pythonDetectado;

    private Visor3DService() {
    }

    // ------------------------------------------------------------------ catalogo
    public static Modelo modelo(String script) {
        if (script == null) {
            return null;
        }
        String nombre = Path.of(script.trim()).getFileName().toString();
        for (Modelo m : MODELOS) {
            if (m.script().equalsIgnoreCase(nombre)) {
                return m;
            }
        }
        return null;
    }

    /** Titulo para mostrar; para un script ajeno al catalogo, su nombre sin extension. */
    public static String titulo(String script) {
        Modelo m = modelo(script);
        if (m != null) {
            return m.titulo();
        }
        if (script == null || script.isBlank()) {
            return "Sin modelo 3D";
        }
        String nombre = Path.of(script.trim()).getFileName().toString().replaceFirst("(?i)\\.py$", "").replace('_', ' ');
        return nombre.isEmpty() ? script : Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }

    // ------------------------------------------------------------------ carpeta
    /** Carpeta de los visores: la que tiene la escena de las cavidades (o, si falta, la primera que exista). */
    public static Path carpetaModelos() {
        Path primera = null;
        for (Path raiz : raices()) {
            for (String candidata : CARPETAS) {
                Path carpeta = raiz.resolve(candidata).normalize();
                if (Files.isRegularFile(carpeta.resolve(MANIFIESTO))) {
                    return carpeta;
                }
                if (primera == null && Files.isDirectory(carpeta)) {
                    primera = carpeta;
                }
            }
        }
        return primera;
    }

    /** Modelos del catalogo presentes en la carpeta (vacio si falta la escena compartida). */
    public static List<Modelo> disponibles(Path carpeta) {
        if (carpeta == null || !Files.isRegularFile(carpeta.resolve(MANIFIESTO))) {
            return List.of();
        }
        return MODELOS.stream().filter(m -> Files.isRegularFile(carpeta.resolve(m.script()))).toList();
    }

    private static List<Path> raices() {
        List<Path> raices = new ArrayList<>();
        raices.add(Path.of(System.getProperty("user.dir")));
        String lanzador = System.getProperty("jpackage.app-path");
        if (lanzador != null && !lanzador.isBlank() && Path.of(lanzador).getParent() != null) {
            raices.add(Path.of(lanzador).getParent());
        }
        return raices;
    }

    // ------------------------------------------------------------------ Python
    /** Comandos Python candidatos: primero los del proyecto o del paquete, despues los del sistema. */
    public static List<List<String>> candidatosPython() {
        Set<Path> propios = new LinkedHashSet<>();
        Path carpeta = carpetaModelos();
        if (carpeta != null) {
            propios.add(carpeta.resolve(".venv").resolve("Scripts").resolve("python.exe").normalize());
        }
        for (Path raiz : raices()) {
            propios.add(raiz.resolve("python-runtime").resolve("python.exe").normalize());
            propios.add(raiz.resolve("app").resolve("python-runtime").resolve("python.exe").normalize());
        }
        List<List<String>> candidatos = new ArrayList<>();
        for (Path python : propios) {
            if (Files.isRegularFile(python)) {
                candidatos.add(List.of(python.toString()));
            }
        }
        candidatos.add(List.of("py", "-3"));
        candidatos.add(List.of("python"));
        candidatos.add(List.of("python3"));
        return candidatos;
    }

    /** Python con PyQt5, VTK y numpy (se recuerda tras la primera deteccion), o null. */
    public static synchronized List<String> python() {
        if (pythonDetectado != null && sigueExistiendo(pythonDetectado)) {
            return pythonDetectado;
        }
        pythonDetectado = null;
        for (List<String> candidato : candidatosPython()) {
            if (ejecutaCodigo(candidato, DEPENDENCIAS)) {
                pythonDetectado = candidato;
                break;
            }
        }
        return pythonDetectado;
    }

    /** Hay un Python del proyecto o del paquete (.venv o runtime embebido); comprobacion rapida, sin ejecutarlo. */
    public static boolean hayPythonPropio() {
        return candidatosPython().stream().anyMatch(c -> c.get(0).contains("\\") || c.get(0).contains("/"));
    }

    /** Python con la biblioteca estandar completa, para crear el entorno .venv e instalar dependencias. */
    public static List<String> pythonParaInstalar() {
        for (List<String> candidato : candidatosPython()) {
            if (ejecutaCodigo(candidato,
                    "import pathlib, sys; raise SystemExit(0 if pathlib.Path(sys.base_prefix, 'Lib').is_dir() else 1)")) {
                return candidato;
            }
        }
        return null;
    }

    /** Vuelve a detectar el Python la proxima vez (por ejemplo, tras instalar dependencias). */
    public static synchronized void olvidarPython() {
        pythonDetectado = null;
    }

    public static boolean ejecutaCodigo(List<String> python, String codigo) {
        List<String> sondeo = new ArrayList<>(python);
        sondeo.addAll(List.of("-c", codigo));
        try {
            Process proceso = new ProcessBuilder(sondeo).redirectErrorStream(true).start();
            proceso.getOutputStream().close();
            if (!proceso.waitFor(ESPERA_SONDEO_S, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                return false;
            }
            return proceso.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean sigueExistiendo(List<String> python) {
        String exe = python.get(0);
        return !(exe.contains("\\") || exe.contains("/")) || Files.isRegularFile(Path.of(exe));
    }

    /** El visor es una ventana: con pythonw.exe no se abre ademas una consola. */
    static List<String> sinConsola(List<String> python) {
        String exe = python.get(0);
        if (exe.toLowerCase(Locale.ROOT).endsWith("python.exe")) {
            Path pythonw = Path.of(exe).resolveSibling("pythonw.exe");
            if (Files.isRegularFile(pythonw)) {
                List<String> comando = new ArrayList<>(python);
                comando.set(0, pythonw.toString());
                return comando;
            }
        }
        return python;
    }

    // ------------------------------------------------------------------ ejecucion
    /**
     * Resultado de una sesion en el visor.
     *
     * @param cerrado el visor aviso de su cierre normal (evento "cierre"); si despues el interprete falla al
     *                liberar memoria, la sesion sigue siendo correcta para el usuario.
     */
    public record Resultado(int codigoSalida, String salida, Set<String> estructuras, long duracionMs,
                            boolean abierto, boolean cerrado, Exception error,
                            UUID sesion, int eventosValidos, int eventosInvalidos) {
        public boolean correcto() {
            return error == null && (codigoSalida == 0 || cerrado);
        }
    }

    /**
     * Abre el visor y espera a que se cierre; se llama desde un hilo de fondo.
     *
     * @param alAbrir se ejecuta cuando el visor avisa de que su ventana ya esta abierta (evento "inicio").
     */
    public static Resultado ejecutar(Path script, List<String> python, Runnable alAbrir) {
        return ejecutar(script, python, alAbrir, null);
    }

    /**
     * Abre el visor y entrega cada evento valido a {@code alEvento} en cuanto llega (C05), para guardarlo sin
     * esperar al cierre. Cada linea que empieza por "{" se valida con un analizador JSON estricto; las mal
     * formadas no se entregan: se cuentan y se conservan en la salida para el diagnostico.
     */
    public static Resultado ejecutar(Path script, List<String> python, Runnable alAbrir, Consumer<VisorEvent> alEvento) {
        List<String> comando = new ArrayList<>(sinConsola(python));
        comando.add(script.getFileName().toString());
        return ejecutarComando(comando, script.getParent(), alAbrir, alEvento);
    }

    /** Ejecuta un comando que se comporta como el visor (tambien lo usan las pruebas del canal de eventos). */
    public static Resultado ejecutarComando(List<String> comando, Path carpeta, Runnable alAbrir, Consumer<VisorEvent> alEvento) {
        UUID sesion = UUID.randomUUID();
        long inicio = System.currentTimeMillis();
        Deque<String> salida = new ArrayDeque<>();
        Set<String> estructuras = new LinkedHashSet<>();
        boolean abierto = false;
        boolean cerrado = false;
        int validos = 0;
        int invalidos = 0;
        try {
            ProcessBuilder pb = new ProcessBuilder(comando).redirectErrorStream(true);
            if (carpeta != null) {
                pb.directory(carpeta.toFile());
            }
            Process proceso = pb.start();
            proceso.getOutputStream().close();
            try (BufferedReader lector = new BufferedReader(
                    new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    VisorEvent.Line clasificada = VisorEvent.parse(linea, sesion, Instant.now());
                    if (clasificada instanceof VisorEvent.Valid v) {
                        validos++;
                        VisorEvent evento = v.event();
                        switch (evento.event()) {
                            case "inicio" -> {
                                abierto = true;
                                if (alAbrir != null) {
                                    alAbrir.run();
                                }
                            }
                            case "cierre" -> cerrado = true;
                            case "seleccion" -> estructuras.add(evento.fichaId());
                            default -> { }
                        }
                        if (alEvento != null) {
                            alEvento.accept(evento);
                        }
                    } else {
                        String texto = clasificada instanceof VisorEvent.Malformed m
                                ? "[evento descartado: " + m.reason() + "] " + m.text()
                                : ((VisorEvent.PlainOutput) clasificada).text();
                        if (clasificada instanceof VisorEvent.Malformed) {
                            invalidos++;
                        }
                        if (clasificada instanceof VisorEvent.Malformed || !texto.isBlank()) {
                            salida.addLast(texto);
                            if (salida.size() > LINEAS_SALIDA) {
                                salida.removeFirst();
                            }
                        }
                    }
                }
            }
            return new Resultado(proceso.waitFor(), String.join("\n", salida), estructuras,
                    System.currentTimeMillis() - inicio, abierto, cerrado, null, sesion, validos, invalidos);
        } catch (Exception ex) {
            return new Resultado(-1, String.join("\n", salida), estructuras, System.currentTimeMillis() - inicio,
                    abierto, cerrado, ex, sesion, validos, invalidos);
        }
    }

    /** Detalle para el registro de auditoria de una sesion. */
    public static String resumen(Path script, Resultado resultado) {
        String nombre = script.getFileName().toString();
        long minutos = Math.round(resultado.duracionMs() / 60000.0);
        StringBuilder texto = new StringBuilder(titulo(nombre)).append(" (").append(nombre).append("): ")
                .append(resultado.estructuras().size()).append(" estructura(s) consultada(s) en ")
                .append(minutos < 1 ? "menos de 1 min" : minutos + " min");
        if (!resultado.estructuras().isEmpty()) {
            texto.append(" - ").append(String.join(", ", resultado.estructuras()));
        }
        if (!resultado.correcto()) {
            texto.append(" - cerrado con error (codigo ").append(resultado.codigoSalida()).append(")");
        }
        return texto.toString();
    }

    /** Campo de texto de una linea JSON del visor (analizador estricto); null si no existe o no es JSON. */
    static String campo(String json, String clave) {
        JsonObject o = JsonSupport.parseObjectOrNull(json);
        JsonElement e = o == null ? null : o.get(clave);
        return JsonSupport.isString(e) ? e.getAsString() : null;
    }
}

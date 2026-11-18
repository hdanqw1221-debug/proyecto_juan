package app.service;

import app.model.RoleType;
import app.session.SessionManager;
import app.security.ServiceGuard;
import app.dao.SimulationDao;
import app.dao.jdbc.SimulationDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Simulation;

import app.util.JsonSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SimulationService {
    private final SimulationDao simulationDao;
    /** Duracion maxima admitida para una practica, en minutos. */
    static final int MAX_MINUTES = 600;

    private final ServiceGuard guard;

    /** Sin sesion: los metodos de escritura se deniegan. */
    public SimulationService(ConnectionFactory factory) {
        this(factory, null);
    }

    public SimulationService(ConnectionFactory factory, SessionManager sessionManager) {
        this.simulationDao = new SimulationDaoJdbc(factory);
        this.guard = ServiceGuard.of(sessionManager);
    }

    public long createSimulation(long courseId, String name, String description, String configJson) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        validateConfigJson(configJson);
        Simulation sim = new Simulation();
        sim.setCourseId(courseId);
        sim.setName(name);
        sim.setDescription(description);
        sim.setConfigJson(configJson);
        return simulationDao.create(sim);
    }

    public List<Simulation> listByCourse(long courseId) {
        return simulationDao.listByCourse(courseId);
    }

    public List<Simulation> listEnabledByCourse(long courseId) {
        return simulationDao.listByCourse(courseId).stream()
                .filter(SimulationService::isEnabled)
                .toList();
    }

    public Simulation findById(long simulationId) {
        return simulationDao.findById(simulationId);
    }

    public void updateDetails(long simulationId, String name, String description, String configJson) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        validateConfigJson(configJson);
        simulationDao.updateDetails(simulationId, name, description, configJson);
    }

    public void updateEnabled(long simulationId, boolean enabled) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        simulationDao.updateEnabled(simulationId, enabled);
    }

    public void deleteSimulation(long simulationId) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        simulationDao.delete(simulationId);
    }

    public static String getScript3D(Simulation simulation) {
        if (simulation == null || simulation.getConfigJson() == null) {
            return null;
        }
        String script = readString(simulation.getConfigJson(), "script3d", "");
        return script.isBlank() ? null : script.trim();
    }

    public static boolean isEnabled(Simulation simulation) {
        JsonObject config = simulation == null ? null : JsonSupport.parseObjectOrNull(simulation.getConfigJson());
        JsonElement enabled = config == null ? null : config.get("habilitado");
        return JsonSupport.isBoolean(enabled) && enabled.getAsBoolean();
    }

    public static String defaultConfigJson() {
        return defaultConfigJson(Visor3DService.MODELOS.get(0).script());
    }

    /** Plantilla de configuracion para una practica con el modelo 3D indicado. */
    public static String defaultConfigJson(String script3d) {
        Visor3DService.Modelo modelo = Visor3DService.modelo(script3d);
        if (modelo == null) {
            modelo = Visor3DService.MODELOS.get(0);
        }
        String explorar = modelo.script().equals("cavidades_equinas.py")
                ? "Muestra u oculta cada cavidad con las teclas 1 a 5"
                : "Acercate con la rueda del raton hacia la estructura que quieras ver: la cavidad se separa en sus componentes";
        return """
                {
                  "habilitado": true,
                  "tema": "cavidades_equinas",
                  "nivel": "intermedio",
                  "script3d": "%s",
                  "tiempoMinutos": 45,
                  "objetivos": [
                    "%s",
                    "Relacionar cada estructura con su topografia y proyeccion superficial"
                  ],
                  "instrucciones": [
                    "Inicia el visor 3D de la cavidad",
                    "%s",
                    "Haz clic en una estructura para ver su ficha completa"
                  ],
                  "escenario": {
                    "titulo": "%s",
                    "descripcion": "%s"
                  }
                }
                """.formatted(modelo.script(), modelo.objetivo(), explorar, modelo.titulo(), modelo.escenario());
    }

    /**
     * Crea en el curso una practica por cada modelo 3D de las cavidades que todavia no tenga.
     *
     * @return numero de practicas creadas
     */
    public int addCavityPractices(long courseId) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        List<String> existing = listByCourse(courseId).stream()
                .map(SimulationService::getScript3D)
                .filter(script -> script != null)
                .map(script -> script.toLowerCase(java.util.Locale.ROOT))
                .toList();
        int created = 0;
        for (Visor3DService.Modelo modelo : Visor3DService.MODELOS) {
            if (!existing.contains(modelo.script().toLowerCase(java.util.Locale.ROOT))) {
                createSimulation(courseId, "Practica 3D: " + modelo.titulo(), modelo.escenario(),
                        defaultConfigJson(modelo.script()));
                created++;
            }
        }
        return created;
    }

    /**
     * Valida la configuracion de una practica con un analizador JSON estricto (C06): tipos, campos obligatorios y
     * que el modelo 3D exista en {@link Visor3DService#MODELOS}. Reune todos los problemas en un solo mensaje.
     *
     * @throws IllegalArgumentException si la configuracion no es valida
     */
    public static void validateConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("La configuracion JSON de la simulacion es obligatoria");
        }
        JsonElement root = JsonSupport.parseStrict(configJson);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("La configuracion de la simulacion debe ser un objeto JSON");
        }
        JsonObject config = root.getAsJsonObject();
        List<String> errors = new ArrayList<>();

        if (!JsonSupport.isBoolean(config.get("habilitado"))) {
            errors.add("'habilitado' es obligatorio y debe ser true o false");
        }
        requireText(config, "tema", errors);
        requireText(config, "nivel", errors);

        JsonElement minutes = config.get("tiempoMinutos");
        if (!JsonSupport.isNumber(minutes)) {
            errors.add("'tiempoMinutos' es obligatorio y debe ser un numero entero");
        } else {
            double value = minutes.getAsDouble();
            if (value != Math.rint(value) || value < 1 || value > MAX_MINUTES) {
                errors.add("'tiempoMinutos' debe ser un entero entre 1 y " + MAX_MINUTES);
            }
        }
        requireTextArray(config, "objetivos", errors);
        requireTextArray(config, "instrucciones", errors);

        JsonElement script = config.get("script3d");
        if (script != null && !script.isJsonNull()) {
            if (!JsonSupport.isString(script)) {
                errors.add("'script3d' debe ser texto");
            } else if (!script.getAsString().isBlank()) {
                String name = script.getAsString().strip();
                if (Visor3DService.modelo(name) == null || name.contains("/") || name.contains("\\")) {
                    errors.add("'script3d' debe ser uno de los modelos 3D del catalogo: "
                            + String.join(", ", Visor3DService.MODELOS.stream().map(Visor3DService.Modelo::script).toList()));
                }
            }
        }

        JsonElement scenario = config.get("escenario");
        if (scenario != null && !scenario.isJsonNull()) {
            if (!scenario.isJsonObject()) {
                errors.add("'escenario' debe ser un objeto con 'titulo' y 'descripcion'");
            } else {
                for (String key : List.of("titulo", "descripcion")) {
                    JsonElement value = scenario.getAsJsonObject().get(key);
                    if (value != null && !JsonSupport.isString(value)) {
                        errors.add("'escenario." + key + "' debe ser texto");
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("La configuracion de la simulacion no es valida:\n- " + String.join("\n- ", errors));
        }
    }

    private static void requireText(JsonObject config, String key, List<String> errors) {
        JsonElement value = config.get(key);
        if (!JsonSupport.isString(value) || value.getAsString().isBlank()) {
            errors.add("'" + key + "' es obligatorio y debe ser un texto no vacio");
        }
    }

    private static void requireTextArray(JsonObject config, String key, List<String> errors) {
        JsonElement value = config.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().isEmpty()) {
            errors.add("'" + key + "' es obligatorio y debe ser una lista con al menos un elemento");
            return;
        }
        for (JsonElement item : value.getAsJsonArray()) {
            if (!JsonSupport.isString(item) || item.getAsString().isBlank()) {
                errors.add("cada elemento de '" + key + "' debe ser un texto no vacio");
                return;
            }
        }
    }

    public static String summarizeSimulation(Simulation simulation) {
        StringBuilder summary = new StringBuilder();
        summary.append("Simulacion: ").append(simulation.getName()).append("\n");
        String script3d = getScript3D(simulation);
        if (script3d != null) {
            summary.append("Modelo 3D: ").append(script3d).append("\n");
        }
        summary.append("Tema: ").append(readString(simulation.getConfigJson(), "tema", "No definido")).append("\n");
        summary.append("Nivel: ").append(readString(simulation.getConfigJson(), "nivel", "No definido")).append("\n");
        summary.append("Duracion: ").append(readInt(simulation.getConfigJson(), "tiempoMinutos", 0)).append(" minutos").append("\n");
        summary.append("Objetivos:\n");
        for (String item : readArray(simulation.getConfigJson(), "objetivos")) {
            summary.append("- ").append(item).append("\n");
        }
        summary.append("Instrucciones:\n");
        for (String item : readArray(simulation.getConfigJson(), "instrucciones")) {
            summary.append("- ").append(item).append("\n");
        }
        summary.append("Escenario: ").append(readString(simulation.getConfigJson(), "titulo", "Sin titulo")).append("\n");
        summary.append(readString(simulation.getConfigJson(), "descripcion", "Sin descripcion"));
        return summary.toString();
    }

    /** Valor de texto de la clave (o de la primera clave igual en un objeto anidado); fallback si no existe. */
    public static String readString(String json, String key, String fallback) {
        JsonElement value = JsonSupport.find(JsonSupport.parseObjectOrNull(json), key);
        return JsonSupport.isString(value) ? value.getAsString() : fallback;
    }

    public static int readInt(String json, String key, int fallback) {
        JsonElement value = JsonSupport.find(JsonSupport.parseObjectOrNull(json), key);
        try {
            return JsonSupport.isNumber(value) ? value.getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static List<String> readArray(String json, String key) {
        JsonElement value = JsonSupport.find(JsonSupport.parseObjectOrNull(json), key);
        List<String> items = new ArrayList<>();
        if (value != null && value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (JsonSupport.isString(item)) {
                    items.add(item.getAsString());
                }
            }
        }
        return items;
    }
}

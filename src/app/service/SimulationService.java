package app.service;

import app.dao.SimulationDao;
import app.dao.jdbc.SimulationDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimulationService {
    private final SimulationDao simulationDao;
    private static final Pattern STRING_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
    private static final Pattern ARRAY_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

    public SimulationService(ConnectionFactory factory) {
        this.simulationDao = new SimulationDaoJdbc(factory);
    }

    public long createSimulation(long courseId, String name, String description, String configJson) {
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
        validateConfigJson(configJson);
        simulationDao.updateDetails(simulationId, name, description, configJson);
    }

    public void updateEnabled(long simulationId, boolean enabled) {
        simulationDao.updateEnabled(simulationId, enabled);
    }

    public void deleteSimulation(long simulationId) {
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
        String config = simulation.getConfigJson();
        if (config == null || config.isBlank()) {
            return false;
        }
        String normalized = config.replace(" ", "").toLowerCase();
        return normalized.contains("\"habilitado\":true");
    }

    public static String defaultConfigJson() {
        return """
                {
                  "habilitado": true,
                  "tema": "anatomia_equina",
                  "nivel": "intermedio",
                  "script3d": "sistema_cardiovascular.py",
                  "tiempoMinutos": 30,
                  "objetivos": [
                    "Identificar la morfologia y grandes vasos del corazon equino",
                    "Explorar las estructuras tridimensionales en el visor interactivo"
                  ],
                  "instrucciones": [
                    "Inicia el visor 3D para examinar el modelo anatomico",
                    "Gira la camara y haz clic sobre las estructuras para ver su descripcion",
                    "Registra tus observaciones en el informe de practica"
                  ],
                  "escenario": {
                    "titulo": "Exploracion Tridimensional del Sistema Cardiovascular Equino",
                    "descripcion": "Practica clinica interactiva con visualizacion espacial guiada de organos circulatorios"
                  }
                }
                """;
    }

    public static void validateConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("La configuracion JSON de la simulacion es obligatoria");
        }
        requireKey(configJson, "habilitado");
        requireKey(configJson, "tema");
        requireKey(configJson, "nivel");
        requireKey(configJson, "tiempoMinutos");
        requireKey(configJson, "objetivos");
        requireKey(configJson, "instrucciones");
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

    public static String readString(String json, String key, String fallback) {
        Pattern pattern = Pattern.compile(String.format(STRING_PATTERN.pattern(), Pattern.quote(key)));
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    public static int readInt(String json, String key, int fallback) {
        Pattern pattern = Pattern.compile(String.format(NUMBER_PATTERN.pattern(), Pattern.quote(key)));
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    public static List<String> readArray(String json, String key) {
        Pattern pattern = Pattern.compile(String.format(ARRAY_PATTERN.pattern(), Pattern.quote(key)), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        List<String> items = new ArrayList<>();
        if (!matcher.find()) {
            return items;
        }

        String raw = matcher.group(1).trim();
        if (raw.isBlank()) {
            return items;
        }

        String[] parts = raw.split(",");
        for (String part : parts) {
            String cleaned = part.trim();
            if (cleaned.startsWith("\"")) {
                cleaned = cleaned.substring(1);
            }
            if (cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            items.add(cleaned);
        }
        return items;
    }

    private static void requireKey(String json, String key) {
        if (!json.contains("\"" + key + "\"")) {
            throw new IllegalArgumentException("La simulacion debe incluir la clave '" + key + "'");
        }
    }
}

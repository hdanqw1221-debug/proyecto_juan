package app.config;

import app.db.EmbeddedDatabase;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

public class AppConfig {
    /** Variables de entorno que, si estan definidas, tienen prioridad sobre config/app.properties. */
    public static final String ENV_DB_URL = "APP_DB_URL";
    public static final String ENV_DB_USER = "APP_DB_USER";
    public static final String ENV_DB_PASSWORD = "APP_DB_PASSWORD";

    private final Properties props;
    private final Function<String, String> env;

    private AppConfig(Properties props) {
        this(props, System::getenv);
    }

    private AppConfig(Properties props, Function<String, String> env) {
        this.props = props;
        this.env = env;
    }

    /** Construye la configuracion desde propiedades y un proveedor de variables de entorno (usado en pruebas). */
    public static AppConfig fromProperties(Properties props, Function<String, String> env) {
        Properties copy = new Properties();
        copy.putAll(props);
        return new AppConfig(copy, env);
    }

    public static AppConfig load(String path) {
        Path resolved = resolveConfigPath(path);
        if (resolved == null) {
            throw new IllegalStateException("No se pudo cargar la configuracion. Rutas probadas: " + String.join(", ", probedPaths(path)));
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(resolved.toFile())) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la configuracion: " + resolved, e);
        }
        return new AppConfig(props);
    }

    private static Path resolveConfigPath(String path) {
        for (String candidate : probedPaths(path)) {
            Path p = Paths.get(candidate).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static List<String> probedPaths(String path) {
        Set<String> candidates = new LinkedHashSet<>();

        if (path != null && !path.isBlank()) {
            candidates.add(path);
            candidates.add(Paths.get(path).toAbsolutePath().toString());
        }

        // Estructura de desarrollo (ejecucion desde raiz del proyecto)
        candidates.add("config/app.properties");

        // Estructura de jpackage app-image / instalador en Windows
        candidates.add("app/config/app.properties");

        String launcherPath = System.getProperty("jpackage.app-path");
        if (launcherPath != null && !launcherPath.isBlank()) {
            Path launcher = Paths.get(launcherPath);
            Path root = launcher.getParent();
            if (root != null) {
                candidates.add(root.resolve("config").resolve("app.properties").toString());
                candidates.add(root.resolve("app").resolve("config").resolve("app.properties").toString());
            }
        }

        String override = System.getProperty("app.config.path");
        if (override != null && !override.isBlank()) {
            candidates.add(override);
        }

        return new ArrayList<>(candidates);
    }

    public String getDbUrl() {
        return fromEnvOrProperty(ENV_DB_URL, "app.db.url");
    }

    public String getDbUser() {
        return fromEnvOrProperty(ENV_DB_USER, "app.db.user");
    }

    /** La contrasena se toma de APP_DB_PASSWORD si existe; asi no hace falta guardarla en el archivo. */
    public String getDbPassword() {
        return fromEnvOrProperty(ENV_DB_PASSWORD, "app.db.password");
    }

    private String fromEnvOrProperty(String envName, String propertyName) {
        String value = env.apply(envName);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return props.getProperty(propertyName);
    }

    /** PostgreSQL incluido en el paquete: la aplicacion lo crea y lo arranca (ver app.db.EmbeddedDatabase). */
    public boolean isEmbeddedDatabase() {
        return Boolean.parseBoolean(props.getProperty("app.db.embedded", "false").trim());
    }

    public int getEmbeddedPort() {
        try {
            return Integer.parseInt(props.getProperty("app.db.embedded.port", "").trim());
        } catch (NumberFormatException e) {
            return EmbeddedDatabase.DEFAULT_PORT;
        }
    }

    /** Carpeta de los datos de la base embebida; por defecto %LOCALAPPDATA%\GestionAcademica\basedatos. */
    public Path getEmbeddedHome() {
        String configured = props.getProperty("app.db.embedded.dir", "").trim();
        if (!configured.isEmpty()) {
            return Paths.get(configured);
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData != null && !localAppData.isBlank()
                ? Paths.get(localAppData)
                : Paths.get(System.getProperty("user.home"), ".local", "share");
        return base.resolve("GestionAcademica").resolve("basedatos");
    }

    /** Cargar los usuarios, la materia y las practicas de prueba (db/test_users.sql) al crear la base embebida. */
    public boolean isEmbeddedDemoData() {
        return Boolean.parseBoolean(props.getProperty("app.db.embedded.demoData", "false").trim());
    }

    /** Copia de esta configuracion con otra conexion a la base de datos. */
    public AppConfig withDatabase(String url, String user, String password) {
        Properties copy = new Properties();
        copy.putAll(props);
        copy.setProperty("app.db.url", url);
        copy.setProperty("app.db.user", user);
        copy.setProperty("app.db.password", password);
        // La conexion indicada explicitamente no debe ser reemplazada por las variables de entorno.
        return new AppConfig(copy, name -> null);
    }
}

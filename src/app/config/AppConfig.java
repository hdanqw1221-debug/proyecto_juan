package app.config;

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

public class AppConfig {
    private final Properties props;

    private AppConfig(Properties props) {
        this.props = props;
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
        return props.getProperty("app.db.url");
    }

    public String getDbUser() {
        return props.getProperty("app.db.user");
    }

    public String getDbPassword() {
        return props.getProperty("app.db.password");
    }
}

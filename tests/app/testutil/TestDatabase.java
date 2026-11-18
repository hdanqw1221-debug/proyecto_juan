package app.testutil;

import app.config.AppConfig;
import app.db.ConnectionFactory;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base PostgreSQL para pruebas de integracion. Nunca usa la base "gestion": por defecto se conecta a
 * "gestion_pruebas" (creada con db/schema.sql, db/cavidades_schema.sql, db/cavidades_fichas.sql y
 * db/test_users.sql). Si no hay servidor, las pruebas que la usan se omiten (no fallan).
 *
 * Variables: APP_TEST_DB_URL, APP_TEST_DB_USER, APP_TEST_DB_PASSWORD (o APP_DB_PASSWORD, o la clave de
 * config/app.properties si existe localmente).
 */
public final class TestDatabase {
    public static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/gestion_pruebas";
    private static Boolean available;

    private TestDatabase() {
    }

    public static String url() {
        return env("APP_TEST_DB_URL", DEFAULT_URL);
    }

    public static String user() {
        return env("APP_TEST_DB_USER", "postgres");
    }

    public static String password() {
        String pw = System.getenv("APP_TEST_DB_PASSWORD");
        if (pw == null || pw.isBlank()) pw = System.getenv("APP_DB_PASSWORD");
        if ((pw == null || pw.isBlank()) && Files.isRegularFile(Path.of("config/app.properties"))) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream("config/app.properties")) {
                p.load(in);
                pw = p.getProperty("app.db.password", "");
            } catch (Exception ignored) {
                pw = "";
            }
        }
        return pw == null ? "" : pw;
    }

    /** Omite la prueba si no hay base de pruebas; aplica db/validacion_schema.sql (idempotente). */
    public static synchronized ConnectionFactory require() {
        if (available == null) {
            available = false;
            if (!url().contains("gestion_pruebas") && System.getenv("APP_TEST_DB_URL") == null) {
                throw new IllegalStateException("Las pruebas no deben usar la base de produccion");
            }
            try (Connection c = DriverManager.getConnection(url(), user(), password());
                 Statement st = c.createStatement()) {
                st.execute(Files.readString(Path.of("db/validacion_schema.sql"), StandardCharsets.UTF_8));
                available = true;
            } catch (Exception ex) {
                System.err.println("Base de pruebas no disponible: " + ex.getMessage());
            }
        }
        assumeTrue(available, "Base de datos de pruebas no disponible (" + url() + ")");
        Properties p = new Properties();
        p.setProperty("app.db.url", url());
        p.setProperty("app.db.user", user());
        p.setProperty("app.db.password", password());
        return new ConnectionFactory(AppConfig.fromProperties(p, name -> null));
    }

    public static Connection connect() throws Exception {
        return DriverManager.getConnection(url(), user(), password());
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}

package app.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bloque 0: la contrasena de la base de datos se lee de APP_DB_PASSWORD con prioridad sobre el archivo. */
class AppConfigTest {

    private static Properties props() {
        Properties p = new Properties();
        p.setProperty("app.db.url", "jdbc:postgresql://localhost:5432/gestion");
        p.setProperty("app.db.user", "postgres");
        p.setProperty("app.db.password", "clave-del-archivo");
        return p;
    }

    @Test
    void laVariableDeEntornoTienePrioridadSobreElArchivo() {
        AppConfig c = AppConfig.fromProperties(props(), Map.of(AppConfig.ENV_DB_PASSWORD, "clave-del-entorno")::get);
        assertEquals("clave-del-entorno", c.getDbPassword());
        assertEquals("postgres", c.getDbUser());
    }

    @Test
    void sinVariableDeEntornoSeUsaElArchivo() {
        AppConfig c = AppConfig.fromProperties(props(), name -> null);
        assertEquals("clave-del-archivo", c.getDbPassword());
    }

    @Test
    void unaVariableVaciaNoAnulaElArchivo() {
        AppConfig c = AppConfig.fromProperties(props(), Map.of(AppConfig.ENV_DB_PASSWORD, "  ")::get);
        assertEquals("clave-del-archivo", c.getDbPassword());
    }

    @Test
    void urlYUsuarioTambienSePuedenTomarDelEntorno() {
        AppConfig c = AppConfig.fromProperties(props(), Map.of(
                AppConfig.ENV_DB_URL, "jdbc:postgresql://10.0.0.5:5432/gestion",
                AppConfig.ENV_DB_USER, "app_gestion")::get);
        assertEquals("jdbc:postgresql://10.0.0.5:5432/gestion", c.getDbUrl());
        assertEquals("app_gestion", c.getDbUser());
    }

    @Test
    void withDatabaseIgnoraLasVariablesDeEntorno() {
        AppConfig c = AppConfig.fromProperties(props(), Map.of(AppConfig.ENV_DB_PASSWORD, "x")::get)
                .withDatabase("jdbc:postgresql://127.0.0.1:15432/gestion", "gestion", "embebida");
        assertEquals("embebida", c.getDbPassword());
    }
}

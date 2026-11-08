package app.db;

import app.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class EmbeddedDatabaseTest {

    @Test
    public void testConfiguracionEmbebida(@TempDir Path carpeta) throws Exception {
        Path props = Files.writeString(carpeta.resolve("app.properties"), String.join("\n",
                "app.db.embedded=true",
                "app.db.embedded.port=16000",
                "app.db.embedded.dir=" + carpeta.resolve("bd").toString().replace("\\", "/"),
                "app.db.embedded.demoData=true",
                "app.db.url=jdbc:postgresql://localhost:5432/gestion",
                "app.db.user=otro",
                "app.db.password=x"));
        AppConfig config = AppConfig.load(props.toString());
        assertTrue(config.isEmbeddedDatabase());
        assertEquals(16000, config.getEmbeddedPort());
        assertEquals(carpeta.resolve("bd"), config.getEmbeddedHome());
        assertTrue(config.isEmbeddedDemoData());

        AppConfig embebida = config.withDatabase("jdbc:postgresql://127.0.0.1:16000/gestion", "postgres", "clave");
        assertEquals("jdbc:postgresql://127.0.0.1:16000/gestion", embebida.getDbUrl());
        assertEquals("postgres", embebida.getDbUser());
        assertEquals("clave", embebida.getDbPassword());
        assertEquals("jdbc:postgresql://localhost:5432/gestion", config.getDbUrl(), "la original no cambia");
    }

    @Test
    public void testConfiguracionDeDesarrolloNoEsEmbebida() {
        AppConfig config = AppConfig.load("config/app.properties");
        assertFalse(config.isEmbeddedDatabase());
        assertEquals(EmbeddedDatabase.DEFAULT_PORT, config.getEmbeddedPort());
        assertTrue(config.getEmbeddedHome().endsWith(Path.of("GestionAcademica", "basedatos")));
    }

    @Test
    public void testPuertoDelArchivoPid() {
        assertEquals(15432, EmbeddedDatabase.readPidFilePort(List.of("1234", "C:/datos", "1760000000", "15432", "", "127.0.0.1")));
        assertNull(EmbeddedDatabase.readPidFilePort(List.of("1234", "C:/datos")));
        assertNull(EmbeddedDatabase.readPidFilePort(List.of("1234", "C:/datos", "1760000000", "x")));
    }

    @Test
    public void testSaltaElPuertoOcupado() throws Exception {
        try (ServerSocket ocupado = new ServerSocket(0, 1, InetAddress.getByName(EmbeddedDatabase.HOST))) {
            int puerto = ocupado.getLocalPort();
            assertNotEquals(puerto, EmbeddedDatabase.choosePort(puerto));
        }
    }

    @Test
    public void testScriptsDelEsquemaEnOrden(@TempDir Path carpeta) throws Exception {
        EmbeddedDatabase sinDemo = new EmbeddedDatabase(carpeta, carpeta, Path.of("db"), 0, false);
        assertEquals(EmbeddedDatabase.SCHEMA_SCRIPTS,
                sinDemo.schemaScripts().stream().map(p -> p.getFileName().toString()).toList());
        EmbeddedDatabase conDemo = new EmbeddedDatabase(carpeta, carpeta, Path.of("db"), 0, true);
        assertEquals(EmbeddedDatabase.DEMO_SCRIPT, conDemo.schemaScripts().get(3).getFileName().toString());
    }

    /** Crea un cluster real con los binarios de PostgreSQL disponibles, carga el esquema y lo reutiliza. */
    @Test
    public void testCreaArrancaYDetiene(@TempDir Path carpeta) throws Exception {
        Path bin = binariosPostgres();
        assumeTrue(bin != null, "No hay binarios de PostgreSQL en este equipo");
        Path home = carpeta.resolve("basedatos");
        AppConfig base = AppConfig.load("config/app.properties");

        EmbeddedDatabase bd = new EmbeddedDatabase(bin, home, Path.of("db"), EmbeddedDatabase.DEFAULT_PORT + 7, true);
        assertFalse(bd.isInitialized());
        try {
            AppConfig config = bd.start(base);
            assertTrue(bd.isInitialized());
            assertTrue(config.getDbUrl().startsWith("jdbc:postgresql://127.0.0.1:"));
            try (Connection conn = DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
                 Statement st = conn.createStatement()) {
                assertEquals(4, count(st, "SELECT count(*) FROM usuarios"));
                assertEquals(3, count(st, "SELECT count(*) FROM simulaciones"));
                assertTrue(count(st, "SELECT count(*) FROM estructuras_anatomicas") > 40);
                assertTrue(count(st, "SELECT count(*) FROM modelos_3d") > 50);
                try (ResultSet rs = st.executeQuery("SELECT nombre FROM cavidades WHERE codigo = 'toracica'")) {
                    assertTrue(rs.next());
                    assertEquals("Cavidad torácica", rs.getString(1), "los acentos se cargan en UTF-8");
                }
            }

            // Una segunda instancia reutiliza el servidor en marcha y, al salir, no lo detiene mientras la primera siga abierta.
            EmbeddedDatabase otra = new EmbeddedDatabase(bin, home, Path.of("db"), EmbeddedDatabase.DEFAULT_PORT + 7, true);
            assertEquals(config.getDbUrl(), otra.start(base).getDbUrl());
            otra.stop();
            try (Connection conn = DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword())) {
                assertTrue(conn.isValid(2));
            }

            // Cierre forzado de la primera: el servidor sigue en marcha y lo detiene la siguiente instancia al salir.
            bd.releaseInstance();
            assertTrue(Files.exists(home.resolve("datos").resolve("postmaster.pid")));
            EmbeddedDatabase siguiente = new EmbeddedDatabase(bin, home, Path.of("db"), EmbeddedDatabase.DEFAULT_PORT + 7, true);
            assertEquals(config.getDbUrl(), siguiente.start(base).getDbUrl());
            siguiente.stop();
        } finally {
            bd.stop();
        }
        assertFalse(Files.exists(home.resolve("datos").resolve("postmaster.pid")), "el servidor quedo detenido");
        try (var restantes = Files.list(home.resolve("instancias"))) {
            assertEquals(0, restantes.count(), "no quedan archivos de instancia");
        }
    }

    private static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** El PostgreSQL preparado para el paquete o, si no existe, el instalado en el equipo. */
    private static Path binariosPostgres() {
        Path paquete = Path.of("build", "pgsql-runtime", "bin");
        if (Files.isRegularFile(paquete.resolve("pg_ctl.exe"))) {
            return paquete;
        }
        File[] versiones = new File("C:\\Program Files\\PostgreSQL").listFiles(File::isDirectory);
        if (versiones == null) {
            return null;
        }
        return Arrays.stream(versiones)
                .sorted(Comparator.comparing(File::getName).reversed())
                .map(v -> v.toPath().resolve("bin"))
                .filter(b -> Files.isRegularFile(b.resolve("initdb.exe")))
                .findFirst().orElse(null);
    }
}

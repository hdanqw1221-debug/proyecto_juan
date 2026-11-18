package app.service;

import app.dao.jdbc.ExplorationDaoJdbc;
import app.db.ConnectionFactory;
import app.model.VisorEvent;
import app.testutil.TestCourses;
import app.testutil.TestDatabase;
import app.testutil.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * C05: los eventos del visor se validan linea a linea y se guardan en exploracion_eventos a medida que llegan.
 * Se usa un visor simulado (script Python) que emite eventos validos, lineas mal formadas, lineas vacias y,
 * en un segundo caso, termina de forma abrupta.
 */
class ExplorationIntegrationTest {
    private ConnectionFactory factory;
    private ExplorationService service;
    private long teacher, student, course, simulation;

    @BeforeEach
    void setUp() throws Exception {
        factory = TestDatabase.require();
        assumeTrue(Visor3DService.ejecutaCodigo(List.of("python"), "pass"), "Python no disponible");
        service = new ExplorationService(factory);
        teacher = TestUsers.create("c05_prof");
        student = TestUsers.create("c05_est");
        course = TestCourses.course(teacher);
        try (Connection c = TestDatabase.connect()) {
            exec(c, "INSERT INTO estudiante_materias (estudiante_id, curso_id) VALUES (" + student + ", " + course + ")");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO simulaciones (curso_id, nombre, configuracion) VALUES (?, 'Practica C05', ?::jsonb) RETURNING id")) {
                ps.setLong(1, course);
                ps.setString(2, SimulationService.defaultConfigJson("cavidad_abdominal.py"));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    simulation = rs.getLong(1);
                }
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        TestCourses.deleteCourse(course);
        TestUsers.delete(student);
        TestUsers.delete(teacher);
    }

    private static Path visor(Path dir, String name, String... lines) throws Exception {
        StringBuilder py = new StringBuilder("import sys\n");
        for (String l : lines) {
            if (l.startsWith("EXIT")) {
                py.append("sys.exit(").append(l.substring(4).strip()).append(")\n");
            } else {
                py.append("print(").append(pyString(l)).append(", flush=True)\n");
            }
        }
        return Files.writeString(dir.resolve(name), py.toString());
    }

    private static String pyString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Test
    void guardaSoloLosEventosValidosConTodosSusCampos(@TempDir Path dir) throws Exception {
        Path script = visor(dir, "visor_falso.py",
                "{\"evento\": \"inicio\", \"t\": 1000.0, \"modelo\": \"abdominal\", \"objetos\": 30}",
                "Warning: aviso de VTK",
                "",
                "{\"evento\": \"seleccion\", \"t\": 1010.0, \"objeto_id\": \"ABD_HIGADO\", \"ficha_id\": \"ABD_HIGADO\", \"origen\": \"clic\", \"modelo\": \"abdominal\"}",
                "{\"evento\": \"seleccion\", \"t\": 1012.0, \"objeto_id\": \"ABD_BAZO\"",
                "{\"evento\": \"seleccion\", \"t\": 1030.0, \"objeto_id\": \"ABD_RINON_IZQ\", \"ficha_id\": \"ABD_RINON\", \"origen\": \"busqueda\", \"modelo\": \"abdominal\"}",
                "{\"evento\": \"seleccion\", \"t\": 1035.0, \"objeto_id\": \"ABD_HIGADO\", \"ficha_id\": \"ABD_HIGADO\", \"origen\": \"clic\", \"modelo\": \"abdominal\"}",
                "{'evento': 'seleccion'}",
                "{\"evento\": \"cierre\", \"t\": 1065.0, \"modelo\": \"abdominal\", \"duracion_s\": 65.0}");

        ExplorationService.Recorder rec = service.recorder(student, simulation);
        List<VisorEvent> received = new ArrayList<>();
        Visor3DService.Resultado r = Visor3DService.ejecutarComando(List.of("python", script.toString()), dir, null,
                e -> { received.add(e); rec.accept(e); });

        assertTrue(r.correcto(), r.salida());
        assertEquals(5, r.eventosValidos());
        assertEquals(2, r.eventosInvalidos());
        assertEquals(5, rec.saved());
        assertEquals(0, rec.failed());
        assertTrue(r.salida().contains("[evento descartado"), r.salida());

        try (Connection c = TestDatabase.connect();
             PreparedStatement ps = c.prepareStatement("SELECT evento, objeto_id, ficha_id, origen, modelo, simulacion_id, "
                     + "sesion, extract(epoch FROM t_evento) AS t, creado_en >= t_evento AS orden FROM exploracion_eventos "
                     + "WHERE usuario_id = ? ORDER BY t_evento, id")) {
            ps.setLong(1, student);
            List<String> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assertEquals(simulation, rs.getLong("simulacion_id"));
                    assertEquals(r.sesion(), rs.getObject("sesion"));
                    rows.add(rs.getString("evento") + "|" + rs.getString("objeto_id") + "|" + rs.getString("ficha_id")
                            + "|" + rs.getString("origen") + "|" + rs.getString("modelo") + "|" + Math.round(rs.getDouble("t")));
                }
            }
            assertEquals(List.of(
                    "inicio|null|null|null|abdominal|1000",
                    "seleccion|ABD_HIGADO|ABD_HIGADO|clic|abdominal|1010",
                    "seleccion|ABD_RINON_IZQ|ABD_RINON|busqueda|abdominal|1030",
                    "seleccion|ABD_HIGADO|ABD_HIGADO|clic|abdominal|1035",
                    "cierre|null|null|null|abdominal|1065"), rows);
        }

        // Vista del profesor: estructuras consultadas, tiempo por cavidad y por ficha.
        ExplorationDaoJdbc.StudentSummary summary = service.summaryByCourses(List.of(course)).stream()
                .filter(s -> s.studentId() == student).findFirst().orElseThrow();
        assertEquals(1, summary.sessions());
        assertEquals(2, summary.structures());
        assertEquals(3, summary.selections());
        assertEquals(65.0, summary.seconds(), 0.01);

        List<ExplorationDaoJdbc.TimeRow> byModel = service.timeByModel(student);
        assertEquals(1, byModel.size());
        assertEquals("Cavidad abdominal", byModel.get(0).name());
        assertEquals(65.0, byModel.get(0).seconds(), 0.01);

        Map<String, Double> byFicha = service.timeByFicha(student).stream()
                .collect(Collectors.toMap(ExplorationDaoJdbc.TimeRow::key, ExplorationDaoJdbc.TimeRow::seconds));
        assertEquals(20.0 + 30.0, byFicha.get("ABD_HIGADO"), 0.01, "1010->1030 y 1035->1065");
        assertEquals(5.0, byFicha.get("ABD_RINON"), 0.01, "1030->1035");
    }

    @Test
    void cierreAbruptoYSalidaVaciaNoRompenElRegistro(@TempDir Path dir) throws Exception {
        Path abrupto = visor(dir, "abrupto.py",
                "{\"evento\": \"inicio\", \"t\": 2000.0, \"modelo\": \"inguinal\"}",
                "{\"evento\": \"seleccion\", \"t\": 2004.0, \"objeto_id\": \"ING_TESTICULO_IZQ\", \"ficha_id\": \"ING_TESTICULO\", \"origen\": \"clic\", \"modelo\": \"inguinal\"}",
                "{\"evento\": \"seleccion\", \"t\": 2006.0, \"objeto_id\": \"ING_EPI",
                "EXIT 3");
        ExplorationService.Recorder rec = service.recorder(student, null);
        Visor3DService.Resultado r = Visor3DService.ejecutarComando(List.of("python", abrupto.toString()), dir, null, rec);
        assertFalse(r.correcto(), "sin evento de cierre y con codigo 3");
        assertEquals(2, rec.saved());
        assertEquals(1, r.eventosInvalidos());

        Path vacio = visor(dir, "vacio.py");
        ExplorationService.Recorder rec2 = service.recorder(student, null);
        Visor3DService.Resultado r2 = Visor3DService.ejecutarComando(List.of("python", vacio.toString()), dir, null, rec2);
        assertEquals(0, r2.eventosValidos());
        assertEquals(0, rec2.saved());

        List<ExplorationDaoJdbc.TimeRow> byModel = service.timeByModel(student);
        assertEquals(4.0, byModel.get(0).seconds(), 0.01, "sin cierre cuenta hasta el ultimo evento recibido");
        assertEquals(0.0, service.timeByFicha(student).get(0).seconds(), 0.01, "la ultima seleccion no tiene duracion conocida");
    }

    @Test
    void unFalloDeLaBaseNoInterrumpeLaLecturaDelVisor(@TempDir Path dir) throws Exception {
        Path script = visor(dir, "visor.py",
                "{\"evento\": \"inicio\", \"t\": 3000.0, \"modelo\": \"toracica\"}",
                "{\"evento\": \"cierre\", \"t\": 3001.0, \"modelo\": \"toracica\"}");
        ExplorationService.Recorder rec = service.recorder(-12345L, null);  // usuario inexistente: viola la clave foranea
        Visor3DService.Resultado r = Visor3DService.ejecutarComando(List.of("python", script.toString()), dir, null, rec);
        assertTrue(r.correcto());
        assertTrue(r.cerrado());
        assertEquals(2, rec.failed());
        assertNotNull(rec.lastError());
    }

    private static void exec(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}

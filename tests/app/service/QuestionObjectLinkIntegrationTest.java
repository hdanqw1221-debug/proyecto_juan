package app.service;

import app.model.Answer;
import app.model.Question;
import app.model.QuestionDraft;
import app.model.RoleType;
import app.testutil.TestCourses;
import app.testutil.TestDatabase;
import app.testutil.TestSessions;
import app.testutil.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** C07: cada pregunta puede enlazarse a una estructura del modelo 3D (clave foranea a modelos_3d). */
class QuestionObjectLinkIntegrationTest {
    private static final String TEMP_OBJECT = "TEST_C07_OBJ";
    private ExamService service;
    private long teacher, course, exam;

    @BeforeEach
    void setUp() throws Exception {
        var factory = TestDatabase.require();
        teacher = TestUsers.create("c07_prof");
        service = new ExamService(factory, TestSessions.as(teacher, RoleType.PROFESOR));
        course = TestCourses.course(teacher);
        exam = TestCourses.exam(course);
        try (Connection c = TestDatabase.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO modelos_3d (objeto_id, nombre, cavidad_id, lado, archivo, color_hex, opacidad, capa, seleccionable) "
                        + "VALUES (?, 'Estructura de prueba', (SELECT min(id) FROM cavidades), 'MED', 'obj/x.obj', '#FFFFFF', 1.0, 1, TRUE) "
                        + "ON CONFLICT (objeto_id) DO NOTHING")) {
            ps.setString(1, TEMP_OBJECT);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        TestCourses.deleteCourse(course);
        TestUsers.delete(teacher);
        try (Connection c = TestDatabase.connect(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM modelos_3d WHERE objeto_id = ?")) {
            ps.setString(1, TEMP_OBJECT);
            ps.executeUpdate();
        }
    }

    private static QuestionDraft draft(String statement, String objectId) {
        QuestionDraft d = new QuestionDraft();
        d.setStatement(statement);
        d.setType("UNICA");
        d.setScore(1);
        d.setObjectId(objectId);
        Answer yes = new Answer();
        yes.setContent("Si");
        yes.setCorrect(true);
        Answer no = new Answer();
        no.setContent("No");
        d.setAnswers(List.of(yes, no));
        return d;
    }

    @Test
    void laEstructuraSePersisteAlCrearYAlEditar() {
        service.createQuestionsWithAnswers(exam, List.of(draft("Con estructura", TEMP_OBJECT), draft("Sin estructura", null),
                draft("Vacia", "  ")));
        List<Question> saved = service.listQuestionsByExam(exam);
        assertEquals(TEMP_OBJECT, saved.get(0).getObjectId());
        assertNull(saved.get(1).getObjectId());
        assertNull(saved.get(2).getObjectId(), "un texto en blanco se guarda como sin enlace");
        assertEquals("Estructura de prueba", service.modelObjectName(TEMP_OBJECT));

        // Edicion: quitar el enlace de la primera y enlazar la segunda.
        QuestionDraft first = draft("Con estructura", null);
        first.setId(saved.get(0).getId());
        QuestionDraft second = draft("Sin estructura", TEMP_OBJECT);
        second.setId(saved.get(1).getId());
        service.synchronizeQuestions(exam, List.of(first, second));
        List<Question> edited = service.listQuestionsByExam(exam);
        assertNull(edited.get(0).getObjectId());
        assertEquals(TEMP_OBJECT, edited.get(1).getObjectId());
    }

    @Test
    void laBaseRechazaUnaEstructuraInexistente() {
        assertThrows(RuntimeException.class, () ->
                service.createQuestionsWithAnswers(exam, List.of(draft("Mala", "NO_EXISTE_EN_EL_MODELO"))));
        assertEquals(0, service.listQuestionsByExam(exam).size(), "la transaccion no deja preguntas a medias");
    }

    @Test
    void siSeBorraLaEstructuraLaPreguntaSeConservaSinEnlace() throws Exception {
        service.createQuestionsWithAnswers(exam, List.of(draft("Con estructura", TEMP_OBJECT)));
        try (Connection c = TestDatabase.connect(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM modelos_3d WHERE objeto_id = ?")) {
            ps.setString(1, TEMP_OBJECT);
            assertEquals(1, ps.executeUpdate());
        }
        List<Question> after = service.listQuestionsByExam(exam);
        assertEquals(1, after.size());
        assertNull(after.get(0).getObjectId());
        assertNull(service.modelObjectName(null));
    }

    @Test
    void elSelectorListaLasEstructurasSeleccionablesDelCatalogo() {
        var objects = service.listModelObjects();
        assertTrue(objects.size() >= 40, "el catalogo trae las estructuras de las 47 fichas: " + objects.size());
        assertTrue(objects.stream().anyMatch(o -> o.objectId().equals(TEMP_OBJECT)));
        assertTrue(objects.stream().allMatch(o -> !o.name().isBlank() && !o.cavity().isBlank()));
    }

    @Test
    void laColumnaTieneLaClaveForaneaADefinida() throws Exception {
        try (Connection c = TestDatabase.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT confrelid::regclass::text, confdeltype FROM pg_constraint WHERE conname = 'fk_preguntas_objeto'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "debe existir fk_preguntas_objeto");
            assertEquals("modelos_3d", rs.getString(1));
            assertEquals("n", rs.getString(2), "ON DELETE SET NULL");
        } catch (SQLException e) {
            fail(e);
        }
    }
}

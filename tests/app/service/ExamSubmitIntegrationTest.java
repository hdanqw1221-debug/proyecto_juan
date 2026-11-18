package app.service;

import app.testutil.TestCourses;
import app.testutil.TestDatabase;
import app.testutil.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/** C02 (PF-27): enviar la evaluacion registra EXAM_SUBMIT con examen, intento y calificacion, en una transaccion. */
class ExamSubmitIntegrationTest {
    private ExamAttemptService service;
    private long teacher, student, course, exam;

    @BeforeEach
    void setUp() throws Exception {
        service = new ExamAttemptService(TestDatabase.require());
        teacher = TestUsers.create("c02_prof");
        student = TestUsers.create("c02_est");
        course = TestCourses.course(teacher);
        exam = TestCourses.exam(course);
    }

    @AfterEach
    void tearDown() throws Exception {
        TestCourses.deleteCourse(course);
        TestUsers.delete(student);
        TestUsers.delete(teacher);
    }

    @Test
    void enviarRegistraExamSubmitYCierraElIntento() throws Exception {
        long attempt = TestCourses.attempt(exam, student);
        service.submitAttempt(attempt, exam, student, 4.2, 4, 5, "{\"calificacionFinal\":4.2}");

        try (Connection c = TestDatabase.connect()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT accion, detalle FROM logs WHERE usuario_id = ? AND accion = 'EXAM_SUBMIT'")) {
                ps.setLong(1, student);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "debe existir el evento EXAM_SUBMIT");
                    String detail = rs.getString("detalle");
                    assertTrue(detail.contains("Examen ID " + exam), detail);
                    assertTrue(detail.contains("Intento ID " + attempt), detail);
                    assertTrue(detail.contains("Calificacion 4.2 / 5.0"), detail);
                    assertFalse(rs.next(), "un solo evento por envio");
                }
            }
            assertEquals(1, count(c, "SELECT count(*) FROM resultados WHERE intento_id = " + attempt));
            assertEquals(1, count(c, "SELECT count(*) FROM intentos_examen WHERE id = " + attempt
                    + " AND fecha_fin IS NOT NULL AND puntaje_total = 4.2"));
        }

        assertThrows(IllegalStateException.class,
                () -> service.submitAttempt(attempt, exam, student, 5.0, 5, 5, "{}"), "no se reenvia");
        try (Connection c = TestDatabase.connect()) {
            assertEquals(1, count(c, "SELECT count(*) FROM logs WHERE usuario_id = " + student + " AND accion = 'EXAM_SUBMIT'"));
        }
    }

    @Test
    void siFallaElGuardadoNoQuedaNadaAMedias() throws Exception {
        long attempt = TestCourses.attempt(exam, student);
        assertThrows(RuntimeException.class,
                () -> service.submitAttempt(attempt, exam, student, 3.0, 3, 5, "{json roto"));
        try (Connection c = TestDatabase.connect()) {
            assertEquals(0, count(c, "SELECT count(*) FROM logs WHERE usuario_id = " + student));
            assertEquals(0, count(c, "SELECT count(*) FROM resultados WHERE intento_id = " + attempt));
            assertEquals(1, count(c, "SELECT count(*) FROM intentos_examen WHERE id = " + attempt + " AND fecha_fin IS NULL"));
        }
    }

    @Test
    void otroEstudianteNoPuedeEnviarElIntento() throws Exception {
        long attempt = TestCourses.attempt(exam, student);
        assertThrows(IllegalStateException.class,
                () -> service.submitAttempt(attempt, exam, teacher, 5.0, 5, 5, "{}"));
    }

    private static int count(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}

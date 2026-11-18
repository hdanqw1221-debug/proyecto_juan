package app.service;

import app.model.RoleType;
import app.testutil.TestDatabase;
import app.testutil.TestSessions;
import app.testutil.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** C14: la vista de notas del profesor muestra el nombre del estudiante, no su identificador. */
class StudentNamesIntegrationTest {
    private long a, b;
    private ExamAttemptService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ExamAttemptService(TestDatabase.require(), TestSessions.as(1L, RoleType.PROFESOR));
        a = TestUsers.create("c14_ana");
        b = TestUsers.create("c14_luis");
    }

    @AfterEach
    void tearDown() throws Exception {
        TestUsers.delete(a);
        TestUsers.delete(b);
    }

    @Test
    void devuelveElNombreDeCadaEstudiante() throws Exception {
        Map<Long, String> names = service.studentNames(List.of(a, b, -999L));
        assertEquals(2, names.size(), "un id inexistente simplemente no aparece");
        try (Connection c = TestDatabase.connect(); PreparedStatement ps = c.prepareStatement("SELECT username FROM usuarios WHERE id = ?")) {
            ps.setLong(1, a);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(rs.getString(1), names.get(a));
            }
        }
        assertTrue(names.get(a).startsWith("c14_ana_"));
        assertTrue(service.studentNames(List.of()).isEmpty());
        assertTrue(service.studentNames(null).isEmpty());
    }
}

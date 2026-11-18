package app.dao;

import app.dao.jdbc.LogDaoJdbc;
import app.db.ConnectionFactory;
import app.model.LogEntry;
import app.model.RoleType;
import app.model.User;
import app.service.AuditService;
import app.testutil.TestDatabase;
import app.testutil.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** C01 (PF-28): cada usuario recibe solo su propia actividad reciente; el administrador la de todos. */
class LogDaoJdbcIntegrationTest {
    private ConnectionFactory factory;
    private long profesor;
    private long estudiante;

    @BeforeEach
    void setUp() throws Exception {
        factory = TestDatabase.require();
        profesor = TestUsers.create("c01_prof");
        estudiante = TestUsers.create("c01_est");
    }

    @AfterEach
    void tearDown() throws Exception {
        TestUsers.delete(profesor);
        TestUsers.delete(estudiante);
    }

    @Test
    void cadaUsuarioSoloRecibeSusEventos() {
        AuditService audit = new AuditService(factory);
        for (int i = 0; i < 3; i++) {
            audit.log(profesor, "EXAM_CREATE", "prof " + i);
            audit.log(estudiante, "EXAM_START", "est " + i);
        }
        LogDao dao = new LogDaoJdbc(factory);
        List<LogEntry> delProfesor = dao.listRecentByUser(profesor, 10);
        List<LogEntry> delEstudiante = dao.listRecentByUser(estudiante, 10);
        assertEquals(3, delProfesor.size());
        assertEquals(3, delEstudiante.size());
        assertTrue(delProfesor.stream().allMatch(e -> e.getUserId() == profesor));
        assertTrue(delEstudiante.stream().allMatch(e -> e.getUserId() == estudiante));
        assertEquals("prof 2", delProfesor.get(0).getDetail(), "el mas reciente primero");
        assertEquals(2, dao.listRecentByUser(estudiante, 2).size(), "respeta el limite");

        User u = new User();
        u.setId(estudiante);
        u.addRole(RoleType.ESTUDIANTE);
        assertTrue(audit.listRecentFor(u, 50).stream().allMatch(e -> e.getUserId() == estudiante));

        User admin = new User();
        admin.setId(-1);
        admin.addRole(RoleType.ADMINISTRADOR);
        List<LogEntry> todo = audit.listRecentFor(admin, 6);
        assertTrue(todo.stream().anyMatch(e -> e.getUserId() == profesor));
        assertTrue(todo.stream().anyMatch(e -> e.getUserId() == estudiante));
    }
}

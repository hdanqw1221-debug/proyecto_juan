package app.service;

import app.dao.LogDao;
import app.model.LogEntry;
import app.model.RoleType;
import app.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** C01: regla de visibilidad de la actividad reciente segun el rol (sin base de datos). */
class AuditServiceTest {
    private static final class FakeLogDao implements LogDao {
        final List<LogEntry> entries = new ArrayList<>();

        @Override
        public void create(LogEntry e) {
            entries.add(0, new LogEntry(entries.size() + 1, e.getUserId(), e.getAction(), e.getDetail(), LocalDateTime.now()));
        }

        @Override
        public List<LogEntry> listRecent(int limit) {
            return entries.stream().limit(limit).toList();
        }

        @Override
        public List<LogEntry> listRecentByUser(long userId, int limit) {
            return entries.stream().filter(e -> e.getUserId() != null && e.getUserId() == userId).limit(limit).toList();
        }
    }

    private static User user(long id, RoleType role) {
        User u = new User();
        u.setId(id);
        u.addRole(role);
        return u;
    }

    @Test
    void profesorYEstudianteSoloVenLoPropioYAdminTodo() {
        FakeLogDao dao = new FakeLogDao();
        AuditService audit = new AuditService(dao);
        audit.log(1L, "LOGIN_OK", "admin");
        audit.log(2L, "EXAM_CREATE", "profesor");
        audit.log(3L, "EXAM_START", "estudiante");
        audit.log(null, "LOGIN_FAIL", "anonimo");

        assertEquals(List.of("profesor"), audit.listRecentFor(user(2, RoleType.PROFESOR), 10).stream().map(LogEntry::getDetail).toList());
        assertEquals(List.of("estudiante"), audit.listRecentFor(user(3, RoleType.ESTUDIANTE), 10).stream().map(LogEntry::getDetail).toList());
        assertEquals(4, audit.listRecentFor(user(1, RoleType.ADMINISTRADOR), 10).size());
        assertTrue(audit.listRecentFor(null, 10).isEmpty());
    }
}

package app.service;

import app.dao.LogDao;
import app.dao.jdbc.LogDaoJdbc;
import app.db.ConnectionFactory;
import app.model.LogEntry;
import app.model.RoleType;
import app.model.User;

import java.util.List;

public class AuditService {
    private final LogDao logDao;

    public AuditService(ConnectionFactory factory) {
        this(new LogDaoJdbc(factory));
    }

    public AuditService(LogDao logDao) {
        this.logDao = logDao;
    }

    public void log(Long userId, String action, String detail) {
        LogEntry entry = new LogEntry();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setDetail(detail);
        logDao.create(entry);
    }

    /** Todos los eventos del sistema: solo para la auditoria del administrador. */
    public List<LogEntry> listRecent(int limit) {
        return logDao.listRecent(limit);
    }

    /**
     * Actividad reciente que puede ver el usuario: el administrador ve la de todo el sistema; el profesor y el
     * estudiante solo la propia (C01).
     */
    public List<LogEntry> listRecentFor(User user, int limit) {
        if (user == null) {
            return List.of();
        }
        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            return logDao.listRecent(limit);
        }
        return logDao.listRecentByUser(user.getId(), limit);
    }
}

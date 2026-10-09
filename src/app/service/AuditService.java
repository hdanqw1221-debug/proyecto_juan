package app.service;

import app.dao.LogDao;
import app.dao.jdbc.LogDaoJdbc;
import app.db.ConnectionFactory;
import app.model.LogEntry;

public class AuditService {
    private final LogDao logDao;

    public AuditService(ConnectionFactory factory) {
        this.logDao = new LogDaoJdbc(factory);
    }

    public void log(Long userId, String action, String detail) {
        LogEntry entry = new LogEntry();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setDetail(detail);
        logDao.create(entry);
    }

    public java.util.List<LogEntry> listRecent(int limit) {
        return logDao.listRecent(limit);
    }
}

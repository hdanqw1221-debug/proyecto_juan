package app.dao;

import app.model.LogEntry;

import java.util.List;

public interface LogDao {
    void create(LogEntry entry);
    List<LogEntry> listRecent(int limit);

    /** Ultimos eventos de un solo usuario (los demas usuarios no se incluyen). */
    List<LogEntry> listRecentByUser(long userId, int limit);
}

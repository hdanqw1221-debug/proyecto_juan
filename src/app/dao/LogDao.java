package app.dao;

import app.model.LogEntry;

import java.util.List;

public interface LogDao {
    void create(LogEntry entry);
    List<LogEntry> listRecent(int limit);
}

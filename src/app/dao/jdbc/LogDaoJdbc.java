package app.dao.jdbc;

import app.dao.LogDao;
import app.db.ConnectionFactory;
import app.model.LogEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LogDaoJdbc implements LogDao {
    private final ConnectionFactory factory;

    public LogDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public void create(LogEntry entry) {
        String sql = "INSERT INTO logs (usuario_id, accion, detalle) VALUES (?, ?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (entry.getUserId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, entry.getUserId());
            }
            ps.setString(2, entry.getAction());
            ps.setString(3, entry.getDetail());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creando log", e);
        }
    }

    @Override
    public List<LogEntry> listRecent(int limit) {
        String sql = "SELECT id, usuario_id, accion, detalle, creado_en FROM logs ORDER BY creado_en DESC LIMIT ?";
        List<LogEntry> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LogEntry entry = new LogEntry(
                            rs.getLong("id"),
                            (Long) rs.getObject("usuario_id"),
                            rs.getString("accion"),
                            rs.getString("detalle"),
                            rs.getTimestamp("creado_en").toLocalDateTime()
                    );
                    list.add(entry);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando logs", e);
        }
        return list;
    }
}

package app.dao.jdbc;

import app.dao.ResultDao;
import app.db.ConnectionFactory;
import app.model.Result;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ResultDaoJdbc implements ResultDao {
    private final ConnectionFactory factory;

    public ResultDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long create(Result result) {
        String sql = "INSERT INTO resultados (intento_id, detalle) VALUES (?, ?::jsonb)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, result.getAttemptId());
            ps.setString(2, result.getDetailJson());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando resultado", e);
        }
        throw new RuntimeException("No se pudo crear resultado");
    }

    @Override
    public List<Result> listByStudent(long studentId) {
        String sql = "SELECT r.id, r.intento_id, r.detalle, r.generado_en " +
                "FROM resultados r " +
                "JOIN intentos_examen i ON r.intento_id = i.id " +
                "WHERE i.estudiante_id = ? ORDER BY r.generado_en DESC";
        List<Result> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Result(
                            rs.getLong("id"),
                            rs.getLong("intento_id"),
                            rs.getString("detalle"),
                            rs.getTimestamp("generado_en").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando resultados", e);
        }
        return list;
    }

    @Override
    public Result findByAttemptId(long attemptId) {
        String sql = "SELECT id, intento_id, detalle, generado_en FROM resultados WHERE intento_id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Result(
                            rs.getLong("id"),
                            rs.getLong("intento_id"),
                            rs.getString("detalle"),
                            rs.getTimestamp("generado_en").toLocalDateTime()
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando resultado por intento", e);
        }
        return null;
    }
}

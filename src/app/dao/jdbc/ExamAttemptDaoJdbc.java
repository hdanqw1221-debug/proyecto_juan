package app.dao.jdbc;

import app.dao.ExamAttemptDao;
import app.db.ConnectionFactory;
import app.model.ExamAttempt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamAttemptDaoJdbc implements ExamAttemptDao {
    private final ConnectionFactory factory;

    public ExamAttemptDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long createAttempt(long examId, long studentId) {
        String sql = "INSERT INTO intentos_examen (examen_id, estudiante_id) VALUES (?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando intento", e);
        }
        throw new RuntimeException("No se pudo crear intento");
    }

    @Override
    public void finishAttempt(long attemptId, double totalScore) {
        String sql = "UPDATE intentos_examen SET fecha_fin = CURRENT_TIMESTAMP, puntaje_total = ? WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, totalScore);
            ps.setLong(2, attemptId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error finalizando intento", e);
        }
    }

    @Override
    public void updateScore(long attemptId, double totalScore) {
        String sql = "UPDATE intentos_examen SET puntaje_total = ?, fecha_fin = COALESCE(fecha_fin, CURRENT_TIMESTAMP) WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, totalScore);
            ps.setLong(2, attemptId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando puntaje del intento", e);
        }
    }

    @Override
    public ExamAttempt findById(long attemptId) {
        String sql = "SELECT id, examen_id, estudiante_id, fecha_inicio, fecha_fin, puntaje_total FROM intentos_examen WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando intento", e);
        }
        return null;
    }

    @Override
    public List<ExamAttempt> listByStudent(long studentId) {
        String sql = "SELECT id, examen_id, estudiante_id, fecha_inicio, fecha_fin, puntaje_total " +
                "FROM intentos_examen WHERE estudiante_id = ? ORDER BY fecha_inicio DESC";
        List<ExamAttempt> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando intentos", e);
        }
        return list;
    }

    @Override
    public List<ExamAttempt> listByExam(long examId) {
        String sql = "SELECT id, examen_id, estudiante_id, fecha_inicio, fecha_fin, puntaje_total " +
                "FROM intentos_examen WHERE examen_id = ? ORDER BY fecha_inicio DESC";
        List<ExamAttempt> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando intentos por examen", e);
        }
        return list;
    }

    @Override
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM intentos_examen";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error contando intentos de examen", e);
        }
        return 0;
    }

    private ExamAttempt map(ResultSet rs) throws SQLException {
        return new ExamAttempt(
                rs.getLong("id"),
                rs.getLong("examen_id"),
                rs.getLong("estudiante_id"),
                rs.getTimestamp("fecha_inicio").toLocalDateTime(),
                rs.getTimestamp("fecha_fin") != null ? rs.getTimestamp("fecha_fin").toLocalDateTime() : null,
                rs.getObject("puntaje_total") != null ? rs.getDouble("puntaje_total") : null
        );
    }
}

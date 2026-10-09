package app.dao.jdbc;

import app.dao.ExamDao;
import app.db.ConnectionFactory;
import app.model.Exam;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamDaoJdbc implements ExamDao {
    private final ConnectionFactory factory;

    public ExamDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long create(Exam exam) {
        String sql = "INSERT INTO examenes (curso_id, titulo, descripcion, activo, vence_en, limite_intentos, duracion_minutos) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, exam.getCourseId());
            ps.setString(2, exam.getTitle());
            ps.setString(3, exam.getDescription());
            ps.setBoolean(4, exam.isActive());
            ps.setTimestamp(5, exam.getDueAt() != null ? Timestamp.valueOf(exam.getDueAt()) : null);
            ps.setInt(6, exam.getMaxAttempts());
            ps.setInt(7, exam.getDurationMinutes());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando examen", e);
        }
        throw new RuntimeException("No se pudo crear examen");
    }

    @Override
    public void updateAvailability(long examId, boolean active, java.time.LocalDateTime dueAt) {
        String sql = "UPDATE examenes SET activo = ?, vence_en = ? WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setTimestamp(2, dueAt != null ? Timestamp.valueOf(dueAt) : null);
            ps.setLong(3, examId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando disponibilidad del examen", e);
        }
    }

    @Override
    public void updateDetails(long examId, String title, String description, boolean active, java.time.LocalDateTime dueAt, int maxAttempts, int durationMinutes) {
        String sql = "UPDATE examenes SET titulo = ?, descripcion = ?, activo = ?, vence_en = ?, limite_intentos = ?, duracion_minutos = ? WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setBoolean(3, active);
            ps.setTimestamp(4, dueAt != null ? Timestamp.valueOf(dueAt) : null);
            ps.setInt(5, maxAttempts);
            ps.setInt(6, durationMinutes);
            ps.setLong(7, examId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando detalles del examen", e);
        }
    }

    @Override
    public Exam findById(long examId) {
        String sql = "SELECT id, curso_id, titulo, descripcion, activo, vence_en, limite_intentos, duracion_minutos, creado_en FROM examenes WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando examen", e);
        }
        return null;
    }

    @Override
    public List<Exam> listByCourse(long courseId) {
        String sql = "SELECT id, curso_id, titulo, descripcion, activo, vence_en, limite_intentos, duracion_minutos, creado_en FROM examenes WHERE curso_id = ? ORDER BY id";
        List<Exam> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando examenes", e);
        }
        return list;
    }

    @Override
    public void delete(long examId) {
        String sql = "DELETE FROM examenes WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando cuestionario", e);
        }
    }

    private Exam map(ResultSet rs) throws SQLException {
        return new Exam(
                rs.getLong("id"),
                rs.getLong("curso_id"),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                rs.getBoolean("activo"),
                rs.getTimestamp("vence_en") != null ? rs.getTimestamp("vence_en").toLocalDateTime() : null,
                rs.getTimestamp("creado_en").toLocalDateTime(),
                rs.getInt("limite_intentos"),
                rs.getInt("duracion_minutos")
        );
    }
}

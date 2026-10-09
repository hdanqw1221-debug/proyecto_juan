package app.dao.jdbc;

import app.dao.AnswerDao;
import app.db.ConnectionFactory;
import app.model.Answer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AnswerDaoJdbc implements AnswerDao {
    private final ConnectionFactory factory;

    public AnswerDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long create(Answer answer) {
        String sql = "INSERT INTO respuestas (pregunta_id, contenido, correcta) VALUES (?, ?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, answer.getQuestionId());
            ps.setString(2, answer.getContent());
            ps.setBoolean(3, answer.isCorrect());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando respuesta", e);
        }
        throw new RuntimeException("No se pudo crear respuesta");
    }

    @Override
    public List<Answer> listByQuestion(long questionId) {
        String sql = "SELECT id, pregunta_id, contenido, correcta FROM respuestas WHERE pregunta_id = ? ORDER BY id";
        List<Answer> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Answer(
                            rs.getLong("id"),
                            rs.getLong("pregunta_id"),
                            rs.getString("contenido"),
                            rs.getBoolean("correcta")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando respuestas", e);
        }
        return list;
    }
}

package app.dao.jdbc;

import app.dao.QuestionDao;
import app.db.ConnectionFactory;
import app.model.Question;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionDaoJdbc implements QuestionDao {
    private final ConnectionFactory factory;

    public QuestionDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
        ensureImageColumns();
    }

    @Override
    public long create(Question question) {
        String sql = "INSERT INTO preguntas "
                + "(examen_id, enunciado, tipo, puntaje, imagen, imagen_nombre, imagen_tipo, objeto_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, question.getExamId());
            ps.setString(2, question.getStatement());
            ps.setString(3, question.getType());
            ps.setDouble(4, question.getScore());
            ps.setBytes(5, question.getImageData());
            ps.setString(6, question.getImageName());
            ps.setString(7, question.getImageContentType());
            ps.setString(8, question.getObjectId());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando pregunta", e);
        }
        throw new RuntimeException("No se pudo crear pregunta");
    }

    @Override
    public List<Question> listByExam(long examId) {
        String sql = "SELECT id, examen_id, enunciado, tipo, puntaje, imagen, imagen_nombre, imagen_tipo, objeto_id "
                + "FROM preguntas WHERE examen_id = ? ORDER BY orden, id";
        List<Question> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Question question = new Question(
                            rs.getLong("id"),
                            rs.getLong("examen_id"),
                            rs.getString("enunciado"),
                            rs.getString("tipo"),
                            rs.getDouble("puntaje")
                    );
                    question.setImageData(rs.getBytes("imagen"));
                    question.setImageName(rs.getString("imagen_nombre"));
                    question.setImageContentType(rs.getString("imagen_tipo"));
                    question.setObjectId(rs.getString("objeto_id"));
                    list.add(question);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando preguntas", e);
        }
        return list;
    }

    private void ensureImageColumns() {
        String[] migrations = {
                "ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS imagen BYTEA",
                "ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS imagen_nombre VARCHAR(255)",
                "ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS imagen_tipo VARCHAR(100)",
                "ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS orden INT NOT NULL DEFAULT 0",
                "ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS objeto_id VARCHAR(60)"
        };
        try (Connection conn = factory.getConnection();
             Statement statement = conn.createStatement()) {
            for (String migration : migrations) {
                statement.executeUpdate(migration);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo preparar el soporte de imagenes en preguntas", e);
        }
    }
}

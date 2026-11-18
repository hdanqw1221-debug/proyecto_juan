package app.service;

import app.model.RoleType;
import app.session.SessionManager;
import app.security.ServiceGuard;
import app.dao.ExamDao;
import app.dao.QuestionDao;
import app.dao.jdbc.ExamDaoJdbc;
import app.dao.jdbc.QuestionDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Answer;
import app.model.Exam;
import app.model.Question;
import app.model.QuestionDraft;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExamService {
    private final ConnectionFactory factory;
    private final ExamDao examDao;
    private final QuestionDao questionDao;

    private final ServiceGuard guard;

    /** Sin sesion: los metodos de escritura se deniegan. */
    public ExamService(ConnectionFactory factory) {
        this(factory, null);
    }

    public ExamService(ConnectionFactory factory, SessionManager sessionManager) {
        this.guard = ServiceGuard.of(sessionManager);
        this.factory = factory;
        this.examDao = new ExamDaoJdbc(factory);
        this.questionDao = new QuestionDaoJdbc(factory);
    }

    public long createExam(long courseId, String title, String description, boolean active, LocalDateTime dueAt) {
        return createExam(courseId, title, description, active, dueAt, 1, 60);
    }

    public long createExam(long courseId, String title, String description, boolean active, LocalDateTime dueAt, int maxAttempts, int durationMinutes) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        Exam exam = new Exam();
        exam.setCourseId(courseId);
        exam.setTitle(title);
        exam.setDescription(description);
        exam.setActive(active);
        exam.setDueAt(dueAt);
        exam.setMaxAttempts(maxAttempts > 0 ? maxAttempts : 1);
        exam.setDurationMinutes(durationMinutes > 0 ? durationMinutes : 60);
        return examDao.create(exam);
    }

    public void updateAvailability(long examId, boolean active, LocalDateTime dueAt) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        examDao.updateAvailability(examId, active, dueAt);
    }

    public void updateDetails(long examId, String title, String description, boolean active, LocalDateTime dueAt, int maxAttempts, int durationMinutes) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        examDao.updateDetails(examId, title, description, active, dueAt, maxAttempts > 0 ? maxAttempts : 1, durationMinutes > 0 ? durationMinutes : 60);
    }

    public Exam findExam(long examId) {
        return examDao.findById(examId);
    }

    public long createQuestion(long examId, String statement, String type, double score) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        Question question = new Question();
        question.setExamId(examId);
        question.setStatement(statement);
        question.setType(type);
        question.setScore(score);
        return questionDao.create(question);
    }

    public long createQuestionWithAnswers(long examId, String statement, String type,
                                          double score, List<Answer> answers) {
        QuestionDraft draft = new QuestionDraft();
        draft.setStatement(statement);
        draft.setType(type);
        draft.setScore(score);
        draft.setAnswers(answers);
        return createQuestionsWithAnswers(examId, List.of(draft)).get(0);
    }

    public List<Long> createQuestionsWithAnswers(long examId, List<QuestionDraft> questions) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Debes proporcionar al menos una pregunta");
        }
        String questionSql = "INSERT INTO preguntas "
                + "(examen_id, enunciado, tipo, puntaje, imagen, imagen_nombre, imagen_tipo, orden, objeto_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String answerSql = "INSERT INTO respuestas (pregunta_id, contenido, correcta) VALUES (?, ?, ?)";

        try (Connection conn = factory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Long> questionIds = new ArrayList<>();
                try (PreparedStatement questionStatement = conn.prepareStatement(
                        questionSql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement answerStatement = conn.prepareStatement(answerSql)) {
                    int order = nextQuestionOrder(conn, examId);
                    for (QuestionDraft question : questions) {
                        questionStatement.setLong(1, examId);
                        questionStatement.setString(2, question.getStatement());
                        questionStatement.setString(3, question.getType());
                        questionStatement.setDouble(4, question.getScore());
                        questionStatement.setBytes(5, question.getImageData());
                        questionStatement.setString(6, question.getImageName());
                        questionStatement.setString(7, question.getImageContentType());
                        questionStatement.setInt(8, order);
                        questionStatement.setString(9, question.getObjectId());
                        order++;
                        questionStatement.executeUpdate();

                        long questionId;
                        try (ResultSet keys = questionStatement.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("No se genero el ID de una pregunta");
                            }
                            questionId = keys.getLong(1);
                            questionIds.add(questionId);
                        }

                        for (Answer answer : question.getAnswers()) {
                            answerStatement.setLong(1, questionId);
                            answerStatement.setString(2, answer.getContent());
                            answerStatement.setBoolean(3, answer.isCorrect());
                            answerStatement.addBatch();
                        }
                    }
                    answerStatement.executeBatch();
                }
                conn.commit();
                return questionIds;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar el lote de preguntas", e);
        }
    }

    public List<Long> synchronizeQuestions(long examId, List<QuestionDraft> questions) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("El cuestionario debe conservar al menos una pregunta");
        }
        String selectSql = "SELECT id FROM preguntas WHERE examen_id = ?";
        String insertSql = "INSERT INTO preguntas "
                + "(examen_id, enunciado, tipo, puntaje, imagen, imagen_nombre, imagen_tipo, orden, objeto_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        String updateSql = "UPDATE preguntas SET enunciado = ?, tipo = ?, puntaje = ?, "
                + "imagen = ?, imagen_nombre = ?, imagen_tipo = ?, orden = ?, objeto_id = ? "
                + "WHERE id = ? AND examen_id = ?";
        String deleteAnswersSql = "DELETE FROM respuestas WHERE pregunta_id = ?";
        String insertAnswerSql = "INSERT INTO respuestas (pregunta_id, contenido, correcta) VALUES (?, ?, ?)";
        String deleteQuestionSql = "DELETE FROM preguntas WHERE id = ? AND examen_id = ?";

        try (Connection conn = factory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Set<Long> existingIds = new HashSet<>();
                try (PreparedStatement select = conn.prepareStatement(selectSql)) {
                    select.setLong(1, examId);
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            existingIds.add(rs.getLong(1));
                        }
                    }
                }

                Set<Long> retainedIds = new HashSet<>();
                List<Long> savedIds = new ArrayList<>();
                try (PreparedStatement insert = conn.prepareStatement(insertSql);
                     PreparedStatement update = conn.prepareStatement(updateSql);
                     PreparedStatement deleteAnswers = conn.prepareStatement(deleteAnswersSql);
                     PreparedStatement insertAnswer = conn.prepareStatement(insertAnswerSql)) {
                    for (int order = 0; order < questions.size(); order++) {
                        QuestionDraft question = questions.get(order);
                        Long questionId = question.getId();
                        if (questionId == null) {
                            insert.setLong(1, examId);
                            insert.setString(2, question.getStatement());
                            insert.setString(3, question.getType());
                            insert.setDouble(4, question.getScore());
                            insert.setBytes(5, question.getImageData());
                            insert.setString(6, question.getImageName());
                            insert.setString(7, question.getImageContentType());
                            insert.setInt(8, order);
                            insert.setString(9, question.getObjectId());
                            try (ResultSet rs = insert.executeQuery()) {
                                if (!rs.next()) {
                                    throw new SQLException("No se genero el ID de una pregunta");
                                }
                                questionId = rs.getLong(1);
                            }
                        } else {
                            if (!existingIds.contains(questionId)) {
                                throw new IllegalArgumentException(
                                        "La pregunta " + questionId + " no pertenece al cuestionario"
                                );
                            }
                            update.setString(1, question.getStatement());
                            update.setString(2, question.getType());
                            update.setDouble(3, question.getScore());
                            update.setBytes(4, question.getImageData());
                            update.setString(5, question.getImageName());
                            update.setString(6, question.getImageContentType());
                            update.setInt(7, order);
                            update.setString(8, question.getObjectId());
                            update.setLong(9, questionId);
                            update.setLong(10, examId);
                            update.executeUpdate();

                            deleteAnswers.setLong(1, questionId);
                            deleteAnswers.executeUpdate();
                        }

                        for (Answer answer : question.getAnswers()) {
                            insertAnswer.setLong(1, questionId);
                            insertAnswer.setString(2, answer.getContent());
                            insertAnswer.setBoolean(3, answer.isCorrect());
                            insertAnswer.addBatch();
                        }
                        retainedIds.add(questionId);
                        savedIds.add(questionId);
                    }
                    insertAnswer.executeBatch();
                }

                try (PreparedStatement deleteQuestion = conn.prepareStatement(deleteQuestionSql)) {
                    for (Long existingId : existingIds) {
                        if (!retainedIds.contains(existingId)) {
                            deleteQuestion.setLong(1, existingId);
                            deleteQuestion.setLong(2, examId);
                            deleteQuestion.addBatch();
                        }
                    }
                    deleteQuestion.executeBatch();
                }
                conn.commit();
                return savedIds;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo actualizar el cuestionario", e);
        }
    }

    public void deleteExam(long examId) {
        guard.require(RoleType.PROFESOR, RoleType.ADMINISTRADOR);
        examDao.delete(examId);
    }

    private int nextQuestionOrder(Connection conn, long examId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(orden), -1) + 1 FROM preguntas WHERE examen_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<Exam> listExamsByCourse(long courseId) {
        return examDao.listByCourse(courseId);
    }

    public List<Question> listQuestionsByExam(long examId) {
        return questionDao.listByExam(examId);
    }

    /** Estructura del modelo 3D que se puede enlazar a una pregunta. */
    public record ModelObject(String objectId, String name, String cavity) {
        @Override
        public String toString() {
            return name + " (" + objectId + ")";
        }
    }

    /** Estructuras seleccionables del modelo 3D (tabla modelos_3d) para el selector del constructor de preguntas. */
    public List<ModelObject> listModelObjects() {
        String sql = "SELECT m.objeto_id, m.nombre, c.nombre AS cavidad FROM modelos_3d m "
                + "JOIN cavidades c ON c.id = m.cavidad_id WHERE m.seleccionable ORDER BY c.nombre, m.nombre, m.objeto_id";
        List<ModelObject> list = new ArrayList<>();
        try (Connection conn = factory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ModelObject(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo listar las estructuras del modelo 3D", e);
        }
        return list;
    }

    /** Nombre de la estructura enlazada (para la revision del intento); el propio id si ya no existe. */
    public String modelObjectName(String objectId) {
        if (objectId == null) {
            return null;
        }
        String sql = "SELECT nombre FROM modelos_3d WHERE objeto_id = ?";
        try (Connection conn = factory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : objectId;
            }
        } catch (SQLException e) {
            return objectId;
        }
    }

    public boolean isAvailable(Exam exam) {
        return exam != null && exam.isActive() && (exam.getDueAt() == null || exam.getDueAt().isAfter(LocalDateTime.now()));
    }
}

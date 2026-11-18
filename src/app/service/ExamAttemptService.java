package app.service;

import app.dao.ExamAttemptDao;
import app.dao.jdbc.ExamAttemptDaoJdbc;
import app.db.ConnectionFactory;
import app.model.ExamAttempt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public class ExamAttemptService {
    /** Accion de auditoria que se registra al enviar una evaluacion (C02, PF-27). */
    public static final String EXAM_SUBMIT = "EXAM_SUBMIT";

    private final ConnectionFactory factory;
    private final ExamAttemptDao examAttemptDao;

    public ExamAttemptService(ConnectionFactory factory) {
        this.factory = factory;
        this.examAttemptDao = new ExamAttemptDaoJdbc(factory);
    }

    public long startAttempt(long examId, long studentId) {
        return examAttemptDao.createAttempt(examId, studentId);
    }

    public void finishAttempt(long attemptId, double totalScore) {
        examAttemptDao.finishAttempt(attemptId, totalScore);
    }

    /**
     * Envia una evaluacion en una sola transaccion: cierra el intento con su calificacion, guarda el detalle
     * de respuestas en {@code resultados} y registra el evento {@value #EXAM_SUBMIT} en {@code logs}. Si algo
     * falla no queda nada a medias. Un intento ya enviado, o de otro estudiante, no se puede volver a enviar.
     */
    public void submitAttempt(long attemptId, long examId, long studentId, double finalScore,
                              int correctAnswers, int totalQuestions, String resultJson) {
        String finish = "UPDATE intentos_examen SET fecha_fin = CURRENT_TIMESTAMP, puntaje_total = ? "
                + "WHERE id = ? AND examen_id = ? AND estudiante_id = ? AND fecha_fin IS NULL";
        String result = "INSERT INTO resultados (intento_id, detalle) VALUES (?, ?::jsonb)";
        String log = "INSERT INTO logs (usuario_id, accion, detalle) VALUES (?, ?, ?)";
        try (Connection conn = factory.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement f = conn.prepareStatement(finish);
                 PreparedStatement r = conn.prepareStatement(result);
                 PreparedStatement l = conn.prepareStatement(log)) {
                f.setDouble(1, finalScore);
                f.setLong(2, attemptId);
                f.setLong(3, examId);
                f.setLong(4, studentId);
                if (f.executeUpdate() != 1) {
                    throw new IllegalStateException("El intento " + attemptId
                            + " no existe, no pertenece al estudiante o ya fue enviado");
                }
                r.setLong(1, attemptId);
                r.setString(2, resultJson);
                r.executeUpdate();
                l.setLong(1, studentId);
                l.setString(2, EXAM_SUBMIT);
                l.setString(3, submitDetail(examId, attemptId, finalScore, correctAnswers, totalQuestions));
                l.executeUpdate();
                conn.commit();
            } catch (RuntimeException | SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo enviar la evaluacion", e);
        }
    }

    static String submitDetail(long examId, long attemptId, double finalScore, int correct, int total) {
        return String.format(Locale.ROOT, "Examen ID %d - Intento ID %d - Calificacion %.1f / 5.0 - Correctas %d de %d",
                examId, attemptId, finalScore, correct, total);
    }

    public void updateScore(long attemptId, double totalScore) {
        examAttemptDao.updateScore(attemptId, totalScore);
    }

    public ExamAttempt findById(long attemptId) {
        return examAttemptDao.findById(attemptId);
    }

    public List<ExamAttempt> listByStudent(long studentId) {
        return examAttemptDao.listByStudent(studentId);
    }

    public List<ExamAttempt> listByExam(long examId) {
        return examAttemptDao.listByExam(examId);
    }

    public int countAll() {
        return examAttemptDao.countAll();
    }
}

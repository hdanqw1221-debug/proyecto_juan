package app.service;

import app.dao.ExamAttemptDao;
import app.dao.jdbc.ExamAttemptDaoJdbc;
import app.db.ConnectionFactory;
import app.model.ExamAttempt;

import java.util.List;

public class ExamAttemptService {
    private final ExamAttemptDao examAttemptDao;

    public ExamAttemptService(ConnectionFactory factory) {
        this.examAttemptDao = new ExamAttemptDaoJdbc(factory);
    }

    public long startAttempt(long examId, long studentId) {
        return examAttemptDao.createAttempt(examId, studentId);
    }

    public void finishAttempt(long attemptId, double totalScore) {
        examAttemptDao.finishAttempt(attemptId, totalScore);
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

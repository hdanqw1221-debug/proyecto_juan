package app.dao;

import app.model.ExamAttempt;

import java.util.List;

public interface ExamAttemptDao {
    long createAttempt(long examId, long studentId);
    void finishAttempt(long attemptId, double totalScore);
    void updateScore(long attemptId, double totalScore);
    ExamAttempt findById(long attemptId);
    List<ExamAttempt> listByStudent(long studentId);
    List<ExamAttempt> listByExam(long examId);
    int countAll();
}

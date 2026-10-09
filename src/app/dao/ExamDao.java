package app.dao;

import app.model.Exam;

import java.util.List;

public interface ExamDao {
    long create(Exam exam);
    void updateAvailability(long examId, boolean active, java.time.LocalDateTime dueAt);
    void updateDetails(long examId, String title, String description, boolean active, java.time.LocalDateTime dueAt, int maxAttempts, int durationMinutes);
    Exam findById(long examId);
    List<Exam> listByCourse(long courseId);
    void delete(long examId);
}

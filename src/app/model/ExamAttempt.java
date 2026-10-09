package app.model;

import java.time.LocalDateTime;

public class ExamAttempt {
    private long id;
    private long examId;
    private long studentId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Double totalScore;

    public ExamAttempt() {}

    public ExamAttempt(long id, long examId, long studentId, LocalDateTime startAt, LocalDateTime endAt, Double totalScore) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.totalScore = totalScore;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getExamId() { return examId; }
    public void setExamId(long examId) { this.examId = examId; }

    public long getStudentId() { return studentId; }
    public void setStudentId(long studentId) { this.studentId = studentId; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
}

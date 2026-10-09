package app.model;

import java.time.LocalDateTime;

public class StudentCourseAssignment {
    private long studentId;
    private String studentUsername;
    private long courseId;
    private String courseName;
    private LocalDateTime assignedAt;

    public StudentCourseAssignment(long studentId, String studentUsername, long courseId, String courseName, LocalDateTime assignedAt) {
        this.studentId = studentId;
        this.studentUsername = studentUsername;
        this.courseId = courseId;
        this.courseName = courseName;
        this.assignedAt = assignedAt;
    }

    public long getStudentId() { return studentId; }
    public String getStudentUsername() { return studentUsername; }
    public long getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
}

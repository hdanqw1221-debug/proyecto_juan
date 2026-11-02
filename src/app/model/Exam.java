package app.model;

import java.time.LocalDateTime;

public class Exam {
    private long id;
    private long courseId;
    private String title;
    private String description;
    private boolean active;
    private LocalDateTime dueAt;
    private LocalDateTime createdAt;
    private int maxAttempts;
    private int durationMinutes;

    public Exam() {
        this.maxAttempts = 1;
        this.durationMinutes = 60;
    }

    public Exam(long id, long courseId, String title, String description, boolean active, LocalDateTime dueAt, LocalDateTime createdAt, int maxAttempts, int durationMinutes) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.active = active;
        this.dueAt = dueAt;
        this.createdAt = createdAt;
        this.maxAttempts = maxAttempts;
        this.durationMinutes = durationMinutes;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCourseId() { return courseId; }
    public void setCourseId(long courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
}

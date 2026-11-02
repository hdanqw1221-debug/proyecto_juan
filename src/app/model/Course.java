package app.model;

import java.time.LocalDateTime;

public class Course {
    private long id;
    private String name;
    private String description;
    private long teacherId;
    private LocalDateTime createdAt;

    public Course() {}

    public Course(long id, String name, String description, long teacherId, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.teacherId = teacherId;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getTeacherId() { return teacherId; }
    public void setTeacherId(long teacherId) { this.teacherId = teacherId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

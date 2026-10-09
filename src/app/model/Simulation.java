package app.model;

import java.time.LocalDateTime;

public class Simulation {
    private long id;
    private long courseId;
    private String name;
    private String description;
    private String configJson;
    private LocalDateTime createdAt;

    public Simulation() {}

    public Simulation(long id, long courseId, String name, String description, String configJson, LocalDateTime createdAt) {
        this.id = id;
        this.courseId = courseId;
        this.name = name;
        this.description = description;
        this.configJson = configJson;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCourseId() { return courseId; }
    public void setCourseId(long courseId) { this.courseId = courseId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package app.model;

import java.time.LocalDateTime;

public class LogEntry {
    private long id;
    private Long userId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;

    public LogEntry() {}

    public LogEntry(long id, Long userId, String action, String detail, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

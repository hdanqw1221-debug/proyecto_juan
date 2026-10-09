package app.model;

import java.time.LocalDateTime;

public class Result {
    private long id;
    private long attemptId;
    private String detailJson;
    private LocalDateTime generatedAt;

    public Result() {}

    public Result(long id, long attemptId, String detailJson, LocalDateTime generatedAt) {
        this.id = id;
        this.attemptId = attemptId;
        this.detailJson = detailJson;
        this.generatedAt = generatedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getAttemptId() { return attemptId; }
    public void setAttemptId(long attemptId) { this.attemptId = attemptId; }

    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}

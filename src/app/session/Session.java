package app.session;

import app.model.User;

import java.time.LocalDateTime;

public class Session {
    private final User user;
    private final LocalDateTime loginAt;

    public Session(User user) {
        this.user = user;
        this.loginAt = LocalDateTime.now();
    }

    public User getUser() { return user; }
    public LocalDateTime getLoginAt() { return loginAt; }
}

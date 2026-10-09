package app.service;

import app.model.User;
import app.security.PasswordHasher;
import app.session.Session;
import app.session.SessionManager;

public class AuthService {
    private final UserService userService;
    private final PasswordHasher hasher;
    private final SessionManager sessionManager;

    public AuthService(UserService userService, PasswordHasher hasher, SessionManager sessionManager) {
        this.userService = userService;
        this.hasher = hasher;
        this.sessionManager = sessionManager;
    }

    public User login(String username, String password) {
        User user = userService.findByUsername(username);
        if (user == null || !user.isActive()) return null;
        if (!hasher.verify(password, user.getPasswordHash())) return null;
        sessionManager.start(new Session(user));
        return user;
    }

    public void logout() {
        sessionManager.end();
    }
}

package app.testutil;

import app.model.RoleType;
import app.model.User;
import app.session.Session;
import app.session.SessionManager;

/** Sesiones de prueba con un rol dado. */
public final class TestSessions {
    private TestSessions() {
    }

    public static SessionManager as(long userId, RoleType... roles) {
        User u = new User();
        u.setId(userId);
        u.setUsername("prueba_" + userId);
        for (RoleType r : roles) u.addRole(r);
        SessionManager sm = new SessionManager();
        sm.start(new Session(u));
        return sm;
    }

    public static void switchTo(SessionManager sm, long userId, RoleType... roles) {
        sm.start(as(userId, roles).getCurrent());
    }
}

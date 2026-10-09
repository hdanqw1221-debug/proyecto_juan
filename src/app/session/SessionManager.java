package app.session;

public class SessionManager {
    private Session current;

    public Session getCurrent() { return current; }

    public void start(Session session) { this.current = session; }

    public void end() { this.current = null; }
}

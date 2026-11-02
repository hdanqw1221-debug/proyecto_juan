package app.security;

import app.model.RoleType;
import app.session.Session;

import java.util.EnumSet;

public class AccessControl {
    public static void requireRole(Session session, RoleType... roles) {
        if (session == null || session.getUser() == null) {
            throw new SecurityException("Sesion requerida");
        }
        EnumSet<RoleType> allowed = EnumSet.noneOf(RoleType.class);
        for (RoleType role : roles) {
            allowed.add(role);
        }
        boolean ok = session.getUser().getRoles().stream().anyMatch(allowed::contains);
        if (!ok) {
            throw new SecurityException("Permiso denegado");
        }
    }
}

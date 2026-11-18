package app.security;

import app.model.RoleType;
import app.session.Session;
import app.session.SessionManager;

import java.util.function.Supplier;

/**
 * Autorizacion dentro de la capa de servicios (C04). Antes los permisos solo se comprobaban al construir los
 * menus de la interfaz; ahora cada metodo sensible de un servicio exige la sesion y el rol adecuados, de modo
 * que una llamada desde cualquier otro punto del codigo tambien queda protegida.
 *
 * Sin gestor de sesion la guarda deniega todo (falla cerrada).
 */
public final class ServiceGuard {
    private final Supplier<Session> session;

    private ServiceGuard(Supplier<Session> session) {
        this.session = session;
    }

    public static ServiceGuard of(SessionManager sessionManager) {
        return sessionManager == null ? denyAll() : new ServiceGuard(sessionManager::getCurrent);
    }

    public static ServiceGuard denyAll() {
        return new ServiceGuard(() -> null);
    }

    /** Exige una sesion con alguno de los roles indicados. */
    public void require(RoleType... roles) {
        AccessControl.requireRole(session.get(), roles);
    }

    /** Exige que la sesion pertenezca al usuario indicado (p. ej. su propio intento o su propia contrasena). */
    public void requireSelf(long userId) {
        Session current = session.get();
        if (current == null || current.getUser() == null) {
            throw new SecurityException("Sesion requerida");
        }
        if (current.getUser().getId() != userId) {
            throw new SecurityException("Permiso denegado: la operacion pertenece a otro usuario");
        }
    }
}

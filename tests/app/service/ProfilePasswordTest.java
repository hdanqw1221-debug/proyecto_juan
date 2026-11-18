package app.service;

import app.db.ConnectionFactory;
import app.model.RoleType;
import app.model.User;
import app.session.SessionManager;
import app.testutil.TestDatabase;
import app.testutil.TestSessions;
import app.security.BCryptPasswordHasher;
import app.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProfilePasswordTest {

    @Test
    public void testChangeOwnPasswordValidations() {
        // Antes usaba config/app.properties, es decir, la base real "gestion"; ahora la base de pruebas.
        ConnectionFactory connectionFactory = TestDatabase.require();
        PasswordHasher hasher = new BCryptPasswordHasher();
        SessionManager sessions = TestSessions.as(1L, RoleType.ADMINISTRADOR);
        UserService userService = new UserService(connectionFactory, hasher, sessions);

        // Crear usuario temporal para prueba
        String username = "test_profile_" + System.currentTimeMillis();
        String initialPass = "ClaveInicial123";
        User user = userService.createUser(username, username + "@unipamplona.edu.co", initialPass, true);
        TestSessions.switchTo(sessions, user.getId(), RoleType.ESTUDIANTE);  // el propio usuario cambia su clave

        try {
            // 1. Contrasena actual incorrecta
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                    userService.changeOwnPassword(user.getId(), "ClaveErronea123", "NuevaClave123")
            );
            assertTrue(ex1.getMessage().contains("actual no es correcta"));

            // 2. Nueva contrasena identica a la actual
            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                    userService.changeOwnPassword(user.getId(), initialPass, initialPass)
            );
            assertTrue(ex2.getMessage().contains("identica"));

            // 3. Nueva contrasena debil
            IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
                    userService.changeOwnPassword(user.getId(), initialPass, "debil")
            );
            assertTrue(ex3.getMessage().contains("debil") || ex3.getMessage().contains("caracteres"));

            // 4. Cambio exitoso con contrasena fuerte
            String newPass = "NuevaClaveSegura2026";
            assertDoesNotThrow(() ->
                    userService.changeOwnPassword(user.getId(), initialPass, newPass)
            );

            // Verificar que la nueva contrasena este activa
            User updated = userService.findById(user.getId());
            assertTrue(hasher.verify(newPass, updated.getPasswordHash()));
            assertFalse(hasher.verify(initialPass, updated.getPasswordHash()));

        } finally {
            // Limpieza (como administrador)
            TestSessions.switchTo(sessions, 1L, RoleType.ADMINISTRADOR);
            userService.deleteUser(user.getId());
        }
    }
}

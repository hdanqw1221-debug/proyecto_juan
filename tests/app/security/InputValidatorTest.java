package app.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InputValidatorTest {

    @Test
    public void testPasswordStrength() {
        // Debiles
        assertFalse(InputValidator.isStrongPassword(null));
        assertFalse(InputValidator.isStrongPassword("12345")); // muy corta
        assertFalse(InputValidator.isStrongPassword("abcdefg")); // sin mayuscula ni numero
        assertFalse(InputValidator.isStrongPassword("Abcdefg")); // sin numero
        assertFalse(InputValidator.isStrongPassword("abcdef1")); // sin mayuscula

        // Fuertes
        assertTrue(InputValidator.isStrongPassword("Clave1"));
        assertTrue(InputValidator.isStrongPassword("Admin123"));
        assertTrue(InputValidator.isStrongPassword("Pamplona2026*"));
    }

    @Test
    public void testEmailValidation() {
        assertDoesNotThrow(() -> InputValidator.email("juan@unipamplona.edu.co"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.email("invalido"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.email(null));
    }
}

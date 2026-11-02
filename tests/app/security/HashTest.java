package app.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mindrot.jbcrypt.BCrypt;

public class HashTest {

    @Test
    public void testHashGeneration() {
        String plain = "mi_clave_secreta";
        String hashed = BCrypt.hashpw(plain, BCrypt.gensalt(10));
        
        assertNotNull(hashed);
        assertNotEquals(plain, hashed);
        assertTrue(BCrypt.checkpw(plain, hashed));
    }
    
    @Test
    public void testWrongPassword() {
        String plain = "mi_clave_secreta";
        String hashed = BCrypt.hashpw(plain, BCrypt.gensalt(10));
        
        assertFalse(BCrypt.checkpw("clave_incorrecta", hashed));
    }
}

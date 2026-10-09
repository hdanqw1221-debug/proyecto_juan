package app.security;

import org.mindrot.jbcrypt.BCrypt;

public class BCryptPasswordHasher implements PasswordHasher {
    @Override
    public String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    @Override
    public boolean verify(String plainPassword, String hash) {
        return BCrypt.checkpw(plainPassword, hash);
    }
}

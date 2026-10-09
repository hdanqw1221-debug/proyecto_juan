import org.mindrot.jbcrypt.BCrypt;

public class GenerateUsers {
    public static void main(String[] args) {
        String salt = BCrypt.gensalt(12);
        
        System.out.println("-- Script para crear usuarios de prueba");
        System.out.println("INSERT INTO roles (nombre) VALUES ('ADMINISTRADOR'), ('ESTUDIANTE') ON CONFLICT (nombre) DO NOTHING;");
        
        createUser("admin", "admin@test.com", "admin123", "ADMINISTRADOR", salt);
        createUser("juan", "juan@test.com", "juan123", "ESTUDIANTE", salt);
        createUser("maria", "maria@test.com", "maria123", "ESTUDIANTE", salt);
    }

    private static void createUser(String user, String email, String pass, String role, String salt) {
        String hash = BCrypt.hashpw(pass, salt);
        System.out.println("\n-- Usuario: " + user + " (Pass: " + pass + ")");
        System.out.println("INSERT INTO usuarios (username, email, password_hash, activo) ");
        System.out.println("VALUES ('" + user + "', '" + email + "', '" + hash + "', true) ON CONFLICT (username) DO NOTHING;");
        
        System.out.println("INSERT INTO usuario_roles (usuario_id, role_id) ");
        System.out.println("SELECT u.id, r.id FROM usuarios u, roles r ");
        System.out.println("WHERE u.username = '" + user + "' AND r.nombre = '" + role + "'");
        System.out.println("ON CONFLICT DO NOTHING;");
    }
}

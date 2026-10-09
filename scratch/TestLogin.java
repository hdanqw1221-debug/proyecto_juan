import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;

public class TestLogin {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/gestion";
        String user = "postgres";
        String pass = "juan";

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            String sql = "SELECT password_hash FROM usuarios WHERE username = 'admin'";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    boolean match = BCrypt.checkpw("admin123", hash);
                    System.out.println("¿Contraseña 'admin123' es correcta para 'admin'?: " + (match ? "SÍ" : "NO"));
                } else {
                    System.out.println("Usuario admin no encontrado.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

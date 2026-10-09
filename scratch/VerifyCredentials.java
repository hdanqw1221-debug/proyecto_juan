import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;

public class VerifyCredentials {
  public static void main(String[] args) throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/gestion", "postgres", "juan")) {
      String[][] creds = {{"admin","admin123"},{"juan","juan123"},{"maria","maria123"}};
      for (String[] c : creds) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash, activo FROM usuarios WHERE username=?")) {
          ps.setString(1, c[0]);
          try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
              System.out.println(c[0] + " -> NO EXISTE");
              continue;
            }
            String hash = rs.getString("password_hash");
            boolean activo = rs.getBoolean("activo");
            boolean ok = BCrypt.checkpw(c[1], hash);
            System.out.println(c[0] + " -> activo=" + activo + ", pass_ok=" + ok);
          }
        }
      }
    }
  }
}

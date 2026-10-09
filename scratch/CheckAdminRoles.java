import java.sql.*;

public class CheckAdminRoles {
  public static void main(String[] args) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/gestion", "postgres", "juan")) {
      String sql = "SELECT u.username, u.activo, r.nombre FROM usuarios u LEFT JOIN usuario_roles ur ON ur.usuario_id=u.id LEFT JOIN roles r ON r.id=ur.rol_id WHERE u.username IN ('admin','juan','maria') ORDER BY u.username, r.nombre";
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
        while(rs.next()) {
          System.out.println(rs.getString(1)+" activo="+rs.getBoolean(2)+" rol="+rs.getString(3));
        }
      }
    }
  }
}

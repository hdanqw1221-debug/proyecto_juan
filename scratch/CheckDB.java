import java.sql.*;

public class CheckDB {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/gestion";
        String user = "postgres";
        String pass = "juan";

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("Conexión exitosa.");
            String sql = "SELECT username, activo FROM usuarios";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                System.out.println("Usuarios encontrados:");
                boolean found = false;
                while (rs.next()) {
                    System.out.println("- " + rs.getString("username") + " (Activo: " + rs.getBoolean("activo") + ")");
                    found = true;
                }
                if (!found) System.out.println("¡No hay usuarios en la tabla!");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}

import java.sql.*;

public class RepairCredentials {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:postgresql://localhost:5432/gestion";
    String user = "postgres";
    String pass = "juan";
    try (Connection conn = DriverManager.getConnection(url, user, pass)) {
      conn.setAutoCommit(false);
      try (Statement st = conn.createStatement()) {
        st.executeUpdate("INSERT INTO roles (nombre) VALUES ('ADMINISTRADOR'), ('ESTUDIANTE') ON CONFLICT (nombre) DO NOTHING");
        st.executeUpdate("INSERT INTO usuarios (username, email, password_hash, activo) VALUES ('admin','admin@test.com','$2a$12$jPSFusRDqV7x2l/kKUIg6OU9WCA6sZS43XYXoroqDwk.5oJTE1l1K',true) ON CONFLICT (username) DO UPDATE SET email=EXCLUDED.email,password_hash=EXCLUDED.password_hash,activo=EXCLUDED.activo");
        st.executeUpdate("INSERT INTO usuarios (username, email, password_hash, activo) VALUES ('juan','juan@test.com','$2a$12$cVnQ.AfzybVueipMAKMgV.f.6C0HEYwZRmu36iLSw43kQh/LmJCm6',true) ON CONFLICT (username) DO UPDATE SET email=EXCLUDED.email,password_hash=EXCLUDED.password_hash,activo=EXCLUDED.activo");
        st.executeUpdate("INSERT INTO usuarios (username, email, password_hash, activo) VALUES ('maria','maria@test.com','$2a$12$lNut3kvN1RV4TFWU/258beeiExtJDwckc4OLGAeMWTjgGpMAbdo1C',true) ON CONFLICT (username) DO UPDATE SET email=EXCLUDED.email,password_hash=EXCLUDED.password_hash,activo=EXCLUDED.activo");
        st.executeUpdate("INSERT INTO usuario_roles (usuario_id, rol_id) SELECT u.id, r.id FROM usuarios u, roles r WHERE u.username='admin' AND r.nombre='ADMINISTRADOR' ON CONFLICT DO NOTHING");
        st.executeUpdate("INSERT INTO usuario_roles (usuario_id, rol_id) SELECT u.id, r.id FROM usuarios u, roles r WHERE u.username='juan' AND r.nombre='ESTUDIANTE' ON CONFLICT DO NOTHING");
        st.executeUpdate("INSERT INTO usuario_roles (usuario_id, rol_id) SELECT u.id, r.id FROM usuarios u, roles r WHERE u.username='maria' AND r.nombre='ESTUDIANTE' ON CONFLICT DO NOTHING");
      }
      conn.commit();
      System.out.println("Credenciales reparadas correctamente.");
    }
  }
}

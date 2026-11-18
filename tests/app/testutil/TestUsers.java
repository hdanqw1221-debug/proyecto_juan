package app.testutil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/** Usuarios temporales para pruebas de integracion; se borran con {@link #delete(long)}. */
public final class TestUsers {
    private TestUsers() {
    }

    public static long create(String prefix) throws Exception {
        String name = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection c = TestDatabase.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO usuarios (username, email, password_hash) VALUES (?, ?, 'x') RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, name + "@pruebas.local");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public static void delete(long userId) throws Exception {
        try (Connection c = TestDatabase.connect()) {
            for (String sql : new String[]{
                    "DELETE FROM logs WHERE usuario_id = ?",
                    "DELETE FROM intentos_examen WHERE estudiante_id = ?",
                    "DELETE FROM usuarios WHERE id = ?"}) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
            }
        }
    }
}

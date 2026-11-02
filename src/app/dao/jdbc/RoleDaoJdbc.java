package app.dao.jdbc;

import app.dao.RoleDao;
import app.db.ConnectionFactory;
import app.model.Role;
import app.model.RoleType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RoleDaoJdbc implements RoleDao {
    private final ConnectionFactory factory;

    public RoleDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public Role findByName(RoleType name) {
        String sql = "SELECT id, nombre FROM roles WHERE nombre = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Role(rs.getLong("id"), rs.getString("nombre"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando rol", e);
        }
        return null;
    }

    @Override
    public List<Role> listAll() {
        List<Role> list = new ArrayList<>();
        String sql = "SELECT id, nombre FROM roles ORDER BY id";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Role(rs.getLong("id"), rs.getString("nombre")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando roles", e);
        }
        return list;
    }

    @Override
    public long ensureRole(RoleType name) {
        Role existing = findByName(name);
        if (existing != null) return existing.getId();
        String sql = "INSERT INTO roles (nombre) VALUES (?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando rol", e);
        }
        throw new RuntimeException("No se pudo crear rol");
    }
}

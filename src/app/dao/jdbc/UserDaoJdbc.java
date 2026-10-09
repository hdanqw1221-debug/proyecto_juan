package app.dao.jdbc;

import app.dao.RoleDao;
import app.dao.UserDao;
import app.db.ConnectionFactory;
import app.model.RoleType;
import app.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserDaoJdbc implements UserDao {
    private final ConnectionFactory factory;
    private final RoleDao roleDao;

    public UserDaoJdbc(ConnectionFactory factory, RoleDao roleDao) {
        this.factory = factory;
        this.roleDao = roleDao;
    }

    @Override
    public long create(User user) {
        String sql = "INSERT INTO usuarios (username, email, password_hash, activo) VALUES (?, ?, ?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setBoolean(4, user.isActive());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    user.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando usuario", e);
        }
        throw new RuntimeException("No se pudo crear usuario");
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE usuarios SET username = ?, email = ?, password_hash = ?, activo = ? WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setBoolean(4, user.isActive());
            ps.setLong(5, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando usuario", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM usuarios WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando usuario", e);
        }
    }

    @Override
    public User findByUsername(String username) {
        String sql = "SELECT id, username, email, password_hash, activo, creado_en FROM usuarios WHERE username = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = map(rs);
                    user.clearRoles();
                    user.getRoles().addAll(getRoles(user.getId()));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario", e);
        }
        return null;
    }

    @Override
    public User findById(long id) {
        String sql = "SELECT id, username, email, password_hash, activo, creado_en FROM usuarios WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = map(rs);
                    user.clearRoles();
                    user.getRoles().addAll(getRoles(user.getId()));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario", e);
        }
        return null;
    }

    @Override
    public List<User> listAll() {
        String sql = "SELECT id, username, email, password_hash, activo, creado_en FROM usuarios ORDER BY id";
        List<User> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User user = map(rs);
                user.clearRoles();
                user.getRoles().addAll(getRoles(user.getId()));
                list.add(user);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando usuarios", e);
        }
        return list;
    }

    @Override
    public void setRoles(long userId, List<RoleType> roles) {
        try (Connection conn = factory.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM usuario_roles WHERE usuario_id = ?")) {
                del.setLong(1, userId);
                del.executeUpdate();
            }
            String sql = "INSERT INTO usuario_roles (usuario_id, rol_id) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (RoleType role : roles) {
                    long roleId = roleDao.ensureRole(role);
                    ps.setLong(1, userId);
                    ps.setLong(2, roleId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error asignando roles", e);
        }
    }

    @Override
    public List<RoleType> getRoles(long userId) {
        String sql = "SELECT r.nombre FROM roles r JOIN usuario_roles ur ON r.id = ur.rol_id WHERE ur.usuario_id = ?";
        List<RoleType> roles = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(RoleType.valueOf(rs.getString("nombre")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error obteniendo roles", e);
        }
        return roles;
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getBoolean("activo"),
                rs.getTimestamp("creado_en").toLocalDateTime()
        );
    }
}

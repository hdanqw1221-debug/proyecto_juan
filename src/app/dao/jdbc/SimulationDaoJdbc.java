package app.dao.jdbc;

import app.dao.SimulationDao;
import app.db.ConnectionFactory;
import app.model.Simulation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SimulationDaoJdbc implements SimulationDao {
    private final ConnectionFactory factory;

    public SimulationDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long create(Simulation simulation) {
        String sql = "INSERT INTO simulaciones (curso_id, nombre, descripcion, configuracion) VALUES (?, ?, ?, ?::jsonb)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, simulation.getCourseId());
            ps.setString(2, simulation.getName());
            ps.setString(3, simulation.getDescription());
            ps.setString(4, simulation.getConfigJson());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando simulacion", e);
        }
        throw new RuntimeException("No se pudo crear simulacion");
    }

    @Override
    public List<Simulation> listByCourse(long courseId) {
        String sql = "SELECT id, curso_id, nombre, descripcion, configuracion, creado_en FROM simulaciones WHERE curso_id = ? ORDER BY id";
        List<Simulation> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Simulation(
                            rs.getLong("id"),
                            rs.getLong("curso_id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getString("configuracion"),
                            rs.getTimestamp("creado_en").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando simulaciones", e);
        }
        return list;
    }

    @Override
    public Simulation findById(long simulationId) {
        String sql = "SELECT id, curso_id, nombre, descripcion, configuracion, creado_en FROM simulaciones WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Simulation(
                            rs.getLong("id"),
                            rs.getLong("curso_id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getString("configuracion"),
                            rs.getTimestamp("creado_en").toLocalDateTime()
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando simulacion por id", e);
        }
        return null;
    }

    @Override
    public void updateEnabled(long simulationId, boolean enabled) {
        String sql = "UPDATE simulaciones " +
                "SET configuracion = COALESCE(configuracion, '{}'::jsonb) || ?::jsonb " +
                "WHERE id = ?";
        String payload = enabled ? "{\"habilitado\": true}" : "{\"habilitado\": false}";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payload);
            ps.setLong(2, simulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando estado del JSON de simulacion", e);
        }
    }

    @Override
    public void updateDetails(long simulationId, String name, String description, String configJson) {
        String sql = "UPDATE simulaciones SET nombre = ?, descripcion = ?, configuracion = ?::jsonb WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, configJson);
            ps.setLong(4, simulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando detalles de simulacion", e);
        }
    }

    @Override
    public void delete(long simulationId) {
        String sql = "DELETE FROM simulaciones WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, simulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando simulacion", e);
        }
    }
}

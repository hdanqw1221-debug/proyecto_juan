package app.dao.jdbc;

import app.dao.AnatomicalStructureDao;
import app.db.ConnectionFactory;
import app.model.AnatomicalStructure;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Acceso a las fichas anatómicas de las cavidades (db/cavidades_schema.sql). */
public class AnatomicalStructureDaoJdbc implements AnatomicalStructureDao {
    private static final String SELECT_BASE = "SELECT e.*, c.codigo AS cavidad_codigo, s.codigo AS sistema_codigo "
            + "FROM estructuras_anatomicas e "
            + "JOIN cavidades c ON c.id = e.cavidad_id "
            + "JOIN sistemas_anatomicos s ON s.id = e.sistema_id ";

    private final ConnectionFactory factory;

    public AnatomicalStructureDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public AnatomicalStructure findByFichaId(String fichaId) {
        List<AnatomicalStructure> list = query(SELECT_BASE + "WHERE e.ficha_id = ?", fichaId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AnatomicalStructure findByObjectId(String objectId) {
        List<AnatomicalStructure> list = query(SELECT_BASE
                + "JOIN modelos_3d m ON m.ficha_id = e.ficha_id WHERE m.objeto_id = ?", objectId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AnatomicalStructure> listByCavity(String cavityCode) {
        return query(SELECT_BASE + "WHERE c.codigo = ? ORDER BY e.ficha_id", cavityCode);
    }

    @Override
    public List<AnatomicalStructure> listAll() {
        return query(SELECT_BASE + "ORDER BY e.ficha_id");
    }

    private List<AnatomicalStructure> query(String sql, String... params) {
        List<AnatomicalStructure> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando estructuras anatomicas", e);
        }
        return list;
    }

    private AnatomicalStructure map(ResultSet rs) throws SQLException {
        AnatomicalStructure s = new AnatomicalStructure();
        s.setFichaId(rs.getString("ficha_id"));
        s.setName(rs.getString("nombre"));
        s.setLatinName(rs.getString("nombre_latino"));
        s.setCavityCode(rs.getString("cavidad_codigo"));
        s.setSystemCode(rs.getString("sistema_codigo"));
        s.setDescription(rs.getString("descripcion"));
        s.setTopography(rs.getString("topografia"));
        s.setSurfaceProjection(rs.getString("proyeccion_superficial"));
        s.setRelations(rs.getString("relaciones"));
        s.setFixation(rs.getString("medios_de_fijacion"));
        s.setVascularization(rs.getString("irrigacion_drenaje"));
        s.setDimensions(rs.getString("dimensiones"));
        s.setClinicalNotes(rs.getString("consideraciones_clinicas"));
        s.setExamination(rs.getString("exploracion"));
        s.setReferences(textArray(rs, "referencias"));
        s.setPendingValidation(rs.getBoolean("pendiente_validacion"));
        s.setPendingFields(textArray(rs, "campos_pendientes"));
        s.setApplicableSex(rs.getString("sexo_aplicable"));
        return s;
    }

    private static List<String> textArray(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        return Arrays.asList((String[]) array.getArray());
    }
}

package app.dao.jdbc;

import app.db.ConnectionFactory;
import app.model.VisorEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla exploracion_eventos (C05). */
public class ExplorationDaoJdbc {
    private final ConnectionFactory factory;

    public ExplorationDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    /** Resumen de exploracion de un estudiante. */
    public record StudentSummary(long studentId, String username, int sessions, int structures, int selections,
                                 double seconds, LocalDateTime lastSession) {
    }

    /** Tiempo por modelo (cavidad) o por ficha. */
    public record TimeRow(String key, String name, int count, double seconds) {
    }

    public long insert(long userId, Long simulationId, VisorEvent e) {
        String sql = "INSERT INTO exploracion_eventos (usuario_id, simulacion_id, sesion, modelo, evento, objeto_id, "
                + "ficha_id, origen, t_evento) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection conn = factory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            if (simulationId == null) ps.setNull(2, Types.BIGINT); else ps.setLong(2, simulationId);
            ps.setObject(3, e.session());
            ps.setString(4, e.model());
            ps.setString(5, e.event());
            ps.setString(6, e.objectId());
            ps.setString(7, e.fichaId());
            ps.setString(8, e.origin());
            ps.setTimestamp(9, Timestamp.from(e.emittedAt()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo guardar el evento del visor", ex);
        }
    }

    /** Estudiantes matriculados en las materias indicadas, con su actividad en los modelos 3D. */
    public List<StudentSummary> summaryByCourses(List<Long> courseIds) {
        if (courseIds.isEmpty()) return List.of();
        String sql = """
                WITH alumnos AS (
                  SELECT DISTINCT u.id, u.username FROM usuarios u
                  JOIN estudiante_materias em ON em.estudiante_id = u.id
                  WHERE em.curso_id = ANY (?)
                ), sesiones AS (
                  SELECT usuario_id, sesion, min(t_evento) AS t0, max(t_evento) AS t1 FROM exploracion_eventos
                  WHERE usuario_id IN (SELECT id FROM alumnos) GROUP BY usuario_id, sesion
                )
                SELECT a.id, a.username,
                       (SELECT count(*) FROM sesiones s WHERE s.usuario_id = a.id) AS sesiones,
                       (SELECT count(DISTINCT ficha_id) FROM exploracion_eventos e
                         WHERE e.usuario_id = a.id AND e.evento = 'seleccion') AS estructuras,
                       (SELECT count(*) FROM exploracion_eventos e
                         WHERE e.usuario_id = a.id AND e.evento = 'seleccion') AS selecciones,
                       (SELECT coalesce(sum(extract(epoch FROM t1 - t0)), 0) FROM sesiones s WHERE s.usuario_id = a.id) AS segundos,
                       (SELECT max(t0) FROM sesiones s WHERE s.usuario_id = a.id) AS ultima
                FROM alumnos a ORDER BY a.username
                """;
        List<StudentSummary> rows = new ArrayList<>();
        try (Connection conn = factory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setArray(1, conn.createArrayOf("bigint", courseIds.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp last = rs.getTimestamp("ultima");
                    rows.add(new StudentSummary(rs.getLong("id"), rs.getString("username"), rs.getInt("sesiones"),
                            rs.getInt("estructuras"), rs.getInt("selecciones"), rs.getDouble("segundos"),
                            last == null ? null : last.toLocalDateTime()));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo consultar la exploracion de los estudiantes", ex);
        }
        return rows;
    }

    /**
     * Tiempo por modelo 3D (cavidad): duracion de cada sesion, desde el primer hasta el ultimo evento (si el visor
     * se cerro de forma abrupta y falta el evento "cierre", cuenta hasta el ultimo evento recibido).
     */
    public List<TimeRow> timeByModel(long studentId) {
        String sql = """
                WITH s AS (
                  SELECT sesion, coalesce(max(modelo) FILTER (WHERE evento = 'inicio'), max(modelo)) AS modelo,
                         min(t_evento) AS t0, max(t_evento) AS t1
                  FROM exploracion_eventos WHERE usuario_id = ? GROUP BY sesion
                )
                SELECT coalesce(modelo, 'desconocido') AS clave, count(*) AS n, sum(extract(epoch FROM t1 - t0)) AS seg
                FROM s GROUP BY 1 ORDER BY seg DESC, clave
                """;
        return times(sql, studentId, false);
    }

    /**
     * Tiempo por ficha: cada seleccion dura hasta el siguiente evento de la misma sesion (otra seleccion o el
     * cierre). La ultima seleccion de una sesion cerrada de forma abrupta no tiene duracion conocida y no suma.
     */
    public List<TimeRow> timeByFicha(long studentId) {
        String sql = """
                WITH e AS (
                  SELECT ficha_id, evento,
                         extract(epoch FROM lead(t_evento) OVER (PARTITION BY sesion ORDER BY t_evento, id) - t_evento) AS dur
                  FROM exploracion_eventos WHERE usuario_id = ?
                )
                SELECT e.ficha_id AS clave, coalesce(ea.nombre, e.ficha_id) AS nombre, count(*) AS n,
                       coalesce(sum(e.dur), 0) AS seg
                FROM e LEFT JOIN estructuras_anatomicas ea ON ea.ficha_id = e.ficha_id
                WHERE e.evento = 'seleccion'
                GROUP BY e.ficha_id, ea.nombre ORDER BY seg DESC, clave
                """;
        return times(sql, studentId, true);
    }

    private List<TimeRow> times(String sql, long studentId, boolean named) {
        List<TimeRow> rows = new ArrayList<>();
        try (Connection conn = factory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("clave");
                    rows.add(new TimeRow(key, named ? rs.getString("nombre") : key, rs.getInt("n"), rs.getDouble("seg")));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("No se pudo consultar el tiempo de exploracion", ex);
        }
        return rows;
    }
}

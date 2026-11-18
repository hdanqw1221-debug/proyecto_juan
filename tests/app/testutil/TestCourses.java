package app.testutil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Materia y evaluacion temporales para pruebas de integracion. */
public final class TestCourses {
    private TestCourses() {
    }

    public static long course(long teacherId) throws Exception {
        return insert("INSERT INTO cursos (nombre, profesor_id) VALUES ('Materia de prueba', ?) RETURNING id", teacherId);
    }

    public static long exam(long courseId) throws Exception {
        return insert("INSERT INTO examenes (curso_id, titulo) VALUES (?, 'Evaluacion de prueba') RETURNING id", courseId);
    }

    public static long attempt(long examId, long studentId) throws Exception {
        try (Connection c = TestDatabase.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO intentos_examen (examen_id, estudiante_id) VALUES (?, ?) RETURNING id")) {
            ps.setLong(1, examId);
            ps.setLong(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public static void deleteCourse(long courseId) throws Exception {
        try (Connection c = TestDatabase.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM cursos WHERE id = ?")) {
            ps.setLong(1, courseId);
            ps.executeUpdate();
        }
    }

    private static long insert(String sql, long param) throws Exception {
        try (Connection c = TestDatabase.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}

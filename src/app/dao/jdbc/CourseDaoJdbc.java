package app.dao.jdbc;

import app.dao.CourseDao;
import app.db.ConnectionFactory;
import app.model.Course;
import app.model.StudentCourseAssignment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDaoJdbc implements CourseDao {
    private final ConnectionFactory factory;

    public CourseDaoJdbc(ConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public long create(Course course) {
        String sql = "INSERT INTO cursos (nombre, descripcion, profesor_id) VALUES (?, ?, ?)";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, course.getName());
            ps.setString(2, course.getDescription());
            ps.setLong(3, course.getTeacherId());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando curso", e);
        }
        throw new RuntimeException("No se pudo crear curso");
    }

    @Override
    public List<Course> listAll() {
        String sql = "SELECT id, nombre, descripcion, profesor_id, creado_en FROM cursos ORDER BY id";
        List<Course> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Course(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("descripcion"),
                        rs.getLong("profesor_id"),
                        rs.getTimestamp("creado_en").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando cursos", e);
        }
        return list;
    }

    @Override
    public Course findById(long courseId) {
        String sql = "SELECT id, nombre, descripcion, profesor_id, creado_en FROM cursos WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Course(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getLong("profesor_id"),
                            rs.getTimestamp("creado_en").toLocalDateTime()
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando curso por ID", e);
        }
        return null;
    }

    @Override
    public void update(Course course) {
        String sql = "UPDATE cursos SET nombre = ?, descripcion = ?, profesor_id = ? WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, course.getName());
            ps.setString(2, course.getDescription());
            ps.setLong(3, course.getTeacherId());
            ps.setLong(4, course.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando curso", e);
        }
    }

    @Override
    public void delete(long courseId) {
        String sql = "DELETE FROM cursos WHERE id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando curso", e);
        }
    }

    @Override
    public List<Course> listByTeacher(long teacherId) {
        String sql = "SELECT id, nombre, descripcion, profesor_id, creado_en FROM cursos WHERE profesor_id = ? ORDER BY id";
        List<Course> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Course(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getLong("profesor_id"),
                            rs.getTimestamp("creado_en").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando cursos del profesor", e);
        }
        return list;
    }

    @Override
    public List<Course> listByStudent(long studentId) {
        String sql = "SELECT c.id, c.nombre, c.descripcion, c.profesor_id, c.creado_en " +
                "FROM cursos c " +
                "JOIN estudiante_materias em ON em.curso_id = c.id " +
                "WHERE em.estudiante_id = ? ORDER BY c.id";
        List<Course> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Course(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getLong("profesor_id"),
                            rs.getTimestamp("creado_en").toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando cursos del estudiante", e);
        }
        return list;
    }

    @Override
    public void assignStudentToCourse(long studentId, long courseId) {
        String sql = "INSERT INTO estudiante_materias (estudiante_id, curso_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error asignando estudiante a materia", e);
        }
    }

    @Override
    public void removeStudentFromCourse(long studentId, long courseId) {
        String sql = "DELETE FROM estudiante_materias WHERE estudiante_id = ? AND curso_id = ?";
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error quitando estudiante de materia", e);
        }
    }

    @Override
    public List<StudentCourseAssignment> listStudentAssignments() {
        String sql = "SELECT u.id AS estudiante_id, u.username, c.id AS curso_id, c.nombre, em.asignado_en " +
                "FROM estudiante_materias em " +
                "JOIN usuarios u ON u.id = em.estudiante_id " +
                "JOIN cursos c ON c.id = em.curso_id " +
                "ORDER BY u.username, c.nombre";
        List<StudentCourseAssignment> list = new ArrayList<>();
        try (Connection conn = factory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new StudentCourseAssignment(
                        rs.getLong("estudiante_id"),
                        rs.getString("username"),
                        rs.getLong("curso_id"),
                        rs.getString("nombre"),
                        rs.getTimestamp("asignado_en").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando relaciones estudiante-materia", e);
        }
        return list;
    }
}

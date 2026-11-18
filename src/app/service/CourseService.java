package app.service;

import app.session.SessionManager;
import app.security.ServiceGuard;
import app.dao.CourseDao;
import app.dao.jdbc.CourseDaoJdbc;
import app.dao.jdbc.UserDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Course;
import app.model.RoleType;
import app.model.StudentCourseAssignment;
import app.model.User;

import java.util.List;

public class CourseService {
    private final CourseDao courseDao;
    private final UserDaoJdbc userDao;

    private final ServiceGuard guard;

    /** Sin sesion: crear, editar y borrar materias y matriculas se deniega. */
    public CourseService(ConnectionFactory factory) {
        this(factory, null);
    }

    public CourseService(ConnectionFactory factory, SessionManager sessionManager) {
        this.guard = ServiceGuard.of(sessionManager);
        this.courseDao = new CourseDaoJdbc(factory);
        this.userDao = new UserDaoJdbc(factory, new app.dao.jdbc.RoleDaoJdbc(factory));
    }

    public long createCourse(String name, String description, long teacherId) {
        guard.require(RoleType.ADMINISTRADOR);
        Course course = new Course();
        course.setName(name);
        course.setDescription(description);
        course.setTeacherId(teacherId);
        return courseDao.create(course);
    }

    public List<Course> listAll() {
        return courseDao.listAll();
    }

    public Course findById(long courseId) {
        return courseDao.findById(courseId);
    }

    public void updateCourse(long courseId, String name, String description, long teacherId) {
        guard.require(RoleType.ADMINISTRADOR);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del curso es obligatorio");
        }
        Course course = courseDao.findById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("El curso no existe");
        }
        course.setName(name.trim());
        course.setDescription(description != null ? description.trim() : "");
        course.setTeacherId(teacherId);
        courseDao.update(course);
    }

    public void deleteCourse(long courseId) {
        guard.require(RoleType.ADMINISTRADOR);
        Course course = courseDao.findById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("El curso no existe");
        }
        courseDao.delete(courseId);
    }

    public String getTeacherName(long teacherId) {
        User user = userDao.findById(teacherId);
        return user != null ? user.getUsername() : ("Docente #" + teacherId);
    }

    public List<Course> listByTeacher(long teacherId) {
        return courseDao.listByTeacher(teacherId);
    }

    public List<Course> listByStudent(long studentId) {
        return courseDao.listByStudent(studentId);
    }

    public void assignStudentToCourse(long studentId, long courseId) {
        guard.require(RoleType.ADMINISTRADOR);
        validateStudentAssignment(studentId, courseId);
        courseDao.assignStudentToCourse(studentId, courseId);
    }

    public void removeStudentFromCourse(long studentId, long courseId) {
        guard.require(RoleType.ADMINISTRADOR);
        validateStudentAssignment(studentId, courseId);
        courseDao.removeStudentFromCourse(studentId, courseId);
    }

    public List<StudentCourseAssignment> listStudentAssignments() {
        return courseDao.listStudentAssignments();
    }

    private void validateStudentAssignment(long studentId, long courseId) {
        User user = userDao.findById(studentId);
        if (user == null) {
            throw new IllegalArgumentException("El estudiante no existe");
        }
        if (!user.getRoles().contains(RoleType.ESTUDIANTE)) {
            throw new IllegalArgumentException("El usuario seleccionado no tiene rol ESTUDIANTE");
        }

        boolean courseExists = courseDao.listAll().stream().anyMatch(course -> course.getId() == courseId);
        if (!courseExists) {
            throw new IllegalArgumentException("La materia seleccionada no existe");
        }
    }
}

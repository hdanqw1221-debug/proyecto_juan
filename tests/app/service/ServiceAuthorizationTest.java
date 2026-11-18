package app.service;

import app.config.AppConfig;
import app.model.RoleType;
import app.session.SessionManager;
import app.testutil.NoDatabase;
import app.testutil.TestSessions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C04: la autorizacion se comprueba dentro de los servicios. Con sesion de estudiante, cada operacion sensible
 * lanza SecurityException antes de tocar la base de datos (los servicios se crean sin conexion a proposito).
 */
class ServiceAuthorizationTest {
    private static final long STUDENT_ID = 900L;

    private static Map<String, Executable> sensitiveOperations(SessionManager sm) {
        ExamService exams = new ExamService(new NoDatabase(), sm);
        SimulationService sims = new SimulationService(null, sm);
        UserService users = new UserService(null, null, sm);
        CourseService courses = new CourseService(null, sm);
        ExamAttemptService attempts = new ExamAttemptService(new NoDatabase(), sm);
        Properties p = new Properties();
        p.setProperty("app.db.url", "jdbc:postgresql://localhost:5432/gestion");
        DatabaseBackupService backups = new DatabaseBackupService(AppConfig.fromProperties(p, n -> null), sm);
        String cfg = SimulationService.defaultConfigJson();

        Map<String, Executable> ops = new LinkedHashMap<>();
        ops.put("crear examen", () -> exams.createExam(1, "t", "d", true, null));
        ops.put("editar examen", () -> exams.updateDetails(1, "t", "d", true, null, 1, 60));
        ops.put("disponibilidad examen", () -> exams.updateAvailability(1, true, null));
        ops.put("borrar examen", () -> exams.deleteExam(1));
        ops.put("crear pregunta", () -> exams.createQuestion(1, "e", "UNICA", 1));
        ops.put("crear preguntas", () -> exams.createQuestionsWithAnswers(1, List.of()));
        ops.put("editar cuestionario", () -> exams.synchronizeQuestions(1, List.of()));
        ops.put("crear simulacion", () -> sims.createSimulation(1, "n", "d", cfg));
        ops.put("editar simulacion", () -> sims.updateDetails(1, "n", "d", cfg));
        ops.put("habilitar simulacion", () -> sims.updateEnabled(1, true));
        ops.put("borrar simulacion", () -> sims.deleteSimulation(1));
        ops.put("practicas de cavidades", () -> sims.addCavityPractices(1));
        ops.put("crear usuario", () -> users.createUser("u", "u@x.co", "Clave12345", true));
        ops.put("editar usuario", () -> users.updateUser(1, "u", "u@x.co", true));
        ops.put("restablecer contrasena", () -> users.changePassword(1, "Clave12345"));
        ops.put("borrar usuario", () -> users.deleteUser(1));
        ops.put("asignar roles", () -> users.assignRoles(STUDENT_ID, List.of(RoleType.ADMINISTRADOR)));
        ops.put("crear materia", () -> courses.createCourse("m", "d", 1));
        ops.put("editar materia", () -> courses.updateCourse(1, "m", "d", 1));
        ops.put("borrar materia", () -> courses.deleteCourse(1));
        ops.put("matricular", () -> courses.assignStudentToCourse(STUDENT_ID, 1));
        ops.put("desmatricular", () -> courses.removeStudentFromCourse(STUDENT_ID, 1));
        ops.put("recalificar intento", () -> attempts.updateScore(1, 5.0));
        ops.put("crear respaldo", () -> backups.createBackup(new File("x.backup")));
        ops.put("restaurar respaldo", () -> backups.restoreBackup(new File("x.backup")));
        return ops;
    }

    @Test
    void estudianteRecibeSecurityExceptionEnTodasLasOperacionesSensibles() {
        SessionManager student = TestSessions.as(STUDENT_ID, RoleType.ESTUDIANTE);
        sensitiveOperations(student).forEach((name, op) ->
                assertThrows(SecurityException.class, op, "con sesion de estudiante: " + name));
    }

    @Test
    void sinSesionTambienSeDeniega() {
        sensitiveOperations(new SessionManager()).forEach((name, op) ->
                assertThrows(SecurityException.class, op, "sin sesion: " + name));
        sensitiveOperations(null).forEach((name, op) ->
                assertThrows(SecurityException.class, op, "sin gestor de sesion: " + name));
    }

    @Test
    void profesorNoAdministraUsuariosNiRespaldosPeroSiSusEvaluaciones() {
        SessionManager teacher = TestSessions.as(5L, RoleType.PROFESOR);
        Map<String, Executable> ops = sensitiveOperations(teacher);
        for (String adminOnly : List.of("crear usuario", "editar usuario", "restablecer contrasena", "borrar usuario",
                "asignar roles", "crear materia", "borrar materia", "matricular", "crear respaldo", "restaurar respaldo")) {
            assertThrows(SecurityException.class, ops.get(adminOnly), "profesor: " + adminOnly);
        }
        for (String allowed : List.of("crear examen", "borrar examen", "crear simulacion", "recalificar intento")) {
            Throwable t = assertThrows(Throwable.class, ops.get(allowed), "sin base de datos la operacion falla despues");
            assertFalse(t instanceof SecurityException, "el profesor si tiene permiso: " + allowed);
        }
    }

    @Test
    void unEstudianteNoOperaSobreIntentosNiContrasenasDeOtro() {
        SessionManager student = TestSessions.as(STUDENT_ID, RoleType.ESTUDIANTE);
        ExamAttemptService attempts = new ExamAttemptService(new NoDatabase(), student);
        assertThrows(SecurityException.class, () -> attempts.startAttempt(1, STUDENT_ID + 1));
        assertThrows(SecurityException.class, () -> attempts.submitAttempt(1, 1, STUDENT_ID + 1, 5, 1, 1, "{}"));
        UserService users = new UserService(null, null, student);
        assertThrows(SecurityException.class, () -> users.changeOwnPassword(STUDENT_ID + 1, "a", "b"));
        Throwable propio = assertThrows(Throwable.class, () -> attempts.startAttempt(1, STUDENT_ID));
        assertFalse(propio instanceof SecurityException, "su propio intento si esta permitido");
    }
}

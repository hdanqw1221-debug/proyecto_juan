package app;

import app.config.AppConfig;
import app.db.ConnectionFactory;
import app.security.BCryptPasswordHasher;
import app.security.PasswordHasher;
import app.service.*;
import app.session.SessionManager;
import app.ui.GlobalExceptionHandler;
import app.ui.LoginFrame;
import app.ui.UiTheme;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        setLookAndFeel();
        AppConfig config = AppConfig.load("config/app.properties");
        ConnectionFactory connectionFactory = new ConnectionFactory(config);

        // Verificacion preventiva de conexion a PostgreSQL
        boolean dbConnected = false;
        while (!dbConnected) {
            try (java.sql.Connection conn = connectionFactory.getConnection()) {
                dbConnected = true;
            } catch (Exception ex) {
                int option = javax.swing.JOptionPane.showOptionDialog(
                        null,
                        "No se pudo conectar a la base de datos PostgreSQL.\n\n"
                                + "Detalle: " + ex.getMessage() + "\n\n"
                                + "Verifica que el servicio de PostgreSQL este iniciado y que los datos en\n"
                                + "'config/app.properties' (URL, usuario y contrasena) sean correctos.",
                        "Error de Conexion - Sistema de Gestion",
                        javax.swing.JOptionPane.DEFAULT_OPTION,
                        javax.swing.JOptionPane.ERROR_MESSAGE,
                        null,
                        new Object[]{"Reintentar", "Salir"},
                        "Reintentar"
                );
                if (option != 0) {
                    System.exit(1);
                    return;
                }
            }
        }

        PasswordHasher hasher = new BCryptPasswordHasher();

        SessionManager sessionManager = new SessionManager();

        UserService userService = new UserService(connectionFactory, hasher);
        AuthService authService = new AuthService(userService, hasher, sessionManager);
        AuditService auditService = new AuditService(connectionFactory);
        RoleService roleService = new RoleService(connectionFactory);
        roleService.ensureBaseRoles();
        userService.ensureDefaultAdmin();

        CourseService courseService = new CourseService(connectionFactory);
        ExamService examService = new ExamService(connectionFactory);
        SimulationService simulationService = new SimulationService(connectionFactory);
        ResultService resultService = new ResultService(connectionFactory);
        ExamAttemptService examAttemptService = new ExamAttemptService(connectionFactory);
        AnswerService answerService = new AnswerService(connectionFactory);

        UserImportService userImportService = new UserImportService(userService);
        DatabaseBackupService backupService = new DatabaseBackupService(config);

        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame(authService, sessionManager, auditService,
                    userService, roleService, courseService, examService, simulationService, resultService,
                    examAttemptService, answerService, userImportService, backupService);
            loginFrame.setVisible(true);
        });
    }

    private static void setLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        UiTheme.install();
    }
}

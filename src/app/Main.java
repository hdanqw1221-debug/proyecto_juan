package app;

import app.config.AppConfig;
import app.db.ConnectionFactory;
import app.db.EmbeddedDatabase;
import app.security.BCryptPasswordHasher;
import app.security.PasswordHasher;
import app.service.*;
import app.session.SessionManager;
import app.ui.GlobalExceptionHandler;
import app.ui.LoginFrame;
import app.ui.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

public class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        setLookAndFeel();
        AppConfig config = AppConfig.load("config/app.properties");
        if (config.isEmbeddedDatabase()) {
            config = startEmbeddedDatabase(config);
        }
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

    /**
     * Arranca el PostgreSQL incluido en la aplicacion (la primera vez tambien crea la base) y devuelve la
     * configuracion para conectarse a el. Al salir de la aplicacion se detiene.
     */
    private static AppConfig startEmbeddedDatabase(AppConfig config) {
        EmbeddedDatabase database;
        try {
            database = EmbeddedDatabase.fromConfig(config);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Error de Base de Datos - Sistema de Gestion",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return config;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(database::stop, "detener-postgresql"));

        while (true) {
            JWindow status = showStatus(database.isInitialized()
                    ? "Iniciando la base de datos..."
                    : "Preparando la base de datos por primera vez...");
            try {
                return database.start(config);
            } catch (Exception ex) {
                status.dispose();
                int option = JOptionPane.showOptionDialog(
                        null,
                        "No se pudo iniciar la base de datos incluida en la aplicacion.\n\n"
                                + "Detalle: " + ex.getMessage() + "\n\n"
                                + "Registro: " + database.getLogFile(),
                        "Error de Base de Datos - Sistema de Gestion",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE,
                        null,
                        new Object[]{"Reintentar", "Salir"},
                        "Reintentar"
                );
                if (option != 0) {
                    System.exit(1);
                }
            } finally {
                status.dispose();
            }
        }
    }

    private static JWindow showStatus(String message) {
        JWindow window = new JWindow();
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(22, 28, 22, 28)));
        JLabel title = UiTheme.title("Sistema de Gestion Academica", 18f);
        JLabel label = new JLabel(message);
        label.setForeground(UiTheme.MUTED);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        panel.add(title, BorderLayout.NORTH);
        panel.add(label, BorderLayout.CENTER);
        panel.add(bar, BorderLayout.SOUTH);
        window.setContentPane(panel);
        window.pack();
        window.setSize(Math.max(window.getWidth(), 420), window.getHeight());
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        return window;
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

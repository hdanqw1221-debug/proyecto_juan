package app.ui;

import app.model.User;
import app.service.*;
import app.session.SessionManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class LoginFrame extends JFrame {
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final AuditService auditService;
    private final UserService userService;
    private final RoleService roleService;
    private final CourseService courseService;
    private final ExamService examService;
    private final SimulationService simulationService;
    private final ResultService resultService;
    private final ExamAttemptService examAttemptService;
    private final AnswerService answerService;
    private final UserImportService userImportService;
    private final DatabaseBackupService databaseBackupService;

    public LoginFrame(AuthService authService,
                      SessionManager sessionManager,
                      AuditService auditService,
                      UserService userService,
                      RoleService roleService,
                      CourseService courseService,
                      ExamService examService,
                      SimulationService simulationService,
                      ResultService resultService,
                      ExamAttemptService examAttemptService,
                      AnswerService answerService) {
        this(authService, sessionManager, auditService, userService, roleService, courseService,
                examService, simulationService, resultService, examAttemptService, answerService,
                new UserImportService(userService), new DatabaseBackupService(app.config.AppConfig.load("config/app.properties")));
    }

    public LoginFrame(AuthService authService,
                      SessionManager sessionManager,
                      AuditService auditService,
                      UserService userService,
                      RoleService roleService,
                      CourseService courseService,
                      ExamService examService,
                      SimulationService simulationService,
                      ResultService resultService,
                      ExamAttemptService examAttemptService,
                      AnswerService answerService,
                      UserImportService userImportService,
                      DatabaseBackupService databaseBackupService) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.auditService = auditService;
        this.userService = userService;
        this.roleService = roleService;
        this.courseService = courseService;
        this.examService = examService;
        this.simulationService = simulationService;
        this.resultService = resultService;
        this.examAttemptService = examAttemptService;
        this.answerService = answerService;
        this.userImportService = userImportService;
        this.databaseBackupService = databaseBackupService;

        roleService.ensureBaseRoles();

        setTitle("Sistema de Gestion - Acceso");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 620));
        setSize(1120, 700);
        setLocationRelativeTo(null);

        initUi();
    }

    private void initUi() {
        JPanel panel = new LoginBackgroundPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(UiTheme.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(44, 58, 44, 58));

        JPanel intro = new JPanel();
        intro.setOpaque(false);
        intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
        intro.setBorder(BorderFactory.createEmptyBorder(22, 0, 22, 70));

        JLabel eyebrow = new JLabel("CAMPUS DIGITAL");
        eyebrow.setForeground(new Color(242, 196, 121));
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 13f));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel introTitle = new JLabel("<html>Aprender.<br>Practicar.<br><span style='color:#f2c479'>Avanzar.</span></html>");
        introTitle.setForeground(Color.WHITE);
        introTitle.setFont(new Font("Bahnschrift", Font.BOLD, 42));
        introTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel introText = new JLabel("<html>Tu espacio para gestionar cursos, evaluaciones<br>y experiencias de simulacion en un solo lugar.</html>");
        introText.setForeground(new Color(245, 237, 231));
        introText.setFont(introText.getFont().deriveFont(Font.PLAIN, 16f));
        introText.setAlignmentX(Component.LEFT_ALIGNMENT);

        intro.add(eyebrow);
        intro.add(Box.createVerticalStrut(18));
        intro.add(introTitle);
        intro.add(Box.createVerticalStrut(22));
        intro.add(introText);

        JPanel formCard = UiTheme.card(new GridBagLayout());
        formCard.setPreferredSize(new Dimension(440, 510));
        formCard.setBorder(BorderFactory.createEmptyBorder(28, 34, 28, 34));
        GridBagConstraints formC = new GridBagConstraints();
        formC.insets = new Insets(8, 10, 8, 10);
        formC.fill = GridBagConstraints.HORIZONTAL;

        JLabel crest = new JLabel("UP") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.PRIMARY);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        crest.setOpaque(false);
        crest.setForeground(Color.WHITE);
        crest.setHorizontalAlignment(SwingConstants.CENTER);
        crest.setFont(crest.getFont().deriveFont(Font.BOLD, 22f));
        crest.setPreferredSize(new Dimension(58, 58));
        formC.gridx = 0;
        formC.gridy = 0;
        formC.gridwidth = 2;
        formC.anchor = GridBagConstraints.CENTER;
        formC.fill = GridBagConstraints.NONE;
        formCard.add(crest, formC);

        JLabel title = UiTheme.title("Bienvenido de nuevo", 28f);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        formC.gridy = 1;
        formC.fill = GridBagConstraints.HORIZONTAL;
        formCard.add(title, formC);

        JLabel institution = new JLabel("Universidad de Pamplona", SwingConstants.CENTER);
        institution.setForeground(UiTheme.PRIMARY);
        institution.setFont(institution.getFont().deriveFont(Font.BOLD, 14f));
        formC.gridy = 2;
        formCard.add(institution, formC);

        JLabel hint = new JLabel("Ingresa con tus credenciales institucionales", SwingConstants.CENTER);
        hint.setForeground(UiTheme.MUTED);
        formC.gridy = 3;
        formCard.add(hint, formC);

        formC.anchor = GridBagConstraints.WEST;
        formC.fill = GridBagConstraints.HORIZONTAL;
        formC.gridy = 4; formC.gridx = 0;
        formC.insets = new Insets(18, 10, 3, 10);
        formCard.add(new JLabel("Usuario"), formC);

        JTextField username = new JTextField();
        username.setColumns(26);
        formC.gridy = 5;
        formC.insets = new Insets(3, 10, 8, 10);
        formCard.add(username, formC);

        formC.gridy = 6;
        formC.insets = new Insets(8, 10, 3, 10);
        formCard.add(new JLabel("Contrasena"), formC);

        JPasswordField password = new JPasswordField();
        password.setColumns(24);
        char passwordEcho = password.getEchoChar();
        JButton togglePassword = new JButton("Ver");
        togglePassword.setToolTipText("Mostrar u ocultar contrasena");
        togglePassword.setPreferredSize(new Dimension(72, 34));
        togglePassword.addActionListener(e -> {
            boolean hidden = password.getEchoChar() != (char) 0;
            password.setEchoChar(hidden ? (char) 0 : passwordEcho);
            togglePassword.setText(hidden ? "Ocultar" : "Ver");
        });
        JPanel passwordBox = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14); // FIELD_RADIUS = 14
                g2.dispose();
            }
        };
        passwordBox.setOpaque(false);
        passwordBox.putClientProperty("uiTheme.surface", Boolean.TRUE);
        passwordBox.setBorder(BorderFactory.createCompoundBorder(
                UiTheme.roundedBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(3, 10, 3, 5)
        ));
        passwordBox.add(password, BorderLayout.CENTER);
        passwordBox.add(togglePassword, BorderLayout.EAST);
        formC.gridy = 7;
        formC.insets = new Insets(3, 10, 12, 10);
        formCard.add(passwordBox, formC);

        JButton login = new JButton("Ingresar");
        login.setPreferredSize(new Dimension(100, 44));
        formC.gridy = 8;
        formC.insets = new Insets(10, 10, 8, 10);
        formCard.add(login, formC);

        JLabel msg = new JLabel(" ", SwingConstants.CENTER);
        msg.setForeground(UiTheme.PRIMARY_DARK);
        formC.gridy = 9;
        formC.insets = new Insets(2, 10, 0, 10);
        formCard.add(msg, formC);

        login.addActionListener(e -> {
            String u = username.getText().trim();
            String p = new String(password.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                msg.setText("Debes ingresar usuario y contrasena");
                return;
            }
            User user = authService.login(u, p);
            if (user == null) {
                msg.setText("Credenciales invalidas o usuario inactivo");
                auditService.log(null, "LOGIN_FAIL", "Usuario: " + u);
                return;
            }
            auditService.log(user.getId(), "LOGIN_OK", "Inicio de sesion");
            MainFrame main = new MainFrame(sessionManager, authService, auditService,
                    userService, roleService, courseService, examService, simulationService, resultService,
                    examAttemptService, answerService, userImportService, databaseBackupService);
            main.setVisible(true);
            dispose();
        });

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.55;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.WEST;
        panel.add(intro, c);

        c.gridx = 1;
        c.weightx = 0.45;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        panel.add(formCard, c);

        UiTheme.styleTree(panel);
        password.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
        password.setOpaque(false);
        togglePassword.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        togglePassword.setBackground(UiTheme.SURFACE);
        togglePassword.setForeground(UiTheme.MUTED);
        togglePassword.setFocusPainted(false);
        togglePassword.setContentAreaFilled(false);
        formCard.setBackground(new Color(255, 255, 253, 244));
        crest.setBackground(UiTheme.PRIMARY);
        crest.setForeground(Color.WHITE);
        institution.setForeground(UiTheme.PRIMARY_DARK);
        setContentPane(panel);
    }

    private static final class LoginBackgroundPanel extends JPanel {
        private java.awt.Image backgroundImage;

        private LoginBackgroundPanel() {
            setOpaque(true);
            try {
                java.io.File file = resolveAssetFile("fondo_login.jpg");
                if (file.exists()) {
                    backgroundImage = javax.imageio.ImageIO.read(file);
                }
            } catch (Exception e) {
                // Ignore and use default colors
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (backgroundImage != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                // Draw background filling the screen (cover behavior)
                double scale = Math.max((double) getWidth() / backgroundImage.getWidth(null),
                                        (double) getHeight() / backgroundImage.getHeight(null));
                scale *= 1.15; // Zoom in 15% to hide the image's baked-in gray borders
                int w = (int) (backgroundImage.getWidth(null) * scale);
                int h = (int) (backgroundImage.getHeight(null) * scale);
                int x = (getWidth() - w) / 2;
                int y = (getHeight() - h) / 2;
                g2.drawImage(backgroundImage, x, y, w, h, null);
                
                GradientPaint overlay = new GradientPaint(
                        0, 0, new Color(50, 10, 18, 220),
                        getWidth(), 0, new Color(40, 25, 25, 120)
                );
                g2.setPaint(overlay);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setPaint(new GradientPaint(
                        0, getHeight() / 2f, new Color(0, 0, 0, 0),
                        0, getHeight(), new Color(20, 8, 10, 100)
                ));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                g2.setPaint(new GradientPaint(
                        0, 0, UiTheme.NAV_DARK,
                        getWidth(), getHeight(), UiTheme.PRIMARY
                ));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fillOval(getWidth() - 300, -100, 360, 360);
                g2.setColor(new Color(242, 196, 121, 35));
                g2.fillOval(-90, getHeight() - 250, 330, 330);
            }
            g2.dispose();
        }

        private static File resolveAssetFile(String fileName) {
            String[] candidates = {
                    "assets/" + fileName,
                    "app/assets/" + fileName
            };

            for (String candidate : candidates) {
                File file = new File(candidate);
                if (file.exists()) {
                    return file;
                }
            }

            String launcherPath = System.getProperty("jpackage.app-path");
            if (launcherPath != null && !launcherPath.isBlank()) {
                File launcher = new File(launcherPath);
                File root = launcher.getParentFile();
                if (root != null) {
                    File inRootAssets = new File(root, "assets/" + fileName);
                    if (inRootAssets.exists()) {
                        return inRootAssets;
                    }
                    File inAppAssets = new File(root, "app/assets/" + fileName);
                    if (inAppAssets.exists()) {
                        return inAppAssets;
                    }
                }
            }

            return new File("assets/" + fileName);
        }
    }
}

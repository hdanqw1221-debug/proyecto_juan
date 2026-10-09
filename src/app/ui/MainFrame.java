package app.ui;

import app.model.RoleType;
import app.model.User;
import app.service.*;
import app.session.SessionManager;
import app.ui.panels.AdminPanel;
import app.ui.panels.DashboardPanel;
import app.ui.panels.StudentPanel;
import app.ui.panels.TeacherPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainFrame extends JFrame {
    private static final int NAV_OPEN_WIDTH = 264;
    private static final int NAV_CLOSED_WIDTH = 76;

    private final SessionManager sessionManager;
    private final AuthService authService;
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

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel content = new JPanel(cardLayout) {
        private java.awt.Image watermark;
        {
            try {
                java.io.File file = resolveAssetFile("logo.png");
                if (file.exists()) watermark = javax.imageio.ImageIO.read(file);
            } catch (Exception e) {}
        }
        @Override
        protected void paintChildren(Graphics g) {
            if (watermark != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.045f));
                
                int size = Math.min(getWidth(), getHeight()) / 2;
                int w = size;
                int h = (int) (size * ((double) watermark.getHeight(null) / watermark.getWidth(null)));
                int x = (getWidth() - w) / 2;
                int y = (getHeight() - h) / 2;
                g2.drawImage(watermark, x, y, w, h, null);
                g2.dispose();
            }
            super.paintChildren(g);
        }
    };
    private final JPanel nav = new NavigationPanel();
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final JLabel screenTitle = UiTheme.title("Resumen general", 22f);
    private boolean navCollapsed;

    public MainFrame(SessionManager sessionManager,
                     AuthService authService,
                     AuditService auditService,
                     UserService userService,
                     RoleService roleService,
                     CourseService courseService,
                     ExamService examService,
                     SimulationService simulationService,
                     ResultService resultService,
                     ExamAttemptService examAttemptService,
                     AnswerService answerService) {
        this(sessionManager, authService, auditService, userService, roleService, courseService,
                examService, simulationService, resultService, examAttemptService, answerService,
                new UserImportService(userService), new DatabaseBackupService(app.config.AppConfig.load("config/app.properties")));
    }

    public MainFrame(SessionManager sessionManager,
                     AuthService authService,
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
        this.sessionManager = sessionManager;
        this.authService = authService;
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

        setTitle("Sistema de Gestion Academica");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setSize(1240, 780);
        setLocationRelativeTo(null);

        initUi();
    }

    private void initUi() {
        User user = sessionManager.getCurrent().getUser();

        nav.removeAll();
        navButtons.clear();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createEmptyBorder(22, 14, 20, 14));
        nav.setBackground(UiTheme.NAV);
        nav.setPreferredSize(new Dimension(NAV_OPEN_WIDTH, 10));

        JPanel navHeader = new JPanel(new BorderLayout(10, 0));
        navHeader.setOpaque(false);
        navHeader.setMaximumSize(new Dimension(236, 70));
        navHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brand = new JLabel("<html><b>UNIVERSIDAD</b><br><span style='color:#e8bd78'>DE PAMPLONA</span></html>");
        brand.putClientProperty("fullText", "<html><b>UNIVERSIDAD</b><br><span style='color:#e8bd78'>DE PAMPLONA</span></html>");
        brand.putClientProperty("shortText", "UP");
        brand.setForeground(Color.WHITE);
        brand.setFont(new Font("Bahnschrift", Font.BOLD, 18));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        navHeader.add(brand, BorderLayout.CENTER);

        JButton toggle = new JButton("<<");
        toggle.putClientProperty("fullText", "<<");
        toggle.putClientProperty("shortText", ">>");
        toggle.setToolTipText("Abrir o cerrar menu");
        toggle.setPreferredSize(new Dimension(42, 42));
        toggle.setBackground(new Color(255, 255, 255, 22));
        toggle.setForeground(Color.WHITE);
        toggle.setOpaque(true);
        toggle.setContentAreaFilled(true);
        toggle.setFocusPainted(false);
        toggle.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.addActionListener(e -> toggleNavigation());
        navHeader.add(toggle, BorderLayout.EAST);
        nav.add(navHeader);
        nav.add(Box.createVerticalStrut(26));

        JButton btnDashboard = makeNavButton("Inicio", "DASH", "IN");
        nav.add(btnDashboard);

        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            nav.add(makeSectionLabel("ADMIN"));
            nav.add(makeNavButton("Administracion", "ADMIN", "AD"));
        }
        if (user.getRoles().contains(RoleType.ADMINISTRADOR) || user.getRoles().contains(RoleType.PROFESOR)) {
            nav.add(makeSectionLabel("DOCENCIA"));
            nav.add(makeNavButton("Gestion Academica", "TEACHER", "GA"));
        }
        if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
            nav.add(makeSectionLabel("ESTUDIANTE"));
            nav.add(makeNavButton("Estudiante", "STUDENT", "ES"));
        }

        nav.add(Box.createVerticalGlue());
        JButton btnProfile = makeNavButton("Mi Perfil", "PROFILE", "PR");
        nav.add(btnProfile);
        nav.add(Box.createVerticalStrut(6));
        JButton logout = makeNavButton("Salir", "LOGOUT", "SA");
        nav.add(logout);

        DashboardPanel dashboard = new DashboardPanel(user, courseService, examService,
                simulationService, examAttemptService, auditService);
        content.add(dashboard, "DASH");

        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            AdminPanel adminPanel = new AdminPanel(sessionManager, auditService, userService, roleService,
                    courseService, userImportService, databaseBackupService);
            content.add(adminPanel, "ADMIN");
        }
        if (user.getRoles().contains(RoleType.ADMINISTRADOR) || user.getRoles().contains(RoleType.PROFESOR)) {
            TeacherPanel teacherPanel = new TeacherPanel(sessionManager, auditService, courseService, examService,
                    simulationService, answerService, examAttemptService, resultService);
            content.add(teacherPanel, "TEACHER");
        }
        if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
            StudentPanel studentPanel = new StudentPanel(sessionManager, auditService, courseService, examService,
                    simulationService, resultService, examAttemptService, answerService);
            content.add(studentPanel, "STUDENT");
        }

        for (Component comp : nav.getComponents()) {
            if (comp instanceof JButton btn) {
                String cmd = btn.getActionCommand();
                if (cmd != null && (cmd.equals("DASH") || cmd.equals("ADMIN") || cmd.equals("TEACHER") || cmd.equals("STUDENT"))) {
                    btn.addActionListener(e -> {
                        if ("DASH".equals(cmd)) {
                            dashboard.refresh();
                        }
                        cardLayout.show(content, cmd);
                        updateNavActive(cmd);
                    });
                }
            }
        }
        updateNavActive("DASH");

        btnProfile.addActionListener(e -> openProfileDialog(user));

        logout.addActionListener(e -> {
            auditService.log(user.getId(), "LOGOUT", "Cierre de sesion");
            authService.logout();
            LoginFrame loginFrame = new LoginFrame(authService, sessionManager, auditService,
                    userService, roleService, courseService, examService, simulationService, resultService, examAttemptService, answerService);
            loginFrame.setVisible(true);
            dispose();
        });

        content.setBackground(UiTheme.BACKGROUND);
        UiTheme.styleTree(content);

        JPanel topBar = new GlassTopBarPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(13, 24, 13, 24));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel product = new JLabel("GESTION ACADEMICA");
        product.setForeground(UiTheme.PRIMARY);
        product.setFont(product.getFont().deriveFont(Font.BOLD, 10f));
        product.setAlignmentX(Component.LEFT_ALIGNMENT);
        screenTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(product);
        heading.add(Box.createVerticalStrut(2));
        heading.add(screenTitle);

        java.time.format.DateTimeFormatter dateFormat = java.time.format.DateTimeFormatter.ofPattern(
                "EEEE, d MMMM", java.util.Locale.forLanguageTag("es-CO")
        );
        JLabel date = new JLabel(java.time.LocalDate.now().format(dateFormat));
        date.setForeground(UiTheme.MUTED);

        String roleName = user.getRoles().contains(RoleType.ADMINISTRADOR) ? "Administrador"
                : (user.getRoles().contains(RoleType.PROFESOR) ? "Docente" : "Estudiante");
        String username = user.getUsername() == null || user.getUsername().isBlank() ? "Usuario" : user.getUsername();
        JLabel currentUser = new JLabel("<html><b>" + username + "</b><br><span style='color:#696971'>"
                + roleName + "</span></html>");
        currentUser.setForeground(UiTheme.INK);

        JLabel avatar = new JLabel(username.substring(0, 1).toUpperCase(), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.PRIMARY_SOFT);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setForeground(UiTheme.PRIMARY_DARK);
        avatar.setFont(avatar.getFont().deriveFont(Font.BOLD, 14f));
        avatar.setPreferredSize(new Dimension(38, 38));

        JPanel userPill = new TranslucentPillPanel(new FlowLayout(FlowLayout.LEFT, 9, 4));
        userPill.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 12));
        userPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userPill.setToolTipText("Haz clic para ver tu perfil y seguridad de la cuenta");
        userPill.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openProfileDialog(user);
            }
        });
        userPill.add(avatar);
        userPill.add(currentUser);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        topRight.setOpaque(false);
        topRight.add(date);
        topRight.add(userPill);
        topBar.add(heading, BorderLayout.WEST);
        topBar.add(topRight, BorderLayout.EAST);

        JPanel appShell = new AtmospherePanel(new BorderLayout());
        appShell.add(topBar, BorderLayout.NORTH);
        appShell.add(nav, BorderLayout.WEST);
        appShell.add(content, BorderLayout.CENTER);

        UiTheme.enableGlassEffect(appShell);
        setContentPane(appShell);
    }

    private void openProfileDialog(User user) {
        ProfileDialog dialog = new ProfileDialog(this, user, userService, auditService);
        dialog.setVisible(true);
    }

    private JButton makeNavButton(String text, String action, String shortText) {
        JButton btn = new NavButton(shortText + "   " + text);
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btn.putClientProperty("fullText", shortText + "   " + text);
        btn.putClientProperty("shortText", shortText);
        btn.putClientProperty("active", Boolean.FALSE);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(236, 48));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBackground(UiTheme.NAV);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setActionCommand(action);
        navButtons.put(action, btn);
        return btn;
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

    private JLabel makeSectionLabel(String text) {
        JLabel label = new JLabel(" " + text);
        label.setForeground(new Color(255, 222, 224));
        label.putClientProperty("sectionLabel", Boolean.TRUE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(18, 0, 6, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void toggleNavigation() {
        navCollapsed = !navCollapsed;
        int width = navCollapsed ? NAV_CLOSED_WIDTH : NAV_OPEN_WIDTH;
        nav.setPreferredSize(new Dimension(width, nav.getHeight()));

        for (Component comp : nav.getComponents()) {
            if (comp instanceof JLabel label) {
                if (Boolean.TRUE.equals(label.getClientProperty("sectionLabel"))) {
                    label.setVisible(!navCollapsed);
                } else {
                    Object text = label.getClientProperty(navCollapsed ? "shortText" : "fullText");
                    if (text instanceof String value) {
                        label.setText(value);
                    }
                    label.setHorizontalAlignment(navCollapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
                }
            } else if (comp instanceof JPanel panel) {
                panel.setMaximumSize(new Dimension(navCollapsed ? 42 : 236, 70));
                for (Component child : panel.getComponents()) {
                    if (child instanceof JLabel label) {
                        Object text = label.getClientProperty(navCollapsed ? "shortText" : "fullText");
                        if (text instanceof String value) {
                            label.setText(value);
                        }
                        label.setHorizontalAlignment(navCollapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
                    } else if (child instanceof JButton button) {
                        Object text = button.getClientProperty(navCollapsed ? "shortText" : "fullText");
                        if (text instanceof String value) {
                            button.setText(value);
                        }
                    }
                }
            } else if (comp instanceof JButton button) {
                Object text = button.getClientProperty(navCollapsed ? "shortText" : "fullText");
                if (text instanceof String value) {
                    button.setText(value);
                }
                button.setHorizontalAlignment(navCollapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
            }
        }

        nav.revalidate();
        nav.repaint();
    }

    private void updateNavActive(String action) {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton button = entry.getValue();
            boolean active = entry.getKey().equals(action);
            button.putClientProperty("active", active);
            button.setForeground(active ? UiTheme.NAV_DARK : Color.WHITE);
            button.repaint();
        }

        screenTitle.setText(switch (action) {
            case "ADMIN" -> "Administracion";
            case "TEACHER" -> "Gestion academica";
            case "STUDENT" -> "Mi espacio de estudio";
            default -> "Resumen general";
        });
    }

    private static final class NavButton extends JButton {
        private boolean hovered;

        private NavButton(String text) {
            super(text);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean active = Boolean.TRUE.equals(getClientProperty("active"));
            if (active) {
                g2.setColor(new Color(255, 248, 239));
                g2.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 14, 14);
                g2.setColor(UiTheme.ACCENT);
                g2.fillRoundRect(4, 12, 4, getHeight() - 24, 4, 4);
            } else if (hovered) {
                g2.setColor(new Color(255, 255, 255, 20));
                g2.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 14, 14);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class AtmospherePanel extends JPanel {
        private AtmospherePanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(
                    0, 0, new Color(246, 239, 235),
                    getWidth(), getHeight(), new Color(226, 237, 240)
            ));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(177, 45, 55, 28));
            g2.fillOval(getWidth() - 430, -230, 620, 620);
            g2.setColor(new Color(205, 145, 63, 30));
            g2.fillOval(120, getHeight() - 360, 520, 420);
            g2.setColor(new Color(42, 103, 138, 20));
            g2.fillOval(getWidth() / 3, 90, 460, 460);

            g2.setColor(new Color(86, 68, 62, 11));
            for (int y = 22; y < getHeight(); y += 36) {
                for (int x = 22; x < getWidth(); x += 36) {
                    g2.fillOval(x, y, 2, 2);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class NavigationPanel extends JPanel {
        private NavigationPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(
                    0, 0, new Color(57, 16, 23, 247),
                    getWidth(), getHeight(), new Color(96, 31, 40, 235)
            ));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(205, 145, 63, 30));
            g2.fillOval(-150, getHeight() - 310, 390, 390);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class GlassTopBarPanel extends JPanel {
        private GlassTopBarPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(
                    0, 0, new Color(255, 255, 253, 224),
                    getWidth(), getHeight(), new Color(255, 255, 255, 174)
            ));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(255, 255, 255, 145));
            g2.drawLine(0, 0, getWidth(), 0);
            g2.setColor(new Color(UiTheme.BORDER.getRed(), UiTheme.BORDER.getGreen(),
                    UiTheme.BORDER.getBlue(), 155));
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class TranslucentPillPanel extends JPanel {
        private TranslucentPillPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 164));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(new Color(UiTheme.BORDER.getRed(), UiTheme.BORDER.getGreen(),
                    UiTheme.BORDER.getBlue(), 165));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}

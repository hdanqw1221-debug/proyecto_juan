package app.ui.panels;

import app.model.RoleType;
import app.model.User;
import app.service.AuditService;
import app.service.CourseService;
import app.service.ExamAttemptService;
import app.service.ExamService;
import app.service.SimulationService;
import app.ui.PieChartPanel;
import app.ui.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DashboardPanel extends JPanel {
    private final User user;
    private final CourseService courseService;
    private final ExamService examService;
    private final SimulationService simulationService;
    private final ExamAttemptService examAttemptService;
    private final AuditService auditService;
    private final JPanel contentContainer = new JPanel(new BorderLayout(16, 16));

    public DashboardPanel(User user, CourseService courseService, ExamService examService,
                          SimulationService simulationService, ExamAttemptService examAttemptService,
                          AuditService auditService) {
        this.user = user;
        this.courseService = courseService;
        this.examService = examService;
        this.simulationService = simulationService;
        this.examAttemptService = examAttemptService;
        this.auditService = auditService;

        setLayout(new BorderLayout(18, 18));
        setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));
        setBackground(UiTheme.BACKGROUND);

        add(new WelcomePanel(user), BorderLayout.NORTH);
        contentContainer.setOpaque(false);
        add(contentContainer, BorderLayout.CENTER);

        refresh();
        UiTheme.styleTree(this);
    }

    public void refresh() {
        contentContainer.removeAll();

        int courses = countCourses(user, courseService);
        int exams = countExams(user, courseService, examService);
        int simulations = countSimulations(user, courseService, simulationService);
        int attempts = safeCountAttempts(user, examAttemptService);

        JPanel metrics = new JPanel(new GridLayout(1, 4, 14, 14));
        metrics.setOpaque(false);
        metrics.add(metricCard("MA", "Materias", courses, "Cursos disponibles", UiTheme.INFO));
        metrics.add(metricCard("EX", "Cuestionarios", exams, "Evaluaciones registradas", UiTheme.PRIMARY));
        metrics.add(metricCard("3D", "Simulaciones", simulations, "Practicas configuradas", UiTheme.WARNING));
        metrics.add(metricCard("IN", "Intentos", attempts, "Actividad academica", UiTheme.SUCCESS));
        contentContainer.add(metrics, BorderLayout.NORTH);

        JPanel detailGrid = new JPanel(new GridLayout(1, 3, 14, 14));
        detailGrid.setOpaque(false);
        detailGrid.add(rolesCard(user));
        detailGrid.add(activityCard(auditService));
        detailGrid.add(chartCard(courses, exams, simulations));
        contentContainer.add(detailGrid, BorderLayout.CENTER);

        JPanel alert = upcomingAlert(user, courseService, examService);
        if (alert != null) {
            contentContainer.add(alert, BorderLayout.SOUTH);
        }

        UiTheme.styleTree(contentContainer);
        contentContainer.revalidate();
        contentContainer.repaint();
    }

    private JPanel metricCard(String code, String title, int value, String caption, Color accent) {
        JPanel card = new MetricCard(accent);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(UiTheme.MUTED);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        top.add(titleLabel, BorderLayout.WEST);
        top.add(new PillLabel(code, accent, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28)),
                BorderLayout.EAST);

        JLabel valueLabel = UiTheme.title(String.valueOf(value), 32f);
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setForeground(UiTheme.MUTED);
        captionLabel.setFont(captionLabel.getFont().deriveFont(12f));

        card.add(top, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(captionLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel rolesCard(User user) {
        JPanel card = UiTheme.card(new BorderLayout(12, 12));
        card.add(sectionHeading("Tu perfil", "Permisos activos en esta sesion"), BorderLayout.NORTH);

        JPanel badges = new JPanel();
        badges.setOpaque(false);
        badges.setLayout(new BoxLayout(badges, BoxLayout.Y_AXIS));
        for (RoleType role : user.getRoles()) {
            String label = switch (role) {
                case ADMINISTRADOR -> "Administrador";
                case PROFESOR -> "Docente / Profesor";
                case ESTUDIANTE -> "Estudiante";
            };
            PillLabel badge = new PillLabel(label, UiTheme.PRIMARY_DARK, UiTheme.PRIMARY_SOFT);
            badge.setAlignmentX(Component.LEFT_ALIGNMENT);
            badges.add(badge);
            badges.add(Box.createVerticalStrut(8));
        }
        badges.add(Box.createVerticalGlue());
        card.add(badges, BorderLayout.CENTER);

        JLabel help = new JLabel("<html>Las opciones del menu se ajustan<br>a estos permisos.</html>");
        help.setForeground(UiTheme.MUTED);
        card.add(help, BorderLayout.SOUTH);
        return card;
    }

    private JPanel activityCard(AuditService auditService) {
        JPanel card = UiTheme.card(new BorderLayout(12, 12));
        card.add(sectionHeading("Actividad reciente", "Ultimos movimientos del sistema"), BorderLayout.NORTH);

        JPanel items = new JPanel();
        items.setOpaque(false);
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        try {
            auditService.listRecent(4).forEach(entry -> {
                JLabel item = new JLabel("<html><b>" + entry.getAction() + "</b><br><span style='color:#696971'>"
                        + entry.getCreatedAt() + "</span></html>");
                item.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                items.add(item);
                items.add(Box.createVerticalStrut(8));
            });
        } catch (RuntimeException ex) {
            JLabel empty = new JLabel("Sin actividad disponible por ahora.");
            empty.setForeground(UiTheme.MUTED);
            items.add(empty);
        }
        if (items.getComponentCount() == 0) {
            JLabel empty = new JLabel("Aun no hay actividad registrada.");
            empty.setForeground(UiTheme.MUTED);
            items.add(empty);
        }
        card.add(items, BorderLayout.CENTER);
        return card;
    }

    private JPanel chartCard(int courses, int exams, int simulations) {
        JPanel card = UiTheme.card(new BorderLayout());
        PieChartPanel chart = new PieChartPanel("Distribucion academica");
        chart.addSlice("Cursos", courses, UiTheme.INFO);
        chart.addSlice("Examenes", exams, UiTheme.SUCCESS);
        chart.addSlice("Simulaciones", simulations, UiTheme.WARNING);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private JPanel upcomingAlert(User user, CourseService courseService, ExamService examService) {
        if (!user.getRoles().contains(RoleType.ESTUDIANTE)) {
            return null;
        }

        int upcoming = 0;
        try {
            for (Long courseId : visibleCourseIds(user, courseService)) {
                for (app.model.Exam exam : examService.listExamsByCourse(courseId)) {
                    if (exam.isActive() && exam.getDueAt() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(
                                java.time.LocalDateTime.now(), exam.getDueAt()
                        );
                        if (days >= 0 && days <= 7) {
                            upcoming++;
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            return null;
        }

        if (upcoming == 0) {
            return null;
        }

        JPanel alert = UiTheme.card(new BorderLayout(12, 0));
        alert.setBackground(UiTheme.WARNING_SOFT);
        alert.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, UiTheme.WARNING),
                BorderFactory.createEmptyBorder(13, 16, 13, 16)
        ));
        PillLabel label = new PillLabel("AVISO", UiTheme.WARNING, new Color(255, 255, 255, 130));
        alert.add(label, BorderLayout.WEST);
        alert.add(new JLabel("Tienes " + upcoming + " examen(es) que vencen en los proximos 7 dias."),
                BorderLayout.CENTER);
        return alert;
    }

    private JPanel sectionHeading(String title, String subtitle) {
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = UiTheme.title(title, 18f);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(UiTheme.MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(3));
        heading.add(subtitleLabel);
        return heading;
    }

    private int countCourses(User user, CourseService courseService) {
        try {
            if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
                return courseService.listByStudent(user.getId()).size();
            }
            if (user.getRoles().contains(RoleType.PROFESOR) && !user.getRoles().contains(RoleType.ADMINISTRADOR)) {
                return courseService.listByTeacher(user.getId()).size();
            }
            return courseService.listAll().size();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private int countExams(User user, CourseService courseService, ExamService examService) {
        try {
            return visibleCourseIds(user, courseService).stream()
                    .mapToInt(courseId -> examService.listExamsByCourse(courseId).size())
                    .sum();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private int countSimulations(User user, CourseService courseService, SimulationService simulationService) {
        try {
            return visibleCourseIds(user, courseService).stream()
                    .mapToInt(courseId -> simulationService.listByCourse(courseId).size())
                    .sum();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private int safeCountAttempts(User user, ExamAttemptService examAttemptService) {
        try {
            if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
                return examAttemptService.listByStudent(user.getId()).size();
            }
            return examAttemptService.countAll();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private List<Long> visibleCourseIds(User user, CourseService courseService) {
        if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
            return courseService.listByStudent(user.getId()).stream().map(course -> course.getId()).toList();
        }
        if (user.getRoles().contains(RoleType.PROFESOR) && !user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            return courseService.listByTeacher(user.getId()).stream().map(course -> course.getId()).toList();
        }
        return courseService.listAll().stream().map(course -> course.getId()).toList();
    }

    private static final class WelcomePanel extends JPanel {
        private WelcomePanel(User user) {
            setLayout(new BorderLayout(20, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(23, 26, 23, 26));
            setPreferredSize(new Dimension(100, 128));

            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
            JLabel eyebrow = new JLabel("TU CAMPUS, HOY");
            eyebrow.setForeground(new Color(241, 197, 122));
            eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
            eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel title = UiTheme.title("Hola, " + user.getUsername(), 28f);
            title.setForeground(Color.WHITE);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel subtitle = new JLabel("Este es el resumen de tu actividad academica.");
            subtitle.setForeground(new Color(245, 230, 229));
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            copy.add(eyebrow);
            copy.add(Box.createVerticalStrut(5));
            copy.add(title);
            copy.add(Box.createVerticalStrut(5));
            copy.add(subtitle);
            add(copy, BorderLayout.CENTER);

            PillLabel status = new PillLabel("SESION ACTIVA", Color.WHITE, new Color(255, 255, 255, 28));
            add(status, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, UiTheme.NAV_DARK, getWidth(), getHeight(), UiTheme.PRIMARY));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillOval(getWidth() - 190, -100, 250, 250);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class MetricCard extends JPanel {
        private final Color accent;

        private MetricCard(Color accent) {
            super(new BorderLayout(8, 8));
            this.accent = accent;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(17, 18, 17, 18));
            setPreferredSize(new Dimension(100, 126));
            putClientProperty("uiTheme.surface", Boolean.TRUE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(50, 35, 30, 11));
            g2.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 4, 18, 18);
            g2.setColor(UiTheme.isGlassEffect(this)
                    ? new Color(UiTheme.SURFACE.getRed(), UiTheme.SURFACE.getGreen(),
                            UiTheme.SURFACE.getBlue(), 218)
                    : UiTheme.SURFACE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 3, 18, 18);
            if (UiTheme.isGlassEffect(this)) {
                g2.setPaint(new GradientPaint(
                        0, 0, new Color(255, 255, 255, 72),
                        0, getHeight(), new Color(255, 255, 255, 6)
                ));
                g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 5, 18, 18);
            }
            g2.setColor(accent);
            g2.fillRoundRect(0, 0, getWidth() - 1, 5, 5, 5);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class PillLabel extends JLabel {
        private final Color fill;

        private PillLabel(String text, Color foreground, Color fill) {
            super(text);
            this.fill = fill;
            setForeground(foreground);
            setFont(getFont().deriveFont(Font.BOLD, 11f));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}

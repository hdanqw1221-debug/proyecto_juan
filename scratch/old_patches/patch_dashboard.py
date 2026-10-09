with open('src/app/ui/panels/DashboardPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

# 1. Update class fields and constructor
old_class_start = """public class DashboardPanel extends JPanel {
    public DashboardPanel(User user, CourseService courseService, ExamService examService,
                          SimulationService simulationService, ExamAttemptService examAttemptService,
                          AuditService auditService) {
        setLayout(new BorderLayout(18, 18));
        setBorder(BorderFactory.createEmptyBorder(22, 24, 24, 24));
        setBackground(UiTheme.BACKGROUND);

        int courses = countCourses(user, courseService);
        int exams = countExams(user, courseService, examService);
        int simulations = countSimulations(user, courseService, simulationService);
        int attempts = safeCountAttempts(user, examAttemptService);

        add(new WelcomePanel(user), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setOpaque(false);

        JPanel metrics = new JPanel(new GridLayout(1, 4, 14, 14));
        metrics.setOpaque(false);
        metrics.add(metricCard("MA", "Materias", courses, "Cursos disponibles", UiTheme.INFO));
        metrics.add(metricCard("EX", "Cuestionarios", exams, "Evaluaciones registradas", UiTheme.PRIMARY));
        metrics.add(metricCard("3D", "Simulaciones", simulations, "Practicas configuradas", UiTheme.WARNING));
        metrics.add(metricCard("IN", "Intentos", attempts, "Actividad academica", UiTheme.SUCCESS));
        content.add(metrics, BorderLayout.NORTH);

        JPanel detailGrid = new JPanel(new GridLayout(1, 3, 14, 14));
        detailGrid.setOpaque(false);
        detailGrid.add(rolesCard(user));
        detailGrid.add(activityCard(auditService));
        detailGrid.add(chartCard(courses, exams, simulations));
        content.add(detailGrid, BorderLayout.CENTER);

        JPanel alert = upcomingAlert(user, courseService, examService);
        if (alert != null) {
            content.add(alert, BorderLayout.SOUTH);
        }

        add(content, BorderLayout.CENTER);
        UiTheme.styleTree(this);
    }"""

new_class_start = """public class DashboardPanel extends JPanel {
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
    }"""

assert old_class_start in text, 'old_class_start not found'
text = text.replace(old_class_start, new_class_start, 1)

# 2. Update rolesCard label
old_role_label = 'String label = role == RoleType.ADMINISTRADOR ? "Administrador" : "Estudiante";'
new_role_label = """String label = switch (role) {
                case ADMINISTRADOR -> "Administrador";
                case PROFESOR -> "Docente / Profesor";
                case ESTUDIANTE -> "Estudiante";
            };"""
assert old_role_label in text, 'old_role_label not found'
text = text.replace(old_role_label, new_role_label, 1)

# 3. Update safeCountAttempts
old_safe_count = """    private int safeCountAttempts(User user, ExamAttemptService examAttemptService) {
        try {
            return examAttemptService.listByStudent(user.getId()).size();
        } catch (RuntimeException ex) {
            return 0;
        }
    }"""

new_safe_count = """    private int safeCountAttempts(User user, ExamAttemptService examAttemptService) {
        try {
            if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
                return examAttemptService.listByStudent(user.getId()).size();
            }
            return examAttemptService.countAll();
        } catch (RuntimeException ex) {
            return 0;
        }
    }"""
assert old_safe_count in text, 'old_safe_count not found'
text = text.replace(old_safe_count, new_safe_count, 1)

# 4. Update countCourses & visibleCourseIds for PROFESOR
old_count_courses = """    private int countCourses(User user, CourseService courseService) {
        try {
            if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
                return courseService.listByStudent(user.getId()).size();
            }
            return courseService.listAll().size();
        } catch (RuntimeException ex) {
            return 0;
        }
    }"""

new_count_courses = """    private int countCourses(User user, CourseService courseService) {
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
    }"""
assert old_count_courses in text, 'old_count_courses not found'
text = text.replace(old_count_courses, new_count_courses, 1)

old_visible_courses = """    private List<Long> visibleCourseIds(User user, CourseService courseService) {
        if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
            return courseService.listByStudent(user.getId()).stream().map(course -> course.getId()).toList();
        }
        return courseService.listAll().stream().map(course -> course.getId()).toList();
    }"""

new_visible_courses = """    private List<Long> visibleCourseIds(User user, CourseService courseService) {
        if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
            return courseService.listByStudent(user.getId()).stream().map(course -> course.getId()).toList();
        }
        if (user.getRoles().contains(RoleType.PROFESOR) && !user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            return courseService.listByTeacher(user.getId()).stream().map(course -> course.getId()).toList();
        }
        return courseService.listAll().stream().map(course -> course.getId()).toList();
    }"""
assert old_visible_courses in text, 'old_visible_courses not found'
text = text.replace(old_visible_courses, new_visible_courses, 1)

with open('src/app/ui/panels/DashboardPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('DashboardPanel updated successfully!')

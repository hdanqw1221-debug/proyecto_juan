package app.ui.panels;

import app.model.Course;
import app.model.Exam;
import app.model.ExamAttempt;
import app.model.Result;
import app.model.RoleType;
import app.model.Simulation;
import app.model.User;
import app.security.AccessControl;
import app.service.AnswerService;
import app.service.AuditService;
import app.service.CertificateService;
import app.service.CourseService;
import app.service.ExamAttemptService;
import app.service.ExamService;
import app.service.ResultService;
import app.service.SimulationService;
import app.service.Visor3DService;
import app.session.SessionManager;
import app.ui.ExamDialog;
import app.ui.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StudentPanel extends JPanel {

    private final SessionManager sessionManager;
    private final AuditService auditService;
    private final CourseService courseService;
    private final ExamService examService;
    private final SimulationService simulationService;
    private final ResultService resultService;
    private final ExamAttemptService examAttemptService;
    private final AnswerService answerService;

    private final DefaultTableModel coursesModel =
            new DefaultTableModel(new Object[]{"Materia", "Docente Titular", "Descripcion"}, 0);
    private final DefaultTableModel simulationsModel =
            new DefaultTableModel(new Object[]{"ID", "Materia", "Simulacion", "Modelo 3D", "Descripcion"}, 0);
    private final DefaultTableModel examsModel =
            new DefaultTableModel(new Object[]{"ID", "Materia", "Evaluacion", "Descripcion", "Cierre", "Estado"}, 0);
    private final DefaultTableModel gradesModel =
            new DefaultTableModel(new Object[]{"Intento", "Materia", "Evaluacion", "Inicio", "Fin", "Nota", "Estado"}, 0);

    private final JComboBox<ComboItem> simulationCourseSelector = new JComboBox<>();
    private final JComboBox<ComboItem> examCourseSelector = new JComboBox<>();
    private final JLabel simulationSummary = new JLabel("Selecciona una materia.");
    private final JLabel examSummary = new JLabel("Selecciona una materia.");
    private final JLabel gradesSummary = new JLabel("Sin notas cargadas.");
    private final JLabel coursesSummary = new JLabel("Cargando materias...");
    private final JLabel pythonScriptsSummary = new JLabel("Preparando catalogo 3D...");
    private final JTextField selectedSimulationId = new JTextField(8);
    private final JTextField selectedExamId = new JTextField(8);
    private final JTextField selectedAttemptId = new JTextField(8);
    private final JLabel selectedSimulationName = new JLabel("Selecciona una practica en la tabla.");
    private final JLabel selectedExamName = new JLabel("Selecciona una evaluacion en la tabla.");
    private final JLabel selectedResultName = new JLabel("Selecciona una calificacion en la tabla.");
    private final JPanel pythonScriptsButtonsPanel = new JPanel(new GridLayout(0, 2, 14, 14));

    public StudentPanel(SessionManager sessionManager, AuditService auditService,
                        CourseService courseService, ExamService examService,
                        SimulationService simulationService, ResultService resultService,
                        ExamAttemptService examAttemptService, AnswerService answerService) {
        this.sessionManager = sessionManager;
        this.auditService = auditService;
        this.courseService = courseService;
        this.examService = examService;
        this.simulationService = simulationService;
        this.resultService = resultService;
        this.examAttemptService = examAttemptService;
        this.answerService = answerService;

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setBackground(UiTheme.BACKGROUND);

        JPanel header = buildHeader("Portal estudiante", "Materias, simuladores, cuestionarios y notas en un solo lugar.");
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Materias", buildCoursesPanel());
        tabs.add("Practicas", buildSimPanel());
        tabs.add("Modelos 3D", buildPythonPanel());
        tabs.add("Evaluaciones", buildExamPanel());
        tabs.add("Calificaciones", buildGradesPanel());
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        refreshStudentData();
        UiTheme.styleTree(this);
    }

    private JPanel buildHeader(String title, String subtitle) {
        JPanel header = UiTheme.card(new BorderLayout(6, 6));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, UiTheme.PRIMARY),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)
        ));
        header.add(UiTheme.title(title, 24f), BorderLayout.NORTH);
        JLabel detail = new JLabel(subtitle);
        detail.setForeground(UiTheme.MUTED);
        header.add(detail, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildCoursesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JButton refresh = new JButton("Actualizar Materias");
        JTable table = new JTable(coursesModel);
        JTextField search = searchField("Buscar materia...");
        installSearch(table, search);

        refresh.addActionListener(e -> refreshStudentData());

        JPanel top = new JPanel(new BorderLayout(12, 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        actions.add(search);
        actions.add(refresh);
        top.add(actions, BorderLayout.WEST);
        coursesSummary.setForeground(UiTheme.MUTED);
        top.add(coursesSummary, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSimPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel top = new JPanel(new BorderLayout(14, 0));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton refresh = new JButton("Ver Simulaciones");
        simulationCourseSelector.setPreferredSize(new Dimension(220, 40));
        simulationCourseSelector.setMinimumSize(new Dimension(180, 40));
        filters.add(new JLabel("Materia"));
        filters.add(simulationCourseSelector);
        filters.add(refresh);
        top.add(filters, BorderLayout.WEST);
        simulationSummary.setForeground(UiTheme.MUTED);
        simulationSummary.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        top.add(simulationSummary, BorderLayout.EAST);

        JTable table = new JTable(simulationsModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(210);
        table.getColumnModel().getColumn(3).setPreferredWidth(380);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 240));
        tableScroll.setMinimumSize(new Dimension(420, 170));
        panel.add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectedSimulationId.setEditable(false);
        JButton run = new JButton("Ejecutar Simulador");
        selectedSimulationName.setForeground(UiTheme.MUTED);
        bottom.add(selectedSimulationName);
        bottom.add(run);

        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        simulationCourseSelector.addActionListener(e -> loadSimulations());
        refresh.addActionListener(e -> loadSimulations());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    selectedSimulationId.setText(String.valueOf(simulationsModel.getValueAt(row, 0)));
                    selectedSimulationName.setText("Practica seleccionada: "
                            + simulationsModel.getValueAt(row, 2));
                }
            }
        });

        run.addActionListener(e -> {
            requireStudent();
            if (selectedSimulationId.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Selecciona una simulacion habilitada.");
                return;
            }
            long simulationId = Long.parseLong(selectedSimulationId.getText().trim());
            ComboItem courseItem = selectedItem(simulationCourseSelector);
            Simulation simulation = courseItem != null ? findSimulation(courseItem.id, simulationId) : null;
            auditService.log(currentStudentId(), "SIM_RUN", "Simulacion ID " + simulationId);
            if (simulation == null) {
                JOptionPane.showMessageDialog(this, "No se pudo cargar la configuracion de la simulacion.");
                return;
            }
            showSimulationPracticeDialog(simulation);
        });

        return panel;
    }

    private void showSimulationPracticeDialog(Simulation simulation) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Practica: " + simulation.getName(), true);
        dialog.setSize(680, 520);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setOpaque(false);
        JLabel title = UiTheme.title(simulation.getName(), 18f);
        JLabel desc = new JLabel(simulation.getDescription() != null ? simulation.getDescription() : "");
        desc.setForeground(UiTheme.MUTED);
        header.add(title, BorderLayout.NORTH);
        header.add(desc, BorderLayout.CENTER);

        JTextArea area = new JTextArea(SimulationService.summarizeSimulation(simulation));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        String scriptName = SimulationService.getScript3D(simulation);
        Path scriptsFolder = Visor3DService.carpetaModelos();
        Path scriptPath = (scriptName != null && scriptsFolder != null) ? scriptsFolder.resolve(scriptName) : null;

        if (scriptPath != null && Files.isRegularFile(scriptPath)) {
            JButton launch3d = new JButton("Abrir Modelo 3D (" + Visor3DService.titulo(scriptName) + ")");
            UiTheme.styleButton(launch3d);
            launch3d.addActionListener(ev -> {
                auditService.log(currentStudentId(), "SIM_RUN_3D_PRACTICE", "Simulacion " + simulation.getName() + " -> " + scriptName);
                runPythonScript(scriptPath);
                dialog.dispose();
            });
            actions.add(launch3d);
        } else if (scriptName != null) {
            JLabel missing = new JLabel("El modelo 3D de esta practica (" + scriptName + ") no esta disponible en este equipo.");
            missing.setForeground(UiTheme.WARNING);
            actions.add(missing);
        }

        JButton close = new JButton("Cerrar");
        UiTheme.styleSecondaryButton(close);
        close.addActionListener(ev -> dialog.dispose());
        actions.add(close);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private JPanel buildPythonPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));

        JPanel header = UiTheme.card(new BorderLayout(6, 6));
        header.add(UiTheme.title("Explora los modelos anatomicos", 21f), BorderLayout.NORTH);
        pythonScriptsSummary.setForeground(UiTheme.MUTED);
        header.add(pythonScriptsSummary, BorderLayout.CENTER);
        panel.add(header, BorderLayout.NORTH);

        pythonScriptsButtonsPanel.setOpaque(false);
        pythonScriptsButtonsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane pythonScriptsScroll = new JScrollPane(pythonScriptsButtonsPanel);
        pythonScriptsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pythonScriptsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        pythonScriptsScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(pythonScriptsScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton refreshPython = new JButton("Actualizar catalogo");
        actions.add(refreshPython);
        panel.add(actions, BorderLayout.SOUTH);

        refreshPython.addActionListener(e -> refreshPythonScripts());
        return panel;
    }

    private JPanel buildExamPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton refresh = new JButton("Actualizar");
        JTextField search = searchField("Buscar evaluacion...");
        examCourseSelector.setPreferredSize(new Dimension(210, 40));
        filters.add(new JLabel("Materia"));
        filters.add(examCourseSelector);
        filters.add(search);
        filters.add(refresh);
        top.add(filters, BorderLayout.WEST);
        examSummary.setForeground(UiTheme.MUTED);
        top.add(examSummary, BorderLayout.EAST);

        JTable table = new JTable(examsModel);
        hideColumn(table, 0);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installSearch(table, search);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectedExamId.setEditable(false);
        JButton start = new JButton("Comenzar evaluacion");
        selectedExamName.setForeground(UiTheme.MUTED);
        bottom.add(selectedExamName);
        bottom.add(start);
        panel.add(bottom, BorderLayout.SOUTH);

        examCourseSelector.addActionListener(e -> loadExams());
        refresh.addActionListener(e -> loadExams());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    selectedExamId.setText(String.valueOf(examsModel.getValueAt(modelRow, 0)));
                    selectedExamName.setText("Evaluacion seleccionada: "
                            + examsModel.getValueAt(modelRow, 2));
                }
            }
        });

        start.addActionListener(e -> {
            requireStudent();
            if (selectedExamId.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Selecciona un cuestionario activo.");
                return;
            }

            long examId = Long.parseLong(selectedExamId.getText().trim());
            long studentId = currentStudentId();
            Exam exam = examService.findExam(examId);
            if (!examService.isAvailable(exam)) {
                JOptionPane.showMessageDialog(this, "Esta evaluacion ya no esta disponible.");
                loadExams();
                return;
            }
            if (hasReachedAttemptLimit(examAttemptService.listByStudent(studentId), exam)) {
                JOptionPane.showMessageDialog(this,
                        "Ya utilizaste los " + exam.getMaxAttempts() + " intento(s) permitidos.");
                loadExams();
                return;
            }

            int questionCount = examService.listQuestionsByExam(examId).size();
            if (questionCount == 0) {
                JOptionPane.showMessageDialog(this,
                        "Esta evaluacion todavia no tiene preguntas disponibles.");
                return;
            }

            int confirmation = JOptionPane.showConfirmDialog(this,
                    "<html><b>" + exam.getTitle() + "</b><br><br>"
                            + questionCount + " pregunta(s)<br>"
                            + exam.getDurationMinutes() + " minutos<br>"
                            + exam.getMaxAttempts() + " intento(s) permitidos<br><br>"
                            + "<span style='color:#237d5b; font-weight:bold;'>&#10004; Modo seguro: Orden aleatorio de preguntas y alternativas</span><br><br>"
                            + "El tiempo comenzara al continuar.</html>",
                    "Antes de comenzar",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (confirmation != JOptionPane.OK_OPTION) {
                return;
            }

            ExamDialog dialog = new ExamDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    examId, studentId, examService, answerService, examAttemptService, resultService);
            auditService.log(studentId, "EXAM_START", "Examen ID " + examId);
            dialog.setVisible(true);
            loadGrades();
            loadExams();
        });

        return panel;
    }

    private JPanel buildGradesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JButton refresh = new JButton("Actualizar");
        JTable table = new JTable(gradesModel);
        JTextField search = searchField("Buscar calificacion...");
        hideColumn(table, 0);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installSearch(table, search);

        refresh.addActionListener(e -> loadGrades());

        JPanel top = new JPanel(new BorderLayout(12, 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        actions.add(search);
        actions.add(refresh);
        top.add(actions, BorderLayout.WEST);
        gradesSummary.setForeground(UiTheme.MUTED);
        top.add(gradesSummary, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        selectedAttemptId.setEditable(false);
        JButton detail = new JButton("Ver resultado");
        JButton certificateBtn = new JButton("Descargar Certificado");
        UiTheme.styleButton(certificateBtn);
        selectedResultName.setForeground(UiTheme.MUTED);
        bottom.add(selectedResultName);
        bottom.add(detail);
        bottom.add(certificateBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    selectedAttemptId.setText(String.valueOf(gradesModel.getValueAt(modelRow, 0)));
                    selectedResultName.setText("Calificacion seleccionada: "
                            + gradesModel.getValueAt(modelRow, 2));
                }
            }
        });

        detail.addActionListener(e -> showResultDetail());
        certificateBtn.addActionListener(e -> generateCertificateForSelectedAttempt());
        return panel;
    }

    private void refreshStudentData() {
        requireStudent();
        loadCourses();
        populateCourseSelectors();
        loadSimulations();
        loadExams();
        loadGrades();
        refreshPythonScripts();
    }

    private void loadCourses() {
        coursesModel.setRowCount(0);
        List<Course> courses = studentCourses();
        for (Course course : courses) {
            String teacherName = courseService.getTeacherName(course.getTeacherId());
            coursesModel.addRow(new Object[]{
                    course.getName(),
                    teacherName,
                    course.getDescription()
            });
        }
        coursesSummary.setText(courses.isEmpty()
                ? "Aun no tienes materias asignadas."
                : courses.size() + " materia(s) inscritas");
    }

    private void populateCourseSelectors() {
        Long selectedSimulationCourse = selectedId(simulationCourseSelector);
        Long selectedExamCourse = selectedId(examCourseSelector);
        List<Course> courses = studentCourses();

        fillCourseCombo(simulationCourseSelector, courses);
        fillCourseCombo(examCourseSelector, courses);

        selectComboItem(simulationCourseSelector, selectedSimulationCourse);
        selectComboItem(examCourseSelector, selectedExamCourse);
    }

    private void loadSimulations() {
        simulationsModel.setRowCount(0);
        selectedSimulationId.setText("");
        selectedSimulationName.setText("Selecciona una practica en la tabla.");

        List<Course> courses = studentCourses();
        ComboItem courseItem = selectedItem(simulationCourseSelector);
        if (courses.isEmpty() || courseItem == null) {
            simulationSummary.setText("No tienes materias asignadas.");
            return;
        }

        List<Simulation> simulations = simulationService.listEnabledByCourse(courseItem.id);
        for (Simulation simulation : simulations) {
            String script3d = SimulationService.getScript3D(simulation);
            simulationsModel.addRow(new Object[]{
                    simulation.getId(),
                    courseItem.label,
                    simulation.getName(),
                    script3d != null ? Visor3DService.titulo(script3d) : "Teorica",
                    simulation.getDescription()
            });
        }
        simulationSummary.setText("Simulaciones habilitadas: " + simulations.size());
    }

    private void loadExams() {
        examsModel.setRowCount(0);
        selectedExamId.setText("");
        selectedExamName.setText("Selecciona una evaluacion en la tabla.");

        List<Course> courses = studentCourses();
        ComboItem courseItem = selectedItem(examCourseSelector);
        if (courses.isEmpty() || courseItem == null) {
            examSummary.setText("No tienes materias asignadas.");
            return;
        }

        List<Exam> exams = new ArrayList<>();
        List<ExamAttempt> attempts = examAttemptService.listByStudent(currentStudentId());
        for (Exam exam : examService.listExamsByCourse(courseItem.id)) {
            boolean limitReached = hasReachedAttemptLimit(attempts, exam);
            if (examService.isAvailable(exam) && !limitReached) {
                exams.add(exam);
                examsModel.addRow(new Object[]{
                        exam.getId(),
                        courseItem.label,
                        exam.getTitle(),
                        exam.getDescription(),
                        formatDate(exam.getDueAt()),
                        "Disponible"
                });
            }
        }
        examSummary.setText(exams.isEmpty()
                ? "No hay cuestionarios disponibles para esta materia."
                : "Cuestionarios disponibles: " + exams.size());
    }

    private void loadGrades() {
        gradesModel.setRowCount(0);
        selectedAttemptId.setText("");
        selectedResultName.setText("Selecciona una calificacion en la tabla.");
        long studentId = currentStudentId();
        List<Course> courses = studentCourses();
        if (courses.isEmpty()) {
            gradesSummary.setText("No tienes materias asignadas.");
            return;
        }

        List<ExamAttempt> attempts = examAttemptService.listByStudent(studentId);
        List<Result> results = resultService.listByStudent(studentId);

        int shownAttempts = 0;
        double total = 0;
        int graded = 0;
        for (ExamAttempt attempt : attempts) {
            Exam exam = findExam(attempt.getExamId(), courses);
            if (exam == null) {
                continue;
            }
            Course course = findCourse(courses, exam.getCourseId());
            Result result = findResult(results, attempt.getId());
            gradesModel.addRow(new Object[]{
                    attempt.getId(),
                    course != null ? course.getName() : "Materia no encontrada",
                    exam.getTitle(),
                    formatDate(attempt.getStartAt()),
                    attempt.getEndAt() != null ? formatDate(attempt.getEndAt()) : "En curso",
                    attempt.getTotalScore(),
                    result != null ? "Calificado" : "Pendiente"
            });
            shownAttempts++;
            if (attempt.getTotalScore() != null) {
                total += attempt.getTotalScore();
                graded++;
            }
        }

        gradesSummary.setText(
                "Intentos: " + shownAttempts +
                        " | Calificados: " + graded +
                        " | Promedio: " + formatAverage(total, graded)
        );
    }

    private void showResultDetail() {
        requireStudent();
        if (selectedAttemptId.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Selecciona un intento para ver el detalle.");
            return;
        }

        long attemptId = Long.parseLong(selectedAttemptId.getText().trim());
        Result result = findResult(resultService.listByStudent(currentStudentId()), attemptId);
        if (result == null) {
            JOptionPane.showMessageDialog(this, "Este intento todavia no tiene un resultado disponible.");
            return;
        }

        ExamAttempt attempt = examAttemptService.findById(attemptId);
        Exam exam = attempt != null ? examService.findExam(attempt.getExamId()) : null;
        Course course = (exam != null) ? courseService.findById(exam.getCourseId()) : null;
        User student = sessionManager.getCurrent().getUser();
        app.ui.ExamAttemptReviewDialog reviewDialog = new app.ui.ExamAttemptReviewDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                attempt, exam, result, examService, answerService, course, student
        );
        reviewDialog.setVisible(true);
    }

    private void generateCertificateForSelectedAttempt() {
        requireStudent();
        if (selectedAttemptId.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Selecciona una calificacion en la tabla primero.");
            return;
        }

        long attemptId = Long.parseLong(selectedAttemptId.getText().trim());
        ExamAttempt attempt = examAttemptService.findById(attemptId);
        if (attempt == null) {
            JOptionPane.showMessageDialog(this, "No se encontro el intento seleccionado.");
            return;
        }

        if (attempt.getTotalScore() == null) {
            JOptionPane.showMessageDialog(this, "Esta evaluacion aun no ha sido calificada.");
            return;
        }

        if (attempt.getTotalScore() < CertificateService.PASSING_SCORE) {
            JOptionPane.showMessageDialog(this,
                    "El certificado de aprobacion solo se expide para calificaciones iguales o superiores a "
                            + CertificateService.PASSING_SCORE + " / 5.0.\n\n"
                            + "Calificacion obtenida: " + String.format(Locale.ROOT, "%.1f", attempt.getTotalScore()) + " / 5.0",
                    "Evaluacion No Aprobada",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Exam exam = examService.findExam(attempt.getExamId());
        Course course = (exam != null) ? courseService.findById(exam.getCourseId()) : null;
        User student = sessionManager.getCurrent().getUser();

        try {
            CertificateService certService = new CertificateService();
            File generated = certService.exportAndOpenCertificate(student, course, exam, attempt);
            auditService.log(student.getId(), "CERTIFICATE_DOWNLOAD",
                    "Certificado generado para evaluacion: " + (exam != null ? exam.getTitle() : attemptId));

            JOptionPane.showMessageDialog(this,
                    "Certificado generado exitosamente.\n\n"
                            + "Archivo: " + generated.getAbsolutePath() + "\n\n"
                            + "Se ha abierto en tu navegador web. Desde alli puedes imprimirlo o guardarlo en formato PDF.",
                    "Certificado Generado",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al generar el certificado: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private Result findResult(List<Result> results, long attemptId) {
        for (Result result : results) {
            if (result.getAttemptId() == attemptId) {
                return result;
            }
        }
        return null;
    }

    private void fillCourseCombo(JComboBox<ComboItem> combo, List<Course> courses) {
        combo.removeAllItems();
        for (Course course : courses) {
            combo.addItem(new ComboItem(course.getId(), course.getName()));
        }
    }

    private ComboItem selectedItem(JComboBox<ComboItem> combo) {
        Object selected = combo.getSelectedItem();
        return selected instanceof ComboItem item ? item : null;
    }

    private Long selectedId(JComboBox<ComboItem> combo) {
        ComboItem item = selectedItem(combo);
        return item != null ? item.id : null;
    }

    private void selectComboItem(JComboBox<ComboItem> combo, Long id) {
        if (id == null) {
            if (combo.getItemCount() > 0) {
                combo.setSelectedIndex(0);
            }
            return;
        }

        for (int i = 0; i < combo.getItemCount(); i++) {
            ComboItem item = combo.getItemAt(i);
            if (item.id == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }

        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private void requireStudent() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ESTUDIANTE);
    }

    private long currentStudentId() {
        return sessionManager.getCurrent().getUser().getId();
    }

    private List<Course> studentCourses() {
        return courseService.listByStudent(currentStudentId());
    }

    private Simulation findSimulation(long courseId, long simulationId) {
        for (Simulation simulation : simulationService.listEnabledByCourse(courseId)) {
            if (simulation.getId() == simulationId) {
                return simulation;
            }
        }
        return null;
    }

    private boolean hasReachedAttemptLimit(List<ExamAttempt> attempts, Exam exam) {
        int usedAttempts = 0;
        for (ExamAttempt attempt : attempts) {
            if (attempt.getExamId() == exam.getId()) {
                usedAttempts++;
            }
        }
        return usedAttempts >= Math.max(1, exam.getMaxAttempts());
    }

    private Course findCourse(List<Course> courses, long courseId) {
        for (Course course : courses) {
            if (course.getId() == courseId) {
                return course;
            }
        }
        return null;
    }

    private Exam findExam(long examId, List<Course> courses) {
        for (Course course : courses) {
            for (Exam exam : examService.listExamsByCourse(course.getId())) {
                if (exam.getId() == examId) {
                    return exam;
                }
            }
        }
        return null;
    }

    private void refreshPythonScripts() {
        pythonScriptsButtonsPanel.removeAll();

        Path scriptsFolder = Visor3DService.carpetaModelos();
        List<Visor3DService.Modelo> models = Visor3DService.disponibles(scriptsFolder);
        if (scriptsFolder == null) {
            pythonScriptsSummary.setText("El catalogo 3D no esta disponible en este equipo.");
        } else if (models.isEmpty()) {
            pythonScriptsSummary.setText("Aun no hay modelos 3D disponibles.");
        } else {
            boolean environmentReady = Visor3DService.hayPythonPropio();
            for (Visor3DService.Modelo model : models) {
                pythonScriptsButtonsPanel.add(simulationCard(model, scriptsFolder.resolve(model.script()), environmentReady));
            }
            pythonScriptsSummary.setText(models.size() + " modelos interactivos de las cavidades del equino");
        }
        pythonScriptsButtonsPanel.revalidate();
        pythonScriptsButtonsPanel.repaint();
    }

    private JPanel simulationCard(Visor3DService.Modelo model, Path script, boolean environmentReady) {
        JPanel card = UiTheme.card(new BorderLayout(12, 12));

        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        JLabel title = UiTheme.title(model.titulo(), 18f);
        JLabel type = new JLabel("MODELO 3D");
        type.setForeground(UiTheme.INFO);
        type.setFont(type.getFont().deriveFont(Font.BOLD, 11f));
        heading.add(title, BorderLayout.CENTER);
        heading.add(type, BorderLayout.EAST);

        JLabel description = new JLabel("<html>" + model.descripcion() + "</html>");
        description.setForeground(UiTheme.MUTED);

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        JLabel status = new JLabel(environmentReady ? "Listo para explorar" : "Se preparara al abrir");
        status.setForeground(environmentReady ? UiTheme.SUCCESS : UiTheme.WARNING);
        JButton open = new JButton("Abrir modelo");
        open.addActionListener(e -> runPythonScript(script));
        footer.add(status, BorderLayout.WEST);
        footer.add(open, BorderLayout.EAST);

        card.add(heading, BorderLayout.NORTH);
        card.add(description, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        UiTheme.styleTree(card);
        return card;
    }

    private void runPythonScript(Path scriptPath) {
        requireStudent();
        auditService.log(currentStudentId(), "SIM_RUN_PY", "Script " + scriptPath.getFileName());
        launchSimulation(scriptPath, false);
    }

    private void preparePythonEnvironment(Path scriptToOpen) {
        Path scriptsFolder = Visor3DService.carpetaModelos();
        if (scriptsFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "No pudimos localizar los recursos de los modelos 3D.",
                    "Modelos no disponibles",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path requirements = scriptsFolder.resolve("requeriments.txt");
        if (!Files.exists(requirements)) {
            JOptionPane.showMessageDialog(this,
                    "Faltan archivos necesarios para preparar los modelos 3D.",
                    "Preparacion no disponible",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> installer = Visor3DService.pythonParaInstalar();
        if (installer == null) {
            JOptionPane.showMessageDialog(this,
                    "Este equipo no cuenta con los componentes necesarios para abrir modelos 3D.\n"
                            + "Solicita apoyo al administrador del sistema.",
                    "Preparacion no disponible",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path environmentPython = scriptsFolder.resolve(".venv").resolve("Scripts").resolve("python.exe");
        List<List<String>> commands = new ArrayList<>();
        if (!Files.isRegularFile(environmentPython)) {
            List<String> createVenv = new ArrayList<>(installer);
            createVenv.addAll(List.of("-m", "venv", ".venv"));
            commands.add(createVenv);
        }
        commands.add(List.of(
                environmentPython.toString(), "-m", "pip", "install", "-r",
                requirements.getFileName().toString()
        ));
        executeEnvironmentSetup(commands, scriptsFolder, scriptToOpen);
    }

    private void executeEnvironmentSetup(List<List<String>> commands, Path workDir, Path scriptToOpen) {
        JDialog progress = progressDialog("Preparando modelos 3D",
                "La primera preparacion puede tardar unos minutos.");

        SwingWorker<CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CommandResult doInBackground() {
                StringBuilder output = new StringBuilder();
                for (List<String> command : commands) {
                    try {
                        Process process = new ProcessBuilder(command)
                                .directory(workDir.toFile())
                                .redirectErrorStream(true)
                                .start();
                        try (var input = process.getInputStream()) {
                            output.append(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                        }
                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            return new CommandResult(exitCode, output.toString(), null);
                        }
                    } catch (Exception ex) {
                        return new CommandResult(-1, output.toString(), ex);
                    }
                }
                Visor3DService.olvidarPython();
                return new CommandResult(Visor3DService.python() != null ? 0 : 1, output.toString(), null);
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    CommandResult result = get();
                    if (result.error != null || result.exitCode != 0) {
                        showPreparationError(scriptToOpen);
                        return;
                    }
                    refreshPythonScripts();
                    launchSimulation(scriptToOpen, true);
                } catch (Exception ex) {
                    showPreparationError(scriptToOpen);
                }
            }
        };

        worker.execute();
        progress.setVisible(true);
    }

    private void showPreparationError(Path scriptToOpen) {
        int option = JOptionPane.showOptionDialog(this,
                "No pudimos preparar el modelo 3D.\nComprueba tu conexion y vuelve a intentarlo.",
                "No se pudo completar la preparacion",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new Object[]{"Reintentar", "Cerrar"},
                "Reintentar");
        if (option == 0) {
            preparePythonEnvironment(scriptToOpen);
        }
    }

    /**
     * Abre el visor en segundo plano. El aviso de progreso se cierra cuando el visor informa de que su
     * ventana esta abierta; al cerrarlo se registra la sesion (estructuras consultadas y duracion).
     */
    private void launchSimulation(Path scriptPath, boolean afterSetup) {
        long studentId = currentStudentId();
        JDialog progress = progressDialog("Abriendo modelo 3D", "Preparando la ventana interactiva...");

        SwingWorker<Visor3DService.Resultado, Void> worker = new SwingWorker<>() {
            private boolean withoutPython;

            @Override
            protected Visor3DService.Resultado doInBackground() {
                List<String> python = Visor3DService.python();
                if (python == null) {
                    withoutPython = true;
                    return null;
                }
                return Visor3DService.ejecutar(scriptPath, python, () -> SwingUtilities.invokeLater(progress::dispose));
            }

            @Override
            protected void done() {
                progress.dispose();
                Visor3DService.Resultado result;
                try {
                    result = get();
                } catch (Exception ex) {
                    result = null;
                }
                if (withoutPython) {
                    if (afterSetup) {
                        showPreparationError(scriptPath);
                    } else {
                        preparePythonEnvironment(scriptPath);
                    }
                    return;
                }
                if (result == null) {
                    JOptionPane.showMessageDialog(StudentPanel.this,
                            "Ocurrio un problema al abrir el modelo. Intenta nuevamente.",
                            "Modelo no disponible",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                auditService.log(studentId, "SIM_3D_SESION", Visor3DService.resumen(scriptPath, result));
                if (!result.correcto()) {
                    int option = JOptionPane.showOptionDialog(
                            StudentPanel.this,
                            "El modelo no pudo abrirse correctamente.\n"
                                    + "Verifica que el equipo tenga aceleracion grafica disponible.",
                            "No se pudo abrir el modelo",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            new Object[]{"Reintentar", "Cerrar"},
                            "Reintentar"
                    );
                    if (option == 0) {
                        runPythonScript(scriptPath);
                    }
                }
            }
        };

        worker.execute();
        progress.setVisible(true);
    }

    private JDialog progressDialog(String title, String message) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), title, false);
        JPanel content = UiTheme.card(new BorderLayout(10, 10));
        content.add(UiTheme.title(title, 17f), BorderLayout.NORTH);
        content.add(new JLabel(message), BorderLayout.CENTER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        content.add(progressBar, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(390, 145);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        return dialog;
    }

    private JTextField searchField(String placeholder) {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(UiTheme.MUTED);
                    g2.setFont(getFont());
                    g2.drawString(placeholder, 13, (getHeight() + g2.getFontMetrics().getAscent()) / 2 - 2);
                    g2.dispose();
                }
            }
        };
        field.setPreferredSize(new Dimension(210, 40));
        field.setToolTipText(placeholder);
        field.getAccessibleContext().setAccessibleName(placeholder);
        return field;
    }

    private void installSearch(JTable table, JTextField search) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(
                (DefaultTableModel) table.getModel()
        );
        table.setRowSorter(sorter);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void updateFilter() {
                String value = search.getText().trim();
                sorter.setRowFilter(value.isEmpty()
                        ? null
                        : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(value)));
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateFilter();
            }
        });
    }

    private void hideColumn(JTable table, int modelIndex) {
        var column = table.getColumnModel().getColumn(modelIndex);
        column.setMinWidth(0);
        column.setMaxWidth(0);
        column.setPreferredWidth(0);
        column.setResizable(false);
    }

    private String formatDate(java.time.LocalDateTime date) {
        if (date == null) {
            return "Sin fecha";
        }
        return date.format(java.time.format.DateTimeFormatter.ofPattern(
                "d MMM yyyy, h:mm a", java.util.Locale.forLanguageTag("es-CO")
        ));
    }

    private int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int position = 0;
        while ((position = text.indexOf(token, position)) >= 0) {
            count++;
            position += token.length();
        }
        return count;
    }

    private String formatAverage(double total, int count) {
        if (count == 0) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", total / count);
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;
        private final Exception error;

        private CommandResult(int exitCode, String output, Exception error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }

    private static final class ComboItem {
        private final long id;
        private final String label;

        private ComboItem(long id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

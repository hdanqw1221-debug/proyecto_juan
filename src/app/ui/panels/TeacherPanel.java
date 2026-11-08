package app.ui.panels;

import app.model.Answer;
import app.model.Course;
import app.model.Exam;
import app.model.ExamAttempt;
import app.model.Question;
import app.model.QuestionDraft;
import app.model.RoleType;
import app.model.Simulation;
import app.security.AccessControl;
import app.service.AnswerService;
import app.service.AuditService;
import app.service.CourseService;
import app.service.ExamAttemptService;
import app.service.ExamService;
import app.service.SimulationService;
import app.service.Visor3DService;
import app.session.SessionManager;
import app.ui.QuestionBatchEditorDialog;
import app.ui.QuestionnairePreviewDialog;
import app.ui.SimulationFormDialog;
import app.ui.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TeacherPanel extends JPanel {
    private final SessionManager sessionManager;
    private final AuditService auditService;
    private final CourseService courseService;
    private final ExamService examService;
    private final SimulationService simulationService;
    private final AnswerService answerService;
    private final ExamAttemptService examAttemptService;
    private final app.service.ResultService resultService;

    private final DefaultTableModel examModel =
            new DefaultTableModel(new Object[]{"ID", "Curso", "Titulo", "Activo", "Vence", "Intentos", "Duracion (min)", "Estado", "Creado"}, 0);
    private final DefaultTableModel questionModel =
            new DefaultTableModel(new Object[]{"ID", "Examen", "Tipo", "Puntaje", "Imagen", "Enunciado"}, 0);
    private final DefaultTableModel answerModel =
            new DefaultTableModel(new Object[]{"ID", "Pregunta", "Correcta", "Contenido"}, 0);
    private final DefaultTableModel gradeModel =
            new DefaultTableModel(new Object[]{"Intento", "Examen", "Estudiante", "Inicio", "Fin", "Puntaje"}, 0);
    private final DefaultTableModel simulationModel =
            new DefaultTableModel(new Object[]{"ID", "Curso", "Nombre", "Modelo 3D", "Disponible", "Duracion"}, 0);

    private final JComboBox<ComboItem> questionnaireCourseFilter = new JComboBox<>();
    private final JComboBox<ComboItem> examCourseSelector = new JComboBox<>();
    private final JComboBox<ComboItem> questionExamSelector = new JComboBox<>();
    private final JComboBox<ComboItem> answerQuestionSelector = new JComboBox<>();
    private final JComboBox<ComboItem> gradesCourseFilter = new JComboBox<>();
    private final JComboBox<ComboItem> gradesExamFilter = new JComboBox<>();
    private final JComboBox<ComboItem> simulationCourseSelector = new JComboBox<>();

    private final JLabel questionnaireSummary = new JLabel("Sin datos cargados.");
    private final JLabel gradesSummary = new JLabel("Selecciona un examen para ver notas.");
    private final JLabel simulationSummary = new JLabel("Sin simulaciones cargadas.");
    private final JTextField selectedAttemptId = new JTextField(8);
    private final JTextField selectedScore = new JTextField(8);
    private final JTextField selectedSimulationId = new JTextField(8);
    private final JTextField selectedExamId = new JTextField(8);
    private final DateTimePicker availabilityDueAtPicker = createDateTimePicker();

    public TeacherPanel(SessionManager sessionManager, AuditService auditService,
                        CourseService courseService, ExamService examService, SimulationService simulationService,
                        AnswerService answerService, ExamAttemptService examAttemptService) {
        this(sessionManager, auditService, courseService, examService, simulationService, answerService, examAttemptService, null);
    }

    public TeacherPanel(SessionManager sessionManager, AuditService auditService,
                        CourseService courseService, ExamService examService, SimulationService simulationService,
                        AnswerService answerService, ExamAttemptService examAttemptService,
                        app.service.ResultService resultService) {
        this.sessionManager = sessionManager;
        this.auditService = auditService;
        this.courseService = courseService;
        this.examService = examService;
        this.simulationService = simulationService;
        this.answerService = answerService;
        this.examAttemptService = examAttemptService;
        this.resultService = resultService;

        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);

        JPanel header = buildHeader("Gestion academica", "Cuestionarios, simulaciones, notas y estadisticas.");
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Cuestionarios", buildQuestionnairePanel());
        tabs.add("Simulaciones", buildSimulationPanel());
        tabs.add("Notas", buildGradesPanel());
        tabs.add("Estadisticas", buildStatsPanel());

        JPanel contentWrapper = new ViewportPanel(new BorderLayout(12, 12));
        contentWrapper.setOpaque(false);
        contentWrapper.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        contentWrapper.add(header, BorderLayout.NORTH);
        contentWrapper.add(tabs, BorderLayout.CENTER);

        JScrollPane mainScroll = new JScrollPane(contentWrapper);
        mainScroll.setBorder(BorderFactory.createEmptyBorder());
        mainScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mainScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        mainScroll.setOpaque(false);
        mainScroll.getViewport().setOpaque(false);

        add(mainScroll, BorderLayout.CENTER);

        refreshTeacherData();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                refreshTeacherData();
            }
        });
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

    private JPanel buildQuestionnairePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);

        JPanel topToolbar = UiTheme.card(new BorderLayout(12, 10));
        topToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, UiTheme.INFO),
                BorderFactory.createEmptyBorder(13, 16, 13, 16)
        ));

        JPanel toolbarHeading = new JPanel(new BorderLayout(16, 0));
        toolbarHeading.setOpaque(false);
        JPanel headingCopy = new JPanel();
        headingCopy.setOpaque(false);
        headingCopy.setLayout(new BoxLayout(headingCopy, BoxLayout.Y_AXIS));
        headingCopy.add(UiTheme.title("Editor de cuestionarios", 20f));
        JLabel headingHint = new JLabel("Organiza el examen, revisa preguntas y valida respuestas antes de publicarlo.");
        headingHint.setForeground(UiTheme.MUTED);
        headingCopy.add(Box.createVerticalStrut(3));
        headingCopy.add(headingHint);
        toolbarHeading.add(headingCopy, BorderLayout.WEST);

        JPanel workflow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        workflow.setOpaque(false);
        workflow.add(workflowBadge("01", "EXAMEN"));
        workflow.add(workflowBadge("02", "PREGUNTAS"));
        workflow.add(workflowBadge("03", "RESPUESTAS"));
        toolbarHeading.add(workflow, BorderLayout.EAST);

        JPanel filters = new JPanel(new BorderLayout(12, 0));
        filters.setOpaque(false);
        JPanel filterControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterControls.setOpaque(false);
        JLabel courseLabel = new JLabel("Materia");
        courseLabel.setFont(courseLabel.getFont().deriveFont(Font.BOLD));
        filterControls.add(courseLabel);
        questionnaireCourseFilter.setPreferredSize(new Dimension(210, 40));
        filterControls.add(questionnaireCourseFilter);
        JButton refresh = new JButton("Refrescar");
        app.ui.UiTheme.styleSecondaryButton(refresh);
        filterControls.add(refresh);
        filters.add(filterControls, BorderLayout.WEST);
        questionnaireSummary.setOpaque(true);
        questionnaireSummary.setBackground(UiTheme.INFO_SOFT);
        questionnaireSummary.setForeground(UiTheme.INFO);
        questionnaireSummary.setBorder(BorderFactory.createCompoundBorder(
                UiTheme.roundedBorder(new Color(UiTheme.INFO.getRed(), UiTheme.INFO.getGreen(),
                        UiTheme.INFO.getBlue(), 80)),
                BorderFactory.createEmptyBorder(7, 11, 7, 11)
        ));
        filters.add(questionnaireSummary, BorderLayout.EAST);

        JPanel questionnaireActions = new JPanel(new BorderLayout(12, 0));
        questionnaireActions.setOpaque(false);
        JPanel primaryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        primaryActions.setOpaque(false);
        JButton btnCreateExam = new JButton("+ Crear Examen");
        app.ui.UiTheme.styleButton(btnCreateExam);
        JButton btnEditExam = new JButton("Editar Examen");
        app.ui.UiTheme.styleSecondaryButton(btnEditExam);
        JButton btnCreateQuestion = new JButton("+ Agregar Preguntas");
        app.ui.UiTheme.styleButton(btnCreateQuestion);
        JButton btnEditQuestionnaire = new JButton("Editar Preguntas");
        app.ui.UiTheme.styleSecondaryButton(btnEditQuestionnaire);
        JButton btnPreviewQuestionnaire = new JButton("Vista Previa");
        app.ui.UiTheme.styleSecondaryButton(btnPreviewQuestionnaire);
        JButton btnDeleteQuestionnaire = new JButton("Eliminar Cuestionario");
        app.ui.UiTheme.styleButton(btnDeleteQuestionnaire);

        primaryActions.add(btnCreateExam);
        primaryActions.add(btnEditExam);
        primaryActions.add(btnCreateQuestion);
        primaryActions.add(btnEditQuestionnaire);
        primaryActions.add(btnPreviewQuestionnaire);
        questionnaireActions.add(primaryActions, BorderLayout.WEST);
        questionnaireActions.add(btnDeleteQuestionnaire, BorderLayout.EAST);

        JPanel toolbarBody = new JPanel();
        toolbarBody.setOpaque(false);
        toolbarBody.setLayout(new BoxLayout(toolbarBody, BoxLayout.Y_AXIS));
        toolbarBody.add(filters);
        toolbarBody.add(Box.createVerticalStrut(9));
        toolbarBody.add(questionnaireActions);
        topToolbar.add(toolbarHeading, BorderLayout.NORTH);
        topToolbar.add(toolbarBody, BorderLayout.CENTER);

        JTable examTable = new JTable(examModel);
        JTable questionTable = new JTable(questionModel);
        JTable answerTable = new JTable(answerModel);

        JPanel pnlExam = buildTableWithSearch("Exámenes", examTable, examModel, null);
        
        JPanel pnlQuestionHeader = new JPanel(new BorderLayout(8, 0));
        pnlQuestionHeader.setOpaque(false);
        pnlQuestionHeader.add(new JLabel("Filtro:"), BorderLayout.WEST);
        pnlQuestionHeader.add(questionExamSelector, BorderLayout.CENTER);
        JPanel pnlQuestion = buildTableWithSearch("Preguntas", questionTable, questionModel, pnlQuestionHeader);
        
        JPanel pnlAnswerHeader = new JPanel(new BorderLayout(8, 0));
        pnlAnswerHeader.setOpaque(false);
        pnlAnswerHeader.add(new JLabel("Filtro:"), BorderLayout.WEST);
        pnlAnswerHeader.add(answerQuestionSelector, BorderLayout.CENTER);
        JPanel pnlAnswer = buildTableWithSearch("Respuestas", answerTable, answerModel, pnlAnswerHeader);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pnlQuestion, pnlAnswer);
        bottomSplit.setResizeWeight(0.58);
        bottomSplit.setDividerSize(8);
        bottomSplit.setContinuousLayout(true);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pnlExam, bottomSplit);
        mainSplit.setResizeWeight(0.38);
        mainSplit.setDividerSize(8);
        mainSplit.setContinuousLayout(true);
        mainSplit.setPreferredSize(new Dimension(900, 720));
        SwingUtilities.invokeLater(() -> {
            mainSplit.setDividerLocation(0.38);
            bottomSplit.setDividerLocation(0.58);
        });

        panel.add(topToolbar, BorderLayout.NORTH);
        panel.add(mainSplit, BorderLayout.CENTER);

        questionnaireCourseFilter.addActionListener(e -> refreshQuestionnaireTables());
        refresh.addActionListener(e -> refreshTeacherData());

        questionExamSelector.addActionListener(e -> {
            loadQuestionsForSelectedExam();
            loadAnswersForSelectedQuestion();
        });

        answerQuestionSelector.addActionListener(e -> loadAnswersForSelectedQuestion());

        examTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = examTable.getSelectedRow();
                if (row >= 0) {
                    long examId = tableLongValue(examTable, row, 0);
                    selectedExamId.setText(String.valueOf(examId));
                    selectComboItem(questionExamSelector, examId);
                    loadExamAvailability(examId);
                }
            }
        });

        questionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = questionTable.getSelectedRow();
                if (row >= 0) {
                    selectComboItem(answerQuestionSelector, tableLongValue(questionTable, row, 0));
                }
            }
        });

        btnCreateExam.addActionListener(e -> {
            requireTeacher();
            ComboItem courseItem = selectedItem(questionnaireCourseFilter);
            if (courseItem == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "Primero crea o selecciona un curso en el filtro global.");
                return;
            }
            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Crear Nuevo Examen", 450, 450);
            JTextField examTitle = new JTextField(14);
            JTextField examDescription = new JTextField(18);
            JCheckBox examActive = new JCheckBox("Activo", true);
            DateTimePicker examDueAt = createDateTimePicker();
            JTextField examAttempts = new JTextField("1", 5);
            JTextField examDuration = new JTextField("60", 5);

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; c.add(new JLabel("Titulo:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(examTitle, gbc);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; c.add(new JLabel("Descripcion:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(examDescription, gbc);
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; c.add(new JLabel("Estado:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(examActive, gbc);
            gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; c.add(new JLabel("Fecha limite:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(examDueAt, gbc);
            gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0; c.add(new JLabel("Intentos max:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(examAttempts, gbc);
            gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0; c.add(new JLabel("Duracion (min):"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(examDuration, gbc);

            JButton confirm = new JButton("Guardar Examen");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                try {
                    int attempts = Integer.parseInt(examAttempts.getText().trim());
                    int duration = Integer.parseInt(examDuration.getText().trim());
                    long id = examService.createExam(courseItem.id, examTitle.getText(), examDescription.getText(),
                            examActive.isSelected(), spinnerToDateTime(examDueAt), attempts, duration);
                    
                    auditService.log(currentTeacherId(), "EXAM_CREATE", "Examen ID " + id);
                    refreshTeacherData();
                    selectComboItem(questionnaireCourseFilter, courseItem.id);
                    javax.swing.JOptionPane.showMessageDialog(this, "Examen creado. ID: " + id);
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        btnCreateQuestion.addActionListener(e -> {
            requireTeacher();
            ComboItem examItem = selectedItem(questionExamSelector);
            if (examItem == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona un examen en la tabla o filtro.");
                return;
            }
            if (!canModifyQuestionnaire(examItem.id, "agregar preguntas")) {
                return;
            }
            QuestionBatchEditorDialog editor = new QuestionBatchEditorDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this), examItem.label
            );
            editor.setVisible(true);
            var questions = editor.getQuestions();
            if (questions != null && !questions.isEmpty()) {
                try {
                    List<Long> ids = examService.createQuestionsWithAnswers(examItem.id, questions);
                    auditService.log(currentTeacherId(), "QUESTION_BATCH_CREATE",
                            ids.size() + " preguntas creadas en examen " + examItem.id);
                    refreshTeacherData();
                    selectComboItem(questionExamSelector, examItem.id);
                    selectComboItem(answerQuestionSelector, ids.get(ids.size() - 1));
                    JOptionPane.showMessageDialog(this,
                            ids.size() + " pregunta(s) y sus respuestas se guardaron correctamente.",
                            "Cuestionario actualizado",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(this,
                            "No se pudo guardar la pregunta: " + ex.getMessage(),
                            "Error al guardar",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnEditExam.addActionListener(e -> editExamDetails());
        btnEditQuestionnaire.addActionListener(e -> editQuestionnaire());
        btnPreviewQuestionnaire.addActionListener(e -> previewQuestionnaire());
        btnDeleteQuestionnaire.addActionListener(e -> deleteQuestionnaire());

        return panel;
    }

    private void editExamDetails() {
        requireTeacher();
        ComboItem examItem = selectedItem(questionExamSelector);
        if (examItem == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un examen en la tabla o en el filtro.");
            return;
        }
        Exam exam = examService.findExam(examItem.id);
        if (exam == null) {
            JOptionPane.showMessageDialog(this, "El examen no fue encontrado.");
            return;
        }

        app.ui.FormModal modal = new app.ui.FormModal((Frame) SwingUtilities.getWindowAncestor(this), "Editar Parametros del Examen", 460, 480);
        JTextField examTitle = new JTextField(exam.getTitle(), 16);
        JTextField examDescription = new JTextField(exam.getDescription() != null ? exam.getDescription() : "", 18);
        JCheckBox examActive = new JCheckBox("Activo", exam.isActive());
        DateTimePicker examDueAt = createDateTimePicker();
        if (exam.getDueAt() != null) {
            examDueAt.setDateTime(exam.getDueAt());
        }
        JTextField examAttempts = new JTextField(String.valueOf(exam.getMaxAttempts()), 5);
        JTextField examDuration = new JTextField(String.valueOf(exam.getDurationMinutes()), 5);

        JPanel c = modal.getContent();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; c.add(new JLabel("Titulo:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; c.add(examTitle, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; c.add(new JLabel("Descripcion:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; c.add(examDescription, gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; c.add(new JLabel("Estado:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; c.add(examActive, gbc);
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; c.add(new JLabel("Fecha limite:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; c.add(examDueAt, gbc);
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0; c.add(new JLabel("Intentos max:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; c.add(examAttempts, gbc);
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0; c.add(new JLabel("Duracion (min):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; c.add(examDuration, gbc);

        JButton confirm = new JButton("Guardar Cambios");
        app.ui.UiTheme.styleButton(confirm);
        JButton cancel = new JButton("Cancelar");
        app.ui.UiTheme.styleSecondaryButton(cancel);
        modal.addActions(confirm, cancel);
        modal.setVisible(true);

        if (modal.isConfirmed()) {
            try {
                int attempts = Integer.parseInt(examAttempts.getText().trim());
                int duration = Integer.parseInt(examDuration.getText().trim());
                examService.updateDetails(exam.getId(), examTitle.getText().trim(), examDescription.getText().trim(),
                        examActive.isSelected(), spinnerToDateTime(examDueAt), attempts, duration);
                auditService.log(currentTeacherId(), "EXAM_UPDATE", "Examen ID " + exam.getId());
                refreshTeacherData();
                selectComboItem(questionExamSelector, exam.getId());
                JOptionPane.showMessageDialog(this, "Parametros del examen actualizados.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error actualizando examen: " + ex.getMessage());
            }
        }
    }

    private void editQuestionnaire() {
        requireTeacher();
        ComboItem examItem = selectedItem(questionExamSelector);
        if (examItem == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un cuestionario en la tabla o en el filtro.");
            return;
        }
        if (!canModifyQuestionnaire(examItem.id, "editar su contenido")) {
            return;
        }

        List<QuestionDraft> drafts = buildQuestionDrafts(examItem.id);
        QuestionBatchEditorDialog editor = new QuestionBatchEditorDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), examItem.label, drafts
        );
        editor.setVisible(true);
        List<QuestionDraft> edited = editor.getQuestions();
        if (edited == null || edited.isEmpty()) {
            return;
        }

        try {
            List<Long> ids = examService.synchronizeQuestions(examItem.id, edited);
            auditService.log(currentTeacherId(), "QUESTIONNAIRE_EDIT",
                    "Cuestionario " + examItem.id + " actualizado con " + ids.size() + " preguntas");
            refreshTeacherData();
            selectComboItem(questionExamSelector, examItem.id);
            JOptionPane.showMessageDialog(
                    this,
                    "Se actualizaron " + ids.size() + " pregunta(s), respuestas, imagenes y orden.",
                    "Cuestionario actualizado",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this, "No se pudo actualizar el cuestionario: " + ex.getMessage(),
                    "Error al editar", JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void previewQuestionnaire() {
        requireTeacher();
        ComboItem examItem = selectedItem(questionExamSelector);
        if (examItem == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un cuestionario para abrir la vista previa.");
            return;
        }
        Exam exam = examService.findExam(examItem.id);
        if (exam == null) {
            JOptionPane.showMessageDialog(this, "El cuestionario seleccionado ya no existe.");
            return;
        }
        List<Question> questions = examService.listQuestionsByExam(examItem.id);
        QuestionnairePreviewDialog preview = new QuestionnairePreviewDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), exam, questions, answerService
        );
        preview.setVisible(true);
    }

    private void deleteQuestionnaire() {
        requireTeacher();
        ComboItem examItem = selectedItem(questionExamSelector);
        if (examItem == null) {
            JOptionPane.showMessageDialog(this, "Selecciona el cuestionario que deseas eliminar.");
            return;
        }
        if (!canModifyQuestionnaire(examItem.id, "eliminarlo")) {
            return;
        }

        int questionCount = examService.listQuestionsByExam(examItem.id).size();
        JPanel warning = UiTheme.card(new GridLayout(0, 1, 5, 5));
        warning.add(UiTheme.title("Eliminar cuestionario", 19f));
        warning.add(new JLabel("Se eliminara: " + examItem.label));
        warning.add(new JLabel(questionCount + " pregunta(s) y todas sus respuestas."));
        JLabel irreversible = new JLabel("Esta accion no se puede deshacer.");
        irreversible.setForeground(UiTheme.PRIMARY);
        irreversible.setFont(irreversible.getFont().deriveFont(Font.BOLD));
        warning.add(irreversible);
        UiTheme.styleTree(warning);

        Object[] options = {"Eliminar definitivamente", "Cancelar"};
        int confirmation = JOptionPane.showOptionDialog(
                this, warning, "Confirmar eliminacion", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]
        );
        if (confirmation != 0) {
            return;
        }

        try {
            examService.deleteExam(examItem.id);
            auditService.log(currentTeacherId(), "QUESTIONNAIRE_DELETE",
                    "Cuestionario eliminado ID " + examItem.id + ": " + examItem.label);
            selectedExamId.setText("");
            refreshTeacherData();
            JOptionPane.showMessageDialog(this, "Cuestionario eliminado correctamente.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this, "No se pudo eliminar el cuestionario: " + ex.getMessage(),
                    "Error al eliminar", JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private List<QuestionDraft> buildQuestionDrafts(long examId) {
        List<QuestionDraft> drafts = new ArrayList<>();
        for (Question question : examService.listQuestionsByExam(examId)) {
            QuestionDraft draft = new QuestionDraft();
            draft.setId(question.getId());
            draft.setStatement(question.getStatement());
            draft.setType(question.getType());
            draft.setScore(question.getScore());
            draft.setImageData(question.getImageData());
            draft.setImageName(question.getImageName());
            draft.setImageContentType(question.getImageContentType());
            draft.setAnswers(answerService.listByQuestion(question.getId()));
            drafts.add(draft);
        }
        return drafts;
    }

    private boolean canModifyQuestionnaire(long examId, String action) {
        List<ExamAttempt> attempts = examAttemptService.listByExam(examId);
        if (attempts.isEmpty()) {
            return true;
        }
        JOptionPane.showMessageDialog(
                this,
                "No es seguro " + action + " porque el cuestionario tiene "
                        + attempts.size() + " intento(s) registrado(s).\n\n"
                        + "Puedes abrir la vista previa o deshabilitarlo para conservar las notas.",
                "Cuestionario con entregas",
                JOptionPane.WARNING_MESSAGE
        );
        return false;
    }

    private JLabel workflowBadge(String number, String text) {
        JLabel badge = new JLabel(number + "  " + text);
        badge.setOpaque(true);
        badge.setBackground(UiTheme.CONTROL);
        badge.setForeground(UiTheme.PRIMARY_DARK);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
        badge.setBorder(BorderFactory.createCompoundBorder(
                UiTheme.roundedBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)
        ));
        return badge;
    }

    private JPanel buildTableWithSearch(String title, JTable table, DefaultTableModel model, JPanel extraHeader) {
        JPanel wrapper = UiTheme.card(new BorderLayout(8, 10));
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftHeader.setOpaque(false);
        JLabel code = new JLabel(sectionCode(title));
        code.setOpaque(true);
        code.setBackground(UiTheme.PRIMARY_SOFT);
        code.setForeground(UiTheme.PRIMARY_DARK);
        code.setFont(code.getFont().deriveFont(Font.BOLD, 10f));
        code.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        JLabel lblTitle = UiTheme.title(title, 16f);
        leftHeader.add(lblTitle);
        leftHeader.add(code);
        JLabel rowCount = new JLabel(model.getRowCount() + " registros");
        rowCount.setForeground(UiTheme.MUTED);
        rowCount.setFont(rowCount.getFont().deriveFont(11f));
        leftHeader.add(rowCount);
        model.addTableModelListener(e -> rowCount.setText(model.getRowCount() + " registros"));
        titleRow.add(leftHeader, BorderLayout.WEST);
        header.add(titleRow);
        header.add(Box.createVerticalStrut(7));
        
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Buscar dentro de " + title.toLowerCase(Locale.ROOT));
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter = new javax.swing.table.TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(javax.swing.RowFilter.regexFilter(
                            "(?i)" + java.util.regex.Pattern.quote(text)
                    ));
                }
            }
        });
        
        JPanel rightHeader = new JPanel(new BorderLayout(8, 0));
        rightHeader.setOpaque(false);
        JLabel searchLabel = new JLabel("Buscar");
        searchLabel.setForeground(UiTheme.MUTED);
        rightHeader.add(searchLabel, BorderLayout.WEST);
        rightHeader.add(searchField, BorderLayout.CENTER);
        rightHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JPanel controlsRow = new JPanel();
        controlsRow.setOpaque(false);
        controlsRow.setLayout(new BoxLayout(controlsRow, BoxLayout.Y_AXIS));
        if (extraHeader != null) {
            extraHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
            extraHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            controlsRow.add(extraHeader);
            controlsRow.add(Box.createVerticalStrut(6));
        }
        rightHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlsRow.add(rightHeader);
        header.add(controlsRow);
        
        JScrollPane scroll = new JScrollPane(table);
        scroll.setWheelScrollingEnabled(true);
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private String sectionCode(String title) {
        String value = title.toLowerCase(Locale.ROOT);
        if (value.contains("cuestion") || value.contains("examen")) {
            return "01";
        }
        if (value.contains("pregunta")) {
            return "02";
        }
        if (value.contains("respuesta")) {
            return "03";
        }
        return "DATOS";
    }

private JPanel buildSimulationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        
        top.add(new JLabel("Curso:"));
        top.add(simulationCourseSelector);
        
        JButton refresh = new JButton("Listar");
        app.ui.UiTheme.styleSecondaryButton(refresh);
        top.add(refresh);
        top.add(simulationSummary);

        JButton create = new JButton("+ Crear practica");
        app.ui.UiTheme.styleButton(create);
        top.add(create);

        JButton addCavities = new JButton("+ Practicas de cavidades 3D");
        app.ui.UiTheme.styleSecondaryButton(addCavities);
        addCavities.setToolTipText("Crea en el curso una practica por cada modelo 3D de las cavidades que todavia no tenga");
        top.add(addCavities);

        JTable table = new JTable(simulationModel);
        JPanel pnlTable = buildTableWithSearch("Simulaciones", table, simulationModel, null);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectedSimulationId.setEditable(false);
        JButton preview3d = new JButton("▶ Probar Modelo 3D");
        app.ui.UiTheme.styleButton(preview3d);
        JButton editSim = new JButton("Editar practica");
        app.ui.UiTheme.styleSecondaryButton(editSim);
        JButton deleteSim = new JButton("Eliminar");
        app.ui.UiTheme.styleSecondaryButton(deleteSim);
        JButton enableJson = new JButton("Habilitar practica");
        JButton disableJson = new JButton("Deshabilitar practica");
        bottom.add(new JLabel("Simulacion Seleccionada:"));
        bottom.add(selectedSimulationId);
        bottom.add(preview3d);
        bottom.add(editSim);
        bottom.add(deleteSim);
        bottom.add(enableJson);
        bottom.add(disableJson);

        panel.add(top, BorderLayout.NORTH);
        panel.add(pnlTable, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> refreshTeacherData());
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    selectedSimulationId.setText(String.valueOf(tableModelValue(table, row, 0)));
                }
            }
        });

        addCavities.addActionListener(e -> {
            requireTeacher();
            ComboItem courseItem = selectedItem(simulationCourseSelector);
            if (courseItem == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona un curso primero.");
                return;
            }
            int created = simulationService.addCavityPractices(courseItem.id);
            auditService.log(currentTeacherId(), "SIM_CREATE",
                    created + " practica(s) 3D de cavidades en el curso ID " + courseItem.id);
            refreshSimulations();
            javax.swing.JOptionPane.showMessageDialog(this, created == 0
                    ? "El curso ya tiene una practica para cada modelo 3D de las cavidades."
                    : "Se crearon " + created + " practica(s) con los modelos 3D de las cavidades.");
        });

        create.addActionListener(e -> {
            requireTeacher();
            ComboItem courseItem = selectedItem(simulationCourseSelector);
            if (courseItem == null) {
                JOptionPane.showMessageDialog(this, "Selecciona un curso primero.");
                return;
            }
            SimulationFormDialog.Result result = SimulationFormDialog.show(this, null);
            if (result == null) return;
            try {
                long id = simulationService.createSimulation(courseItem.id,
                        result.name(), result.description(), result.configJson());
                auditService.log(currentTeacherId(), "SIM_CREATE", "Simulacion ID " + id);
                refreshSimulations();
                JOptionPane.showMessageDialog(this, "Practica creada con exito.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo crear la practica: " + ex.getMessage(),
                        "Error al guardar", JOptionPane.ERROR_MESSAGE);
            }
        });

        editSim.addActionListener(e -> {
            requireTeacher();
            if (selectedSimulationId.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Selecciona una practica para editar.");
                return;
            }
            long simId = Long.parseLong(selectedSimulationId.getText().trim());
            Simulation sim = simulationService.findById(simId);
            if (sim == null) {
                JOptionPane.showMessageDialog(this, "No se encontro la practica.");
                return;
            }
            SimulationFormDialog.Result result = SimulationFormDialog.show(this, sim);
            if (result == null) return;
            try {
                simulationService.updateDetails(simId,
                        result.name(), result.description(), result.configJson());
                auditService.log(currentTeacherId(), "SIM_UPDATE_DETAILS", "Simulacion ID " + simId);
                refreshSimulations();
                JOptionPane.showMessageDialog(this, "Practica actualizada con exito.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo actualizar la practica: " + ex.getMessage(),
                        "Error al guardar", JOptionPane.ERROR_MESSAGE);
            }
        });

        deleteSim.addActionListener(e -> {
            requireTeacher();
            if (selectedSimulationId.getText().isBlank()) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona una simulacion para eliminar.");
                return;
            }
            long simId = Long.parseLong(selectedSimulationId.getText().trim());
            int opt = javax.swing.JOptionPane.showConfirmDialog(
                    this,
                    "¿Estas seguro de eliminar la simulacion ID " + simId + "?",
                    "Confirmar eliminacion",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE
            );
            if (opt == javax.swing.JOptionPane.YES_OPTION) {
                simulationService.deleteSimulation(simId);
                auditService.log(currentTeacherId(), "SIM_DELETE", "Simulacion ID " + simId);
                selectedSimulationId.setText("");
                refreshSimulations();
                javax.swing.JOptionPane.showMessageDialog(this, "Simulacion eliminada correctamente.");
            }
        });

        preview3d.addActionListener(e -> {
            requireTeacher();
            String scriptToRun = null;
            if (!selectedSimulationId.getText().isBlank()) {
                try {
                    long simId = Long.parseLong(selectedSimulationId.getText().trim());
                    Simulation sim = simulationService.findById(simId);
                    if (sim != null) {
                        scriptToRun = SimulationService.getScript3D(sim);
                    }
                } catch (Exception ignored) {}
            }
            if (scriptToRun == null || scriptToRun.isBlank()) {
                String[] availableModels = Visor3DService.MODELOS.stream()
                        .map(Visor3DService.Modelo::titulo).toArray(String[]::new);
                Object choice = javax.swing.JOptionPane.showInputDialog(
                        this,
                        "Selecciona el modelo 3D que deseas explorar y probar:",
                        "Probar Visor Anatomico 3D",
                        javax.swing.JOptionPane.QUESTION_MESSAGE,
                        null,
                        availableModels,
                        availableModels[0]
                );
                if (choice == null) {
                    return;
                }
                scriptToRun = Visor3DService.MODELOS.stream()
                        .filter(model -> model.titulo().equals(choice))
                        .map(Visor3DService.Modelo::script)
                        .findFirst().orElse(null);
                if (scriptToRun == null) {
                    return;
                }
            }
            runTeacherPythonScript(scriptToRun);
        });

        enableJson.addActionListener(e -> toggleSimulationStatus(true));
        disableJson.addActionListener(e -> toggleSimulationStatus(false));

        return panel;
    }

    private void toggleSimulationStatus(boolean enabled) {
        requireTeacher();
        if (selectedSimulationId.getText().isBlank()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Selecciona una simulacion.");
            return;
        }
        long id = Long.parseLong(selectedSimulationId.getText().trim());
        simulationService.updateEnabled(id, enabled);
        auditService.log(currentTeacherId(), "SIM_UPDATE", "Simulacion ID " + id + " -> " + (enabled ? "Habilitado" : "Deshabilitado"));
        refreshSimulations();
        javax.swing.JOptionPane.showMessageDialog(this, "Estado actualizado.");
    }

    private JPanel buildGradesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        top.add(new JLabel("Curso:"));
        top.add(gradesCourseFilter);
        top.add(new JLabel("Examen:"));
        top.add(gradesExamFilter);

        JButton refresh = new JButton("Listar Notas");
        app.ui.UiTheme.styleSecondaryButton(refresh);
        
        JButton exportCsv = new JButton("Exportar a CSV");
        app.ui.UiTheme.styleButton(exportCsv);
        
        JButton exportHtml = new JButton("Exportar Reporte (HTML/PDF)");
        app.ui.UiTheme.styleSecondaryButton(exportHtml);

        top.add(refresh);
        top.add(exportCsv);
        top.add(exportHtml);
        top.add(gradesSummary);

        JTable table = new JTable(gradeModel);
        JPanel pnlTable = buildTableWithSearch("Notas de Estudiantes", table, gradeModel, null);

        panel.add(top, BorderLayout.NORTH);
        panel.add(pnlTable, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JTextField attemptIdField = new JTextField(8);
        attemptIdField.setEditable(false);
        JButton btnReviewAttempt = new JButton("Ver Revision de Respuestas");
        app.ui.UiTheme.styleButton(btnReviewAttempt);
        JButton manualDetail = new JButton("Modificar Nota Manual");
        app.ui.UiTheme.styleSecondaryButton(manualDetail);
        bottom.add(new JLabel("Intento Seleccionado:"));
        bottom.add(attemptIdField);
        bottom.add(btnReviewAttempt);
        bottom.add(manualDetail);
        panel.add(bottom, BorderLayout.SOUTH);

        gradesCourseFilter.addActionListener(e -> {
            ComboItem courseItem = selectedItem(gradesCourseFilter);
            if (courseItem != null) {
                fillCombo(gradesExamFilter, examService.listExamsByCourse(courseItem.id));
            }
        });
        
        refresh.addActionListener(e -> refreshGrades());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    attemptIdField.setText(String.valueOf(tableModelValue(table, row, 0)));
                }
            }
        });

        btnReviewAttempt.addActionListener(e -> {
            requireTeacher();
            if (attemptIdField.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Selecciona un intento en la tabla primero.");
                return;
            }
            long attemptId = Long.parseLong(attemptIdField.getText().trim());
            app.model.ExamAttempt attempt = examAttemptService.findById(attemptId);
            if (attempt == null) {
                JOptionPane.showMessageDialog(this, "No se encontro el intento seleccionado.");
                return;
            }
            app.model.Exam exam = examService.findExam(attempt.getExamId());
            app.model.Result result = resultService != null ? resultService.findByAttemptId(attemptId) : null;
            if (result == null) {
                JOptionPane.showMessageDialog(this, "Este intento todavia no tiene un detalle de respuestas registrado.");
                return;
            }
            app.ui.ExamAttemptReviewDialog dialog = new app.ui.ExamAttemptReviewDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this),
                    attempt, exam, result, examService, answerService
            );
            dialog.setVisible(true);
        });

        manualDetail.addActionListener(e -> {
            requireTeacher();
            if (attemptIdField.getText().isBlank()) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona un intento primero.");
                return;
            }

            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Modificar Nota", 350, 200);
            JTextField scoreInput = new JTextField("0.0", 5);
            
            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new java.awt.Insets(10,10,10,10);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; c.add(new JLabel("Nueva Calificacion:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(scoreInput, gbc);

            JButton confirm = new JButton("Guardar Nota");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                try {
                    double score = Double.parseDouble(scoreInput.getText().trim());
                    long attemptId = Long.parseLong(attemptIdField.getText().trim());
                    examAttemptService.updateScore(attemptId, score);
                    auditService.log(currentTeacherId(), "GRADE_UPDATE_MANUAL", "Intento ID " + attemptId + " -> " + score);
                    refreshGrades();
                    javax.swing.JOptionPane.showMessageDialog(this, "Calificacion guardada.");
                } catch (NumberFormatException ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Calificacion invalida.");
                }
            }
        });

        exportCsv.addActionListener(e -> {
            javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
            fileChooser.setDialogTitle("Guardar como CSV");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos CSV", "csv"));
            if (fileChooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".csv")) {
                    file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
                }
                try (java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8);
                     java.io.PrintWriter writer = new java.io.PrintWriter(osw)) {
                    writer.write('\ufeff'); // UTF-8 BOM para compatibilidad con Excel
                    for (int i = 0; i < gradeModel.getColumnCount(); i++) {
                        writer.print(escapeCsvCell(gradeModel.getColumnName(i)));
                        if (i < gradeModel.getColumnCount() - 1) writer.print(",");
                    }
                    writer.println();
                    for (int i = 0; i < gradeModel.getRowCount(); i++) {
                        for (int j = 0; j < gradeModel.getColumnCount(); j++) {
                            Object val = gradeModel.getValueAt(i, j);
                            writer.print(escapeCsvCell(val != null ? val.toString() : ""));
                            if (j < gradeModel.getColumnCount() - 1) writer.print(",");
                        }
                        writer.println();
                    }
                    javax.swing.JOptionPane.showMessageDialog(this, "Archivo CSV exportado exitosamente con codificacion UTF-8.");
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage());
                }
            }
        });

        exportHtml.addActionListener(e -> {
            ComboItem examItem = selectedItem(gradesExamFilter);
            if (examItem == null) {
                JOptionPane.showMessageDialog(this, "Selecciona un examen para exportar.");
                return;
            }
            if (gradeModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "No hay notas para exportar.");
                return;
            }

            javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
            fileChooser.setDialogTitle("Guardar Reporte HTML (Imprimible a PDF)");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos HTML", "html"));
            if (fileChooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".html")) {
                    file = new java.io.File(file.getParentFile(), file.getName() + ".html");
                }
                try (java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8);
                     java.io.PrintWriter writer = new java.io.PrintWriter(osw)) {
                    
                    writer.println("<!DOCTYPE html>");
                    writer.println("<html lang='es'><head><meta charset='UTF-8'>");
                    writer.println("<title>Reporte de Calificaciones</title>");
                    writer.println("<style>");
                    writer.println("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #333; margin: 40px; }");
                    writer.println("h1 { color: #2c3e50; text-align: center; border-bottom: 2px solid #3498db; padding-bottom: 10px; }");
                    writer.println("h2 { color: #34495e; }");
                    writer.println(".summary { background: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px; border-left: 4px solid #3498db; }");
                    writer.println("table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 14px; }");
                    writer.println("th { background-color: #3498db; color: white; padding: 12px; text-align: left; }");
                    writer.println("td { padding: 10px; border-bottom: 1px solid #ddd; }");
                    writer.println("tr:nth-child(even) { background-color: #f2f2f2; }");
                    writer.println("tr:hover { background-color: #e8f4f8; }");
                    writer.println(".footer { margin-top: 40px; text-align: center; font-size: 12px; color: #7f8c8d; border-top: 1px solid #eee; padding-top: 20px; }");
                    writer.println("@media print { .no-print { display: none; } body { margin: 0; } }");
                    writer.println("</style></head><body>");
                    
                    writer.println("<div class='no-print' style='text-align: right; margin-bottom: 20px;'>");
                    writer.println("<button onclick='window.print()' style='padding: 8px 15px; background: #3498db; color: white; border: none; border-radius: 4px; cursor: pointer;'>Imprimir a PDF</button>");
                    writer.println("</div>");
                    
                    writer.println("<h1>Reporte Oficial de Calificaciones</h1>");
                    writer.println("<h2>Examen: " + examItem.label + "</h2>");
                    writer.println("<div class='summary'>");
                    writer.println("<p><strong>Resumen:</strong> " + gradesSummary.getText() + "</p>");
                    writer.println("<p><strong>Generado por:</strong> Docente ID " + currentTeacherId() + "</p>");
                    writer.println("<p><strong>Fecha de emision:</strong> " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>");
                    writer.println("</div>");

                    writer.println("<table><thead><tr>");
                    for (int i = 0; i < gradeModel.getColumnCount(); i++) {
                        writer.println("<th>" + escapeHtml(gradeModel.getColumnName(i)) + "</th>");
                    }
                    writer.println("</tr></thead><tbody>");

                    for (int i = 0; i < gradeModel.getRowCount(); i++) {
                        writer.println("<tr>");
                        for (int j = 0; j < gradeModel.getColumnCount(); j++) {
                            Object val = gradeModel.getValueAt(i, j);
                            writer.println("<td>" + escapeHtml(val != null ? val.toString() : "") + "</td>");
                        }
                        writer.println("</tr>");
                    }
                    writer.println("</tbody></table>");
                    
                    writer.println("<div class='footer'>");
                    writer.println("<p>Sistema de Gestion Academica y Simulador Anatomico Equino 3D - Universidad de Pamplona</p>");
                    writer.println("</div>");

                    writer.println("</body></html>");

                    JOptionPane.showMessageDialog(this, "Reporte HTML exportado exitosamente.\nPuedes abrirlo en tu navegador e imprimirlo como PDF.");
                    
                    // Intentar abrir el archivo en el navegador
                    try {
                        java.awt.Desktop.getDesktop().browse(file.toURI());
                    } catch (Exception ignored) {}
                    
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error al generar reporte: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        return panel;
    }

    private void fillCombo(JComboBox<ComboItem> combo, java.util.List<app.model.Exam> exams) {
        combo.removeAllItems();
        for (app.model.Exam exam : exams) {
            combo.addItem(new ComboItem(exam.getId(), exam.getTitle()));
        }
    }

    private void refreshGrades() {
        gradeModel.setRowCount(0);
        ComboItem examItem = selectedItem(gradesExamFilter);
        if (examItem == null) {
            gradesSummary.setText("Selecciona un examen.");
            return;
        }

        java.util.List<app.model.ExamAttempt> attempts = examAttemptService.listByExam(examItem.id);
        int graded = 0;
        double total = 0;
        Double max = null;
        Double min = null;

        for (app.model.ExamAttempt attempt : attempts) {
            gradeModel.addRow(new Object[]{
                    attempt.getId(),
                    attempt.getExamId(),
                    attempt.getStudentId(),
                    attempt.getStartAt(),
                    attempt.getEndAt(),
                    attempt.getTotalScore()
            });

            if (attempt.getTotalScore() != null) {
                graded++;
                total += attempt.getTotalScore();
                max = max == null ? attempt.getTotalScore() : Math.max(max, attempt.getTotalScore());
                min = min == null ? attempt.getTotalScore() : Math.min(min, attempt.getTotalScore());
            }
        }

        gradesSummary.setText(
                "Intentos: " + attempts.size() +
                        " | Calificados: " + graded +
                        " | Promedio: " + formatAverage(total, graded) +
                        " | Max: " + formatNumber(max) +
                        " | Min: " + formatNumber(min)
        );
    }

    private JPanel buildStatsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea area = new JTextArea();
        area.setEditable(false);
        JButton refresh = new JButton("Actualizar");
        refresh.addActionListener(e -> {
            requireTeacher();
            long teacherId = currentTeacherId();
            List<Course> courses = courseService.listByTeacher(teacherId);
            int courseCount = courses.size();
            int examCount = 0;
            int questionCount = 0;
            int answerCount = 0;
            int attemptsCount = 0;
            double totalScores = 0;
            int gradedAttempts = 0;

            for (Course c : courses) {
                List<Exam> exams = examService.listExamsByCourse(c.getId());
                examCount += exams.size();
                for (Exam ex : exams) {
                    List<Question> questions = examService.listQuestionsByExam(ex.getId());
                    questionCount += questions.size();
                    attemptsCount += examAttemptService.listByExam(ex.getId()).size();
                    for (ExamAttempt attempt : examAttemptService.listByExam(ex.getId())) {
                        if (attempt.getTotalScore() != null) {
                            totalScores += attempt.getTotalScore();
                            gradedAttempts++;
                        }
                    }
                    for (Question q : questions) {
                        answerCount += answerService.listByQuestion(q.getId()).size();
                    }
                }
            }

            area.setText("");
            area.append("Cursos creados: " + courseCount + "\n");
            area.append("Examenes creados: " + examCount + "\n");
            area.append("Preguntas creadas: " + questionCount + "\n");
            area.append("Respuestas registradas: " + answerCount + "\n");
            area.append("Intentos recibidos: " + attemptsCount + "\n");
            area.append("Promedio de notas: " + formatAverage(totalScores, gradedAttempts) + "\n");
        });
        
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(800, 800));
        panel.add(refresh, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void refreshTeacherData() {
        requireTeacher();
        populateCourseSelectors();
        refreshQuestionnaireTables();
        refreshSimulations();
        populateGradesExamSelector(null);
        loadGrades();
    }

    private void populateCourseSelectors() {
        List<Course> courses = teacherCourses();
        Long previousQuestionnaireCourse = selectedId(questionnaireCourseFilter);
        Long previousExamCourse = selectedId(examCourseSelector);
        Long previousGradesCourse = selectedId(gradesCourseFilter);
        Long previousSimulationCourse = selectedId(simulationCourseSelector);

        fillCourseCombo(questionnaireCourseFilter, courses, true);
        fillCourseCombo(examCourseSelector, courses, false);
        fillCourseCombo(gradesCourseFilter, courses, true);
        fillCourseCombo(simulationCourseSelector, courses, false);

        selectComboItem(questionnaireCourseFilter, previousQuestionnaireCourse);
        selectComboItem(examCourseSelector, previousExamCourse);
        selectComboItem(gradesCourseFilter, previousGradesCourse);
        selectComboItem(simulationCourseSelector, previousSimulationCourse);
    }

    private void refreshQuestionnaireTables() {
        examModel.setRowCount(0);
        questionModel.setRowCount(0);
        answerModel.setRowCount(0);

        Long selectedCourseId = selectedId(questionnaireCourseFilter);
        List<Exam> exams = new ArrayList<>();

        for (Course course : teacherCourses()) {
            if (selectedCourseId != null && course.getId() != selectedCourseId) {
                continue;
            }

            for (Exam exam : examService.listExamsByCourse(course.getId())) {
                exams.add(exam);
                examModel.addRow(new Object[]{
                        exam.getId(),
                        course.getName(),
                        exam.getTitle(),
                        exam.isActive(),
                        exam.getDueAt(),
                        exam.getMaxAttempts(),
                        exam.getDurationMinutes(),
                        describeExamStatus(exam),
                        exam.getCreatedAt()
                });
            }
        }

        Long previousExamId = selectedId(questionExamSelector);
        fillExamCombo(questionExamSelector, exams, true);
        selectComboItem(questionExamSelector, previousExamId);
        loadQuestionsForSelectedExam();
        questionnaireSummary.setText("Examenes: " + exams.size() + " | Preguntas: " + questionModel.getRowCount() + " | Respuestas: " + answerModel.getRowCount());
    }

    private void loadQuestionsForSelectedExam() {
        questionModel.setRowCount(0);
        answerModel.setRowCount(0);

        ComboItem examItem = selectedItem(questionExamSelector);
        List<Question> questions = new ArrayList<>();
        if (examItem != null) {
            questions = examService.listQuestionsByExam(examItem.id);
            for (Question question : questions) {
                questionModel.addRow(new Object[]{
                        question.getId(),
                        examItem.label,
                        question.getType(),
                        question.getScore(),
                        question.getImageData() == null ? "Sin imagen" : "Disponible",
                        question.getStatement()
                });
            }
        }

        Long previousQuestionId = selectedId(answerQuestionSelector);
        fillQuestionCombo(answerQuestionSelector, questions, true);
        selectComboItem(answerQuestionSelector, previousQuestionId);
        loadAnswersForSelectedQuestion();

        int answerCount = answerModel.getRowCount();
        questionnaireSummary.setText("Examenes: " + examModel.getRowCount() + " | Preguntas: " + questions.size() + " | Respuestas: " + answerCount);
    }

    private void loadAnswersForSelectedQuestion() {
        answerModel.setRowCount(0);
        ComboItem questionItem = selectedItem(answerQuestionSelector);
        if (questionItem == null) {
            questionnaireSummary.setText("Examenes: " + examModel.getRowCount() + " | Preguntas: " + questionModel.getRowCount() + " | Respuestas: 0");
            return;
        }

        List<Answer> answers = answerService.listByQuestion(questionItem.id);
        for (Answer answer : answers) {
            answerModel.addRow(new Object[]{
                    answer.getId(),
                    questionItem.label,
                    answer.isCorrect(),
                    answer.getContent()
            });
        }
        questionnaireSummary.setText("Examenes: " + examModel.getRowCount() + " | Preguntas: " + questionModel.getRowCount() + " | Respuestas: " + answers.size());
    }

    private void populateGradesExamSelector(Long preferredExamId) {
        List<Exam> exams = new ArrayList<>();
        Long selectedCourseId = selectedId(gradesCourseFilter);

        for (Course course : teacherCourses()) {
            if (selectedCourseId != null && course.getId() != selectedCourseId) {
                continue;
            }
            exams.addAll(examService.listExamsByCourse(course.getId()));
        }

        fillExamCombo(gradesExamFilter, exams, true);
        if (preferredExamId != null) {
            selectComboItem(gradesExamFilter, preferredExamId);
        }
    }

    private void loadGrades() {
        requireTeacher();
        gradeModel.setRowCount(0);
        selectedAttemptId.setText("");
        selectedScore.setText("");

        ComboItem examItem = selectedItem(gradesExamFilter);
        if (examItem == null) {
            gradesSummary.setText("Selecciona un examen para ver notas.");
            return;
        }

        List<ExamAttempt> attempts = examAttemptService.listByExam(examItem.id);
        int graded = 0;
        double total = 0;
        Double max = null;
        Double min = null;

        for (ExamAttempt attempt : attempts) {
            gradeModel.addRow(new Object[]{
                    attempt.getId(),
                    attempt.getExamId(),
                    attempt.getStudentId(),
                    attempt.getStartAt(),
                    attempt.getEndAt(),
                    attempt.getTotalScore()
            });

            if (attempt.getTotalScore() != null) {
                graded++;
                total += attempt.getTotalScore();
                max = max == null ? attempt.getTotalScore() : Math.max(max, attempt.getTotalScore());
                min = min == null ? attempt.getTotalScore() : Math.min(min, attempt.getTotalScore());
            }
        }

        gradesSummary.setText(
                "Intentos: " + attempts.size() +
                        " | Calificados: " + graded +
                        " | Promedio: " + formatAverage(total, graded) +
                        " | Max: " + formatNumber(max) +
                        " | Min: " + formatNumber(min)
        );
    }

    private void updateExamAvailability(Boolean active, LocalDateTime dueAt) {
        requireTeacher();
        if (selectedExamId.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Selecciona un examen en la tabla.");
            return;
        }

        long examId = Long.parseLong(selectedExamId.getText().trim());
        Exam exam = examService.findExam(examId);
        if (exam == null) {
            JOptionPane.showMessageDialog(this, "El examen no existe.");
            return;
        }

        try {
            boolean newActive = active != null ? active : exam.isActive();
            examService.updateAvailability(examId, newActive, dueAt);
            auditService.log(currentTeacherId(), "EXAM_AVAILABILITY_UPDATE", "Examen ID " + examId);
            refreshTeacherData();
            JOptionPane.showMessageDialog(this, "Disponibilidad del cuestionario actualizada.");
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Fecha invalida", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadExamAvailability(long examId) {
        Exam exam = examService.findExam(examId);
        if (exam == null) {
            resetDateTimePicker(availabilityDueAtPicker);
            return;
        }
        if (exam.getDueAt() != null) {
            availabilityDueAtPicker.setDateTime(exam.getDueAt());
        } else {
            resetDateTimePicker(availabilityDueAtPicker);
        }
    }

    private void refreshSimulations() {
        simulationModel.setRowCount(0);
        selectedSimulationId.setText("");

        ComboItem courseItem = selectedItem(simulationCourseSelector);
        if (courseItem == null) {
            if (simulationCourseSelector.getItemCount() == 0) {
                simulationSummary.setText("No hay cursos disponibles. Crea una materia en Administracion > Cursos.");
            } else {
                simulationSummary.setText("Selecciona un curso para ver simulaciones.");
            }
            return;
        }

        String courseName = courseItem.label;
        int enabledCount = 0;
        List<Simulation> simulations = simulationService.listByCourse(courseItem.id);
        for (Simulation simulation : simulations) {
            boolean enabled = SimulationService.isEnabled(simulation);
            if (enabled) {
                enabledCount++;
            }
            String script3d = SimulationService.getScript3D(simulation);
            simulationModel.addRow(new Object[]{
                    simulation.getId(),
                    courseName,
                    simulation.getName(),
                    script3d != null ? Visor3DService.titulo(script3d) : "Ninguno",
                    enabled ? "Si" : "No",
                    simulationDuration(simulation)
            });
        }
        simulationSummary.setText("Practicas: " + simulations.size() + " | Disponibles: " + enabledCount);
    }

    private String simulationDuration(Simulation simulation) {
        try {
            int minutes = app.service.SimulationConfig.fromJson(simulation.getConfigJson())
                    .integer("tiempoMinutos", 0);
            return minutes > 0 ? minutes + " min" : "Sin definir";
        } catch (IllegalArgumentException ex) {
            return "Sin definir";
        }
    }

    private void fillCourseCombo(JComboBox<ComboItem> combo, List<Course> courses, boolean includeAll) {
        combo.removeAllItems();
        if (includeAll) {
            combo.addItem(ComboItem.all("Todos"));
        }
        for (Course course : courses) {
            combo.addItem(new ComboItem(course.getId(), course.getName()));
        }
    }

    private void fillExamCombo(JComboBox<ComboItem> combo, List<Exam> exams, boolean includeEmpty) {
        combo.removeAllItems();
        if (includeEmpty) {
            combo.addItem(ComboItem.all("Todos"));
        }
        for (Exam exam : exams) {
            combo.addItem(new ComboItem(exam.getId(), exam.getTitle()));
        }
    }

    private void fillQuestionCombo(JComboBox<ComboItem> combo, List<Question> questions, boolean includeEmpty) {
        combo.removeAllItems();
        if (includeEmpty) {
            combo.addItem(ComboItem.all("Todas"));
        }
        for (Question question : questions) {
            combo.addItem(new ComboItem(question.getId(), "#" + question.getId() + " " + question.getStatement()));
        }
    }

    private List<Course> teacherCourses() {
        if (sessionManager.getCurrent() != null
                && sessionManager.getCurrent().getUser() != null
                && sessionManager.getCurrent().getUser().getRoles().contains(RoleType.ADMINISTRADOR)) {
            return courseService.listAll();
        }
        return courseService.listByTeacher(currentTeacherId());
    }

    private long currentTeacherId() {
        return sessionManager.getCurrent().getUser().getId();
    }

    private void requireTeacher() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR, RoleType.PROFESOR);
    }

    private ComboItem selectedItem(JComboBox<ComboItem> combo) {
        Object selected = combo.getSelectedItem();
        if (selected instanceof ComboItem item && !item.isAll) {
            return item;
        }
        return null;
    }

    private Long selectedId(JComboBox<ComboItem> combo) {
        Object selected = combo.getSelectedItem();
        if (selected instanceof ComboItem item && !item.isAll) {
            return item.id;
        }
        return null;
    }

    private Object tableModelValue(JTable table, int viewRow, int column) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        return table.getModel().getValueAt(modelRow, column);
    }

    private String escapeCsvCell(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private long tableLongValue(JTable table, int viewRow, int column) {
        Object value = tableModelValue(table, viewRow, column);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("El ID seleccionado no es valido: " + value, ex);
            }
        }
        throw new IllegalArgumentException("La fila seleccionada no contiene un ID.");
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
            if (!item.isAll && item.id == id) {
                combo.setSelectedIndex(i);
                return;
            }
        }

        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private String formatAverage(double total, int count) {
        if (count == 0) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", total / count);
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String describeExamStatus(Exam exam) {
        if (!exam.isActive()) {
            return "Deshabilitado";
        }
        if (exam.getDueAt() != null && exam.getDueAt().isBefore(LocalDateTime.now())) {
            return "Vencido";
        }
        return "Disponible";
    }

    private DateTimePicker createDateTimePicker() {
        return new DateTimePicker(LocalDateTime.now().plusDays(7));
    }

    private LocalDateTime spinnerToDateTime(DateTimePicker picker) {
        return picker.getDateTime();
    }

    private void resetDateTimePicker(DateTimePicker picker) {
        picker.setDateTime(LocalDateTime.now().plusDays(7));
    }

    private static final class DateTimePicker extends JPanel {
        private final JSpinner spinner;

        private DateTimePicker(LocalDateTime initial) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            putClientProperty("uiTheme.surface", Boolean.TRUE);
            
            SpinnerDateModel model = new SpinnerDateModel();
            spinner = new JSpinner(model);
            JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd HH:mm");
            spinner.setEditor(editor);
            
            add(spinner);
            setDateTime(initial);
        }

        private void setDateTime(LocalDateTime value) {
            if (value != null) {
                spinner.setValue(java.util.Date.from(value.atZone(java.time.ZoneId.systemDefault()).toInstant()));
            } else {
                spinner.setValue(new java.util.Date());
            }
        }

        private LocalDateTime getDateTime() {
            java.util.Date date = (java.util.Date) spinner.getValue();
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
    }

    private static final class ComboItem {
        private final long id;
        private final String label;
        private final boolean isAll;

        private ComboItem(long id, String label) {
            this(id, label, false);
        }

        private ComboItem(long id, String label, boolean isAll) {
            this.id = id;
            this.label = label;
            this.isAll = isAll;
        }

        private static ComboItem all(String label) {
            return new ComboItem(-1, label, true);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class ViewportPanel extends JPanel implements Scrollable {
        public ViewportPanel(LayoutManager layout) {
            super(layout);
        }
        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 50; }
        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            if (getParent() instanceof JViewport) {
                return (((JViewport)getParent()).getHeight() > getPreferredSize().height);
            }
            return false;
        }
    }

    private void runTeacherPythonScript(String scriptName) {
        Path scriptsFolder = Visor3DService.carpetaModelos();
        if (scriptsFolder == null) {
            javax.swing.JOptionPane.showMessageDialog(this, "No pudimos localizar la carpeta de modelos 3D.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        Path scriptPath = scriptsFolder.resolve(scriptName);
        if (!Files.isRegularFile(scriptPath)) {
            javax.swing.JOptionPane.showMessageDialog(this, "El archivo " + scriptName + " no existe.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        long teacherId = currentTeacherId();
        auditService.log(teacherId, "TEACHER_SIM_3D", "Vista previa 3D: " + scriptName);
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
                    javax.swing.JOptionPane.showMessageDialog(TeacherPanel.this,
                            "No se detecto un entorno Python con PyQt5, VTK y numpy.\n"
                                    + "Pide al administrador que instale las dependencias de los modelos 3D.",
                            "Python no listo", javax.swing.JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (result == null) {
                    return;
                }
                auditService.log(teacherId, "SIM_3D_SESION", Visor3DService.resumen(scriptPath, result));
                if (!result.correcto()) {
                    javax.swing.JOptionPane.showMessageDialog(TeacherPanel.this,
                            "El modelo no pudo abrirse correctamente.\nVerifica aceleracion grafica y librerias.",
                            "Aviso", javax.swing.JOptionPane.WARNING_MESSAGE);
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
}

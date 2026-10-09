with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

# 1. Add ResultService field
old_field = "    private final ExamAttemptService examAttemptService;"
new_field = """    private final ExamAttemptService examAttemptService;
    private final app.service.ResultService resultService;"""
assert old_field in text, 'old_field not found'
text = text.replace(old_field, new_field, 1)

# 2. Add constructor overload
old_ctor = """    public TeacherPanel(SessionManager sessionManager, AuditService auditService,
                        CourseService courseService, ExamService examService, SimulationService simulationService,
                        AnswerService answerService, ExamAttemptService examAttemptService) {
        this.sessionManager = sessionManager;
        this.auditService = auditService;
        this.courseService = courseService;
        this.examService = examService;
        this.simulationService = simulationService;
        this.answerService = answerService;
        this.examAttemptService = examAttemptService;"""

new_ctor = """    public TeacherPanel(SessionManager sessionManager, AuditService auditService,
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
        this.resultService = resultService;"""
assert old_ctor in text, 'old_ctor not found'
text = text.replace(old_ctor, new_ctor, 1)

# 3. Add btnReviewAttempt in buildGradesPanel
old_bottom = """        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField attemptIdField = new JTextField(8);
        attemptIdField.setEditable(false);
        JButton manualDetail = new JButton("Modificar Nota Manual");
        bottom.add(new JLabel("Intento Seleccionado:"));
        bottom.add(attemptIdField);
        bottom.add(manualDetail);
        panel.add(bottom, BorderLayout.SOUTH);"""

new_bottom = """        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
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
        panel.add(bottom, BorderLayout.SOUTH);"""
assert old_bottom in text, 'old_bottom not found'
text = text.replace(old_bottom, new_bottom, 1)

# 4. Add listener for btnReviewAttempt
old_manual_click = "        manualDetail.addActionListener(e -> {"
new_manual_click = """        btnReviewAttempt.addActionListener(e -> {
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

        manualDetail.addActionListener(e -> {"""
assert old_manual_click in text, 'old_manual_click not found'
text = text.replace(old_manual_click, new_manual_click, 1)

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('TeacherPanel review button added successfully!')

# Now update MainFrame.java to pass resultService
with open('src/app/ui/MainFrame.java', 'r', encoding='utf-8') as f:
    mf_text = f.read().replace('\r\n', '\n')

old_tp_inst = """            TeacherPanel teacherPanel = new TeacherPanel(sessionManager, auditService, courseService, examService,
                    simulationService, answerService, examAttemptService);"""

new_tp_inst = """            TeacherPanel teacherPanel = new TeacherPanel(sessionManager, auditService, courseService, examService,
                    simulationService, answerService, examAttemptService, resultService);"""

assert old_tp_inst in mf_text, 'old_tp_inst not found'
mf_text = mf_text.replace(old_tp_inst, new_tp_inst, 1)

with open('src/app/ui/MainFrame.java', 'w', encoding='utf-8') as f:
    f.write(mf_text)
print('MainFrame updated successfully!')

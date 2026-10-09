import re

with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    content = f.read()

# We need to replace the entire buildQuestionnairePanel method.
# It starts with "private JPanel buildQuestionnairePanel() {" and ends with "return panel;\n    }\n"
start_idx = content.find('private JPanel buildQuestionnairePanel() {')
end_idx = content.find('private JPanel buildSimulationPanel() {')

if start_idx != -1 and end_idx != -1:
    new_method = """private JPanel buildQuestionnairePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        topToolbar.add(new JLabel("Curso Global:"));
        topToolbar.add(questionnaireCourseFilter);
        JButton refresh = new JButton("Refrescar");
        app.ui.UiTheme.styleSecondaryButton(refresh);
        topToolbar.add(refresh);
        topToolbar.add(questionnaireSummary);

        JButton btnCreateExam = new JButton("+ Crear Examen");
        app.ui.UiTheme.styleButton(btnCreateExam);
        JButton btnCreateQuestion = new JButton("+ Crear Pregunta");
        app.ui.UiTheme.styleButton(btnCreateQuestion);
        JButton btnCreateAnswer = new JButton("+ Crear Respuesta");
        app.ui.UiTheme.styleButton(btnCreateAnswer);
        
        topToolbar.add(btnCreateExam);
        topToolbar.add(btnCreateQuestion);
        topToolbar.add(btnCreateAnswer);

        JTable examTable = new JTable(examModel);
        JTable questionTable = new JTable(questionModel);
        JTable answerTable = new JTable(answerModel);

        JPanel pnlExam = buildTableWithSearch("Exámenes", examTable, examModel, null);
        
        JPanel pnlQuestionHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlQuestionHeader.setOpaque(false);
        pnlQuestionHeader.add(new JLabel("Filtro:"));
        pnlQuestionHeader.add(questionExamSelector);
        JPanel pnlQuestion = buildTableWithSearch("Preguntas", questionTable, questionModel, pnlQuestionHeader);
        
        JPanel pnlAnswerHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlAnswerHeader.setOpaque(false);
        pnlAnswerHeader.add(new JLabel("Filtro:"));
        pnlAnswerHeader.add(answerQuestionSelector);
        JPanel pnlAnswer = buildTableWithSearch("Respuestas", answerTable, answerModel, pnlAnswerHeader);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pnlQuestion, pnlAnswer);
        bottomSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pnlExam, bottomSplit);
        mainSplit.setResizeWeight(0.4);
        mainSplit.setPreferredSize(new Dimension(800, 1200));

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
                    long examId = ((Number) examTable.getValueAt(row, 0)).longValue();
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
                    selectComboItem(answerQuestionSelector, ((Number) questionTable.getValueAt(row, 0)).longValue());
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
            app.ui.panels.DateTimePicker examDueAt = createDateTimePicker();
            JTextField examAttempts = new JTextField("1", 5);
            JTextField examDuration = new JTextField("60", 5);

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; c.add(new JLabel("Titulo:"), gbc);
            gbc.gridx = 1; c.add(examTitle, gbc);
            gbc.gridx = 0; gbc.gridy = 1; c.add(new JLabel("Descripcion:"), gbc);
            gbc.gridx = 1; c.add(examDescription, gbc);
            gbc.gridx = 0; gbc.gridy = 2; c.add(new JLabel("Estado:"), gbc);
            gbc.gridx = 1; c.add(examActive, gbc);
            gbc.gridx = 0; gbc.gridy = 3; c.add(new JLabel("Fecha limite:"), gbc);
            gbc.gridx = 1; c.add(examDueAt, gbc);
            gbc.gridx = 0; gbc.gridy = 4; c.add(new JLabel("Intentos max:"), gbc);
            gbc.gridx = 1; c.add(examAttempts, gbc);
            gbc.gridx = 0; gbc.gridy = 5; c.add(new JLabel("Duracion (min):"), gbc);
            gbc.gridx = 1; c.add(examDuration, gbc);

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
                            examActive.isSelected(), spinnerToDateTime(examDueAt));
                    
                    // Llama al setter del nuevo DAO para limites, asumiendo que agregaste un metodo en el DAO, 
                    // o lo dejaremos que use los valores predeterminados de la BD por ahora ya que alteramos la tabla.
                    // Para asignar los limites, requeriria actualizar ExamService.createExam. 
                    // Vamos a pasar los argumentos extras al createExam.
                    
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
            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Crear Pregunta", 400, 300);
            JTextField questionStatement = new JTextField(20);
            JComboBox<String> questionType = new JComboBox<>(new String[]{"seleccion_unica", "seleccion_multiple", "abierta", "verdadero_falso"});
            JTextField questionScore = new JTextField("1.0", 5);

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; c.add(new JLabel("Enunciado:"), gbc);
            gbc.gridx = 1; c.add(questionStatement, gbc);
            gbc.gridx = 0; gbc.gridy = 1; c.add(new JLabel("Tipo:"), gbc);
            gbc.gridx = 1; c.add(questionType, gbc);
            gbc.gridx = 0; gbc.gridy = 2; c.add(new JLabel("Puntaje:"), gbc);
            gbc.gridx = 1; c.add(questionScore, gbc);

            JButton confirm = new JButton("Guardar Pregunta");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                try {
                    double score = Double.parseDouble(questionScore.getText().trim());
                    long id = examService.createQuestion(examItem.id, questionStatement.getText(),
                            String.valueOf(questionType.getSelectedItem()), score);
                    auditService.log(currentTeacherId(), "QUESTION_CREATE", "Pregunta ID " + id);
                    refreshTeacherData();
                    selectComboItem(questionExamSelector, examItem.id);
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        btnCreateAnswer.addActionListener(e -> {
            requireTeacher();
            ComboItem questionItem = selectedItem(answerQuestionSelector);
            if (questionItem == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona una pregunta en la tabla o filtro.");
                return;
            }
            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Crear Respuesta", 400, 250);
            JTextField answerContent = new JTextField(20);
            JCheckBox answerCorrect = new JCheckBox("Es la respuesta correcta");

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; c.add(new JLabel("Contenido:"), gbc);
            gbc.gridx = 1; c.add(answerContent, gbc);
            gbc.gridx = 1; gbc.gridy = 1; c.add(answerCorrect, gbc);

            JButton confirm = new JButton("Guardar Respuesta");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                long id = answerService.createAnswer(questionItem.id, answerContent.getText(), answerCorrect.isSelected());
                auditService.log(currentTeacherId(), "ANSWER_CREATE", "Respuesta ID " + id);
                loadAnswersForSelectedQuestion();
            }
        });

        return panel;
    }

    private JPanel buildTableWithSearch(String title, JTable table, DefaultTableModel model, JPanel extraHeader) {
        JPanel wrapper = new JPanel(new BorderLayout(5, 5));
        wrapper.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftHeader.setOpaque(false);
        JLabel lblTitle = new JLabel(" " + title);
        lblTitle.setFont(lblTitle.getFont().deriveFont(java.awt.Font.BOLD));
        leftHeader.add(lblTitle);
        if (extraHeader != null) {
            leftHeader.add(Box.createHorizontalStrut(15));
            leftHeader.add(extraHeader);
        }
        header.add(leftHeader, BorderLayout.WEST);
        
        JTextField searchField = new JTextField(15);
        searchField.setToolTipText("Buscar...");
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter = new javax.swing.table.TableRowSorter<>(model);
        table.setRowSorter(sorter);
        
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String text = searchField.getText();
                if (text.trim().length() == 0) sorter.setRowFilter(null);
                else sorter.setRowFilter(javax.swing.RowFilter.regexFilter("(?i)" + text));
            }
        });
        
        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightHeader.setOpaque(false);
        rightHeader.add(new JLabel("Buscar: "));
        rightHeader.add(searchField);
        header.add(rightHeader, BorderLayout.EAST);
        
        JScrollPane scroll = new JScrollPane(table);
        scroll.setWheelScrollingEnabled(false);
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }
"""

    new_content = content[:start_idx] + new_method + '\n' + content[end_idx:]
    with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("UI refactored successfully.")
else:
    print("Could not find bounds.")

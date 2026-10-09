with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    text = f.read()

# Normalize to LF
text = text.replace('\r\n', '\n')

# 1. Update examModel columns
old_model = 'new DefaultTableModel(new Object[]{"ID", "Curso", "Titulo", "Activo", "Vence", "Estado", "Creado"}, 0);'
new_model = 'new DefaultTableModel(new Object[]{"ID", "Curso", "Titulo", "Activo", "Vence", "Intentos", "Duracion (min)", "Estado", "Creado"}, 0);'
assert old_model in text, 'old_model not found'
text = text.replace(old_model, new_model, 1)

# 2. Update createExam call with attempts and duration
old_create = """long id = examService.createExam(courseItem.id, examTitle.getText(), examDescription.getText(),
                            examActive.isSelected(), spinnerToDateTime(examDueAt));
                    
                    // Llama al setter del nuevo DAO para limites, asumiendo que agregaste un metodo en el DAO, 
                    // o lo dejaremos que use los valores predeterminados de la BD por ahora ya que alteramos la tabla.
                    // Para asignar los limites, requeriria actualizar ExamService.createExam. 
                    // Vamos a pasar los argumentos extras al createExam."""

new_create = """long id = examService.createExam(courseItem.id, examTitle.getText(), examDescription.getText(),
                            examActive.isSelected(), spinnerToDateTime(examDueAt), attempts, duration);"""

assert old_create in text, 'old_create not found'
text = text.replace(old_create, new_create, 1)

# 3. Add btnEditExam listener
old_listener = 'btnEditQuestionnaire.addActionListener(e -> editQuestionnaire());'
new_listener = """btnEditExam.addActionListener(e -> editExamDetails());
        btnEditQuestionnaire.addActionListener(e -> editQuestionnaire());"""
assert old_listener in text, 'old_listener not found'
text = text.replace(old_listener, new_listener, 1)

# 4. Add editExamDetails method before editQuestionnaire
edit_exam_method = """    private void editExamDetails() {
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

    private void editQuestionnaire() {"""

old_edit_start = '    private void editQuestionnaire() {'
assert old_edit_start in text, 'old_edit_start not found'
text = text.replace(old_edit_start, edit_exam_method, 1)

# 5. Update examModel.addRow
old_row = """                examModel.addRow(new Object[]{
                        exam.getId(),
                        course.getName(),
                        exam.getTitle(),
                        exam.isActive(),
                        exam.getDueAt(),
                        describeExamStatus(exam),
                        exam.getCreatedAt()
                });"""

new_row = """                examModel.addRow(new Object[]{
                        exam.getId(),
                        course.getName(),
                        exam.getTitle(),
                        exam.isActive(),
                        exam.getDueAt(),
                        exam.getMaxAttempts(),
                        exam.getDurationMinutes(),
                        describeExamStatus(exam),
                        exam.getCreatedAt()
                });"""

assert old_row in text, 'old_row not found'
text = text.replace(old_row, new_row, 1)

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('TeacherPanel updated successfully!')

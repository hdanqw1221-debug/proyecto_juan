import re

with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    content = f.read()

start_idx = content.find('private JPanel buildGradesPanel() {')
end_idx = content.find('private JPanel buildStatsPanel() {')

if start_idx != -1 and end_idx != -1:
    new_grades = """private JPanel buildGradesPanel() {
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

        top.add(refresh);
        top.add(exportCsv);
        top.add(gradesSummary);

        JTable table = new JTable(gradeModel);
        JPanel pnlTable = buildTableWithSearch("Notas de Estudiantes", table, gradeModel, null);

        panel.add(top, BorderLayout.NORTH);
        panel.add(pnlTable, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField attemptIdField = new JTextField(8);
        attemptIdField.setEditable(false);
        JButton manualDetail = new JButton("Modificar Nota Manual");
        bottom.add(new JLabel("Intento Seleccionado:"));
        bottom.add(attemptIdField);
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
                    attemptIdField.setText(String.valueOf(table.getValueAt(row, 0)));
                }
            }
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
            gbc.gridx = 0; gbc.gridy = 0; c.add(new JLabel("Nueva Calificacion:"), gbc);
            gbc.gridx = 1; c.add(scoreInput, gbc);

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
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                    for (int i = 0; i < gradeModel.getColumnCount(); i++) {
                        writer.print(gradeModel.getColumnName(i) + (i == gradeModel.getColumnCount() - 1 ? "" : ","));
                    }
                    writer.println();
                    for (int i = 0; i < gradeModel.getRowCount(); i++) {
                        for (int j = 0; j < gradeModel.getColumnCount(); j++) {
                            Object val = gradeModel.getValueAt(i, j);
                            writer.print(val != null ? val.toString().replace(",", ";") : "");
                            writer.print(j == gradeModel.getColumnCount() - 1 ? "" : ",");
                        }
                        writer.println();
                    }
                    javax.swing.JOptionPane.showMessageDialog(this, "Archivo CSV exportado exitosamente.");
                } catch (Exception ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage());
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
    
    private String formatNumber(Double num) {
        return num == null ? "N/A" : String.format(java.util.Locale.US, "%.2f", num);
    }

    """
    
    content = content[:start_idx] + new_grades + content[end_idx:]

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("Grades Panel Refactored Part 2")

import re

with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    content = f.read()

start_idx = content.find('private JPanel buildGradesPanel() {')
end_idx = content.find('private JPanel buildStatsPanel() {')

if start_idx != -1 and end_idx != -1:
    new_grades = """private JPanel buildGradesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        JButton refresh = new JButton("Actualizar Notas");
        app.ui.UiTheme.styleSecondaryButton(refresh);
        
        JButton exportCsv = new JButton("Exportar a CSV");
        app.ui.UiTheme.styleButton(exportCsv);

        top.add(refresh);
        top.add(exportCsv);
        top.add(gradesSummary);

        JTable table = new JTable(gradesModel);
        JPanel pnlTable = buildTableWithSearch("Notas de Estudiantes", table, gradesModel, null);

        panel.add(top, BorderLayout.NORTH);
        panel.add(pnlTable, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectedAttemptId.setEditable(false);
        JButton manualDetail = new JButton("Modificar Nota (JSON)");
        JButton viewDetail = new JButton("Ver Resultado");
        bottom.add(new JLabel("Intento Seleccionado:"));
        bottom.add(selectedAttemptId);
        bottom.add(manualDetail);
        bottom.add(viewDetail);
        panel.add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> refreshTeacherData());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    selectedAttemptId.setText(String.valueOf(table.getValueAt(row, 0)));
                }
            }
        });

        manualDetail.addActionListener(e -> openGradeModal());
        viewDetail.addActionListener(e -> showResultDetail());
        exportCsv.addActionListener(e -> exportGradesToCsv());

        return panel;
    }

    private void exportGradesToCsv() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Guardar como CSV");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos CSV", "csv"));
        if (fileChooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
            }
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                for (int i = 0; i < gradesModel.getColumnCount(); i++) {
                    writer.print(gradesModel.getColumnName(i) + (i == gradesModel.getColumnCount() - 1 ? "" : ","));
                }
                writer.println();
                for (int i = 0; i < gradesModel.getRowCount(); i++) {
                    for (int j = 0; j < gradesModel.getColumnCount(); j++) {
                        Object val = gradesModel.getValueAt(i, j);
                        writer.print(val != null ? val.toString().replace(",", ";") : "");
                        writer.print(j == gradesModel.getColumnCount() - 1 ? "" : ",");
                    }
                    writer.println();
                }
                javax.swing.JOptionPane.showMessageDialog(this, "Archivo CSV exportado exitosamente.");
            } catch (Exception ex) {
                javax.swing.JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage());
            }
        }
    }

    private void openGradeModal() {
        requireTeacher();
        if (selectedAttemptId.getText().isBlank()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Selecciona un intento primero.");
            return;
        }

        app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Ingresar Calificacion", 400, 300);
        JTextArea jsonInput = new JTextArea("{\\n  \\"calificacion\\": 0,\\n  \\"comentarios\\": \\"\\"\\n}", 8, 30);
        
        JPanel c = modal.getContent();
        c.setLayout(new BorderLayout(10, 10));
        c.add(new JLabel("Formato JSON requerido:"), BorderLayout.NORTH);
        c.add(new JScrollPane(jsonInput), BorderLayout.CENTER);

        JButton confirm = new JButton("Guardar Nota");
        app.ui.UiTheme.styleButton(confirm);
        JButton cancel = new JButton("Cancelar");
        app.ui.UiTheme.styleSecondaryButton(cancel);
        modal.addActions(confirm, cancel);
        modal.setVisible(true);

        if (modal.isConfirmed()) {
            long attemptId = Long.parseLong(selectedAttemptId.getText().trim());
            resultService.saveResult(attemptId, jsonInput.getText());
            auditService.log(currentTeacherId(), "GRADE_UPDATE", "Intento ID " + attemptId);
            refreshTeacherData();
            javax.swing.JOptionPane.showMessageDialog(this, "Calificacion guardada.");
        }
    }

    """
    
    # We must replace old action listeners for grading which were part of buildGradesPanel
    content = content[:start_idx] + new_grades + content[end_idx:]

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("Grades Panel Refactored")

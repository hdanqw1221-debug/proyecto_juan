import re

with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Refactor buildSimulationPanel
start_idx = content.find('private JPanel buildSimulationPanel() {')
end_idx = content.find('private JPanel buildGradesPanel() {')

if start_idx != -1 and end_idx != -1:
    new_sim = """private JPanel buildSimulationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        
        top.add(new JLabel("Curso:"));
        top.add(simulationCourseSelector);
        
        JButton refresh = new JButton("Listar");
        app.ui.UiTheme.styleSecondaryButton(refresh);
        top.add(refresh);
        top.add(simulationSummary);

        JButton create = new JButton("+ Crear Simulacion");
        app.ui.UiTheme.styleButton(create);
        top.add(create);

        JTable table = new JTable(simulationModel);
        JPanel pnlTable = buildTableWithSearch("Simulaciones", table, simulationModel, null);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectedSimulationId.setEditable(false);
        JButton loadTemplate = new JButton("Cargar Plantilla");
        JButton enableJson = new JButton("Habilitar JSON");
        JButton disableJson = new JButton("Deshabilitar JSON");
        bottom.add(new JLabel("Simulacion Seleccionada:"));
        bottom.add(selectedSimulationId);
        bottom.add(loadTemplate);
        bottom.add(enableJson);
        bottom.add(disableJson);

        panel.add(top, BorderLayout.NORTH);
        panel.add(pnlTable, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> refreshSimulations());
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    selectedSimulationId.setText(String.valueOf(table.getValueAt(row, 0)));
                }
            }
        });

        create.addActionListener(e -> {
            requireTeacher();
            ComboItem courseItem = selectedItem(simulationCourseSelector);
            if (courseItem == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona un curso primero.");
                return;
            }

            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Crear Simulacion", 500, 400);
            JTextField name = new JTextField(20);
            JTextField desc = new JTextField(20);
            JTextArea config = new JTextArea(app.service.SimulationService.defaultConfigJson(), 10, 35);
            config.setLineWrap(true);
            config.setWrapStyleWord(true);

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new java.awt.Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; c.add(new JLabel("Nombre:"), gbc);
            gbc.gridx = 1; c.add(name, gbc);
            gbc.gridx = 0; gbc.gridy = 1; c.add(new JLabel("Descripcion:"), gbc);
            gbc.gridx = 1; c.add(desc, gbc);
            gbc.gridx = 0; gbc.gridy = 2; c.add(new JLabel("Config JSON:"), gbc);
            gbc.gridx = 1; c.add(new JScrollPane(config), gbc);

            JButton confirm = new JButton("Guardar");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                try {
                    long id = simulationService.createSimulation(courseItem.id, name.getText(), desc.getText(), config.getText());
                    auditService.log(currentTeacherId(), "SIM_CREATE", "Simulacion ID " + id);
                    refreshSimulations();
                    javax.swing.JOptionPane.showMessageDialog(this, "Simulacion creada. ID: " + id);
                } catch (IllegalArgumentException ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion JSON", javax.swing.JOptionPane.WARNING_MESSAGE);
                }
            }
        });

        loadTemplate.addActionListener(e -> {
            // Re-implement if needed, but the original logic updated the inline text area
            // Now we can just show a modal to see the template
            JTextArea area = new JTextArea(app.service.SimulationService.defaultConfigJson(), 15, 40);
            area.setEditable(false);
            javax.swing.JOptionPane.showMessageDialog(this, new JScrollPane(area), "Plantilla JSON", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });

        enableJson.addActionListener(e -> toggleSimulationStatus(true));
        disableJson.addActionListener(e -> toggleSimulationStatus(false));

        return panel;
    }

    """
    content = content[:start_idx] + new_sim + content[end_idx:]

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(content)
print("Simulation Panel Refactored")

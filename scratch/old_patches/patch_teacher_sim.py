# -*- coding: utf-8 -*-
with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\TeacherPanel.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update simulationModel declaration
old_model = """    private final DefaultTableModel simulationModel =
            new DefaultTableModel(new Object[]{"ID", "Curso", "Nombre", "JSON habilitado", "Configuracion"}, 0);"""

new_model = """    private static final String[] MODEL_3D_OPTIONS = {
            "(Ninguno)",
            "sistema_cardiovascular.py",
            "sistema_oseo.py",
            "sistema_muscular.py",
            "sistema_organosinternos.py",
            "piel_caballo.py"
    };

    private final DefaultTableModel simulationModel =
            new DefaultTableModel(new Object[]{"ID", "Curso", "Nombre", "Modelo 3D", "Habilitado", "Configuracion"}, 0);"""

assert old_model in text, "old_model not found"
text = text.replace(old_model, new_model, 1)

# 2. Update buildSimulationPanel bottom bar and create/edit actions
old_bottom = """        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectedSimulationId.setEditable(false);
        JButton loadTemplate = new JButton("Cargar Plantilla");
        JButton enableJson = new JButton("Habilitar JSON");
        JButton disableJson = new JButton("Deshabilitar JSON");
        bottom.add(new JLabel("Simulacion Seleccionada:"));
        bottom.add(selectedSimulationId);
        bottom.add(loadTemplate);
        bottom.add(enableJson);
        bottom.add(disableJson);"""

new_bottom = """        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectedSimulationId.setEditable(false);
        JButton loadTemplate = new JButton("Cargar Plantilla");
        JButton editSim = new JButton("Editar Simulacion");
        app.ui.UiTheme.styleSecondaryButton(editSim);
        JButton enableJson = new JButton("Habilitar JSON");
        JButton disableJson = new JButton("Deshabilitar JSON");
        bottom.add(new JLabel("Simulacion Seleccionada:"));
        bottom.add(selectedSimulationId);
        bottom.add(editSim);
        bottom.add(loadTemplate);
        bottom.add(enableJson);
        bottom.add(disableJson);"""

assert old_bottom in text, "old_bottom not found"
text = text.replace(old_bottom, new_bottom, 1)

old_create = """        create.addActionListener(e -> {
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
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; c.add(new JLabel("Nombre:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(name, gbc);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; c.add(new JLabel("Descripcion:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(desc, gbc);
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; gbc.weighty = 0.0; c.add(new JLabel("Config JSON:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH; c.add(new JScrollPane(config), gbc);

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
        });"""

new_create = """        create.addActionListener(e -> {
            requireTeacher();
            ComboItem courseItem = selectedItem(simulationCourseSelector);
            if (courseItem == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona un curso primero.");
                return;
            }

            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Crear Simulacion", 540, 460);
            JTextField name = new JTextField(20);
            JTextField desc = new JTextField(20);
            JComboBox<String> modelCombo = new JComboBox<>(MODEL_3D_OPTIONS);
            modelCombo.setSelectedItem("sistema_cardiovascular.py");
            JTextArea config = new JTextArea(app.service.SimulationService.defaultConfigJson(), 10, 35);
            config.setLineWrap(true);
            config.setWrapStyleWord(true);

            modelCombo.addActionListener(ev -> {
                String chosen = (String) modelCombo.getSelectedItem();
                String txt = config.getText();
                if (chosen != null && !chosen.equals("(Ninguno)")) {
                    if (txt.contains("\\"script3d\\"")) {
                        txt = txt.replaceAll("\\"script3d\\"\\\\s*:\\\\s*\\"[^\\"]*\\"", "\\"script3d\\": \\"" + chosen + "\\"");
                    } else {
                        txt = txt.replaceFirst("\\\\{", "{\\\\n  \\"script3d\\": \\"" + chosen + "\\",");
                    }
                    config.setText(txt);
                }
            });

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new java.awt.Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; c.add(new JLabel("Nombre:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(name, gbc);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; c.add(new JLabel("Descripcion:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(desc, gbc);
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; c.add(new JLabel("Modelo 3D:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(modelCombo, gbc);
            gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.weighty = 0.0; c.add(new JLabel("Config JSON:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH; c.add(new JScrollPane(config), gbc);

            JButton confirm = new JButton("Guardar");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                try {
                    String json = config.getText();
                    String chosen = (String) modelCombo.getSelectedItem();
                    if (chosen != null && !chosen.equals("(Ninguno)")) {
                        if (!json.contains("\\"script3d\\"")) {
                            json = json.replaceFirst("\\\\{", "{\\\\n  \\"script3d\\": \\"" + chosen + "\\",");
                        }
                    }
                    long id = simulationService.createSimulation(courseItem.id, name.getText().trim(), desc.getText().trim(), json);
                    auditService.log(currentTeacherId(), "SIM_CREATE", "Simulacion ID " + id);
                    refreshSimulations();
                    javax.swing.JOptionPane.showMessageDialog(this, "Simulacion creada con exito. ID: " + id);
                } catch (IllegalArgumentException ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion JSON", javax.swing.JOptionPane.WARNING_MESSAGE);
                }
            }
        });

        editSim.addActionListener(e -> {
            requireTeacher();
            if (selectedSimulationId.getText().isBlank()) {
                javax.swing.JOptionPane.showMessageDialog(this, "Selecciona una simulacion para editar.");
                return;
            }
            long simId = Long.parseLong(selectedSimulationId.getText().trim());
            Simulation sim = simulationService.findById(simId);
            if (sim == null) {
                javax.swing.JOptionPane.showMessageDialog(this, "No se encontro la simulacion.");
                return;
            }

            app.ui.FormModal modal = new app.ui.FormModal((java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this), "Editar Simulacion", 540, 460);
            JTextField name = new JTextField(sim.getName(), 20);
            JTextField desc = new JTextField(sim.getDescription() != null ? sim.getDescription() : "", 20);
            JComboBox<String> modelCombo = new JComboBox<>(MODEL_3D_OPTIONS);
            String currentScript = SimulationService.getScript3D(sim);
            if (currentScript != null) {
                for (int i = 0; i < MODEL_3D_OPTIONS.length; i++) {
                    if (MODEL_3D_OPTIONS[i].equalsIgnoreCase(currentScript)) {
                        modelCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
            JTextArea config = new JTextArea(sim.getConfigJson() != null ? sim.getConfigJson() : app.service.SimulationService.defaultConfigJson(), 10, 35);
            config.setLineWrap(true);
            config.setWrapStyleWord(true);

            modelCombo.addActionListener(ev -> {
                String chosen = (String) modelCombo.getSelectedItem();
                String txt = config.getText();
                if (chosen != null && !chosen.equals("(Ninguno)")) {
                    if (txt.contains("\\"script3d\\"")) {
                        txt = txt.replaceAll("\\"script3d\\"\\\\s*:\\\\s*\\"[^\\"]*\\"", "\\"script3d\\": \\"" + chosen + "\\"");
                    } else {
                        txt = txt.replaceFirst("\\\\{", "{\\\\n  \\"script3d\\": \\"" + chosen + "\\",");
                    }
                    config.setText(txt);
                }
            });

            JPanel c = modal.getContent();
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new java.awt.Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; c.add(new JLabel("Nombre:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(name, gbc);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; c.add(new JLabel("Descripcion:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(desc, gbc);
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0; c.add(new JLabel("Modelo 3D:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; c.add(modelCombo, gbc);
            gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.weighty = 0.0; c.add(new JLabel("Config JSON:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH; c.add(new JScrollPane(config), gbc);

            JButton confirm = new JButton("Guardar Cambios");
            app.ui.UiTheme.styleButton(confirm);
            JButton cancel = new JButton("Cancelar");
            app.ui.UiTheme.styleSecondaryButton(cancel);
            modal.addActions(confirm, cancel);
            modal.setVisible(true);

            if (modal.isConfirmed()) {
                try {
                    String json = config.getText();
                    String chosen = (String) modelCombo.getSelectedItem();
                    if (chosen != null && !chosen.equals("(Ninguno)")) {
                        if (!json.contains("\\"script3d\\"")) {
                            json = json.replaceFirst("\\\\{", "{\\\\n  \\"script3d\\": \\"" + chosen + "\\",");
                        }
                    }
                    simulationService.updateDetails(simId, name.getText().trim(), desc.getText().trim(), json);
                    auditService.log(currentTeacherId(), "SIM_UPDATE_DETAILS", "Simulacion ID " + simId);
                    refreshSimulations();
                    javax.swing.JOptionPane.showMessageDialog(this, "Simulacion actualizada con exito.");
                } catch (IllegalArgumentException ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion JSON", javax.swing.JOptionPane.WARNING_MESSAGE);
                }
            }
        });"""

assert old_create in text, "old_create not found"
text = text.replace(old_create, new_create, 1)

# 3. Update refreshSimulations
old_refresh = """        for (Simulation simulation : simulations) {
            boolean enabled = SimulationService.isEnabled(simulation);
            if (enabled) {
                enabledCount++;
            }
            simulationModel.addRow(new Object[]{
                    simulation.getId(),
                    courseName,
                    simulation.getName(),
                    enabled ? "Si" : "No",
                    simulation.getConfigJson()
            });
        }"""

new_refresh = """        for (Simulation simulation : simulations) {
            boolean enabled = SimulationService.isEnabled(simulation);
            if (enabled) {
                enabledCount++;
            }
            String script3d = SimulationService.getScript3D(simulation);
            simulationModel.addRow(new Object[]{
                    simulation.getId(),
                    courseName,
                    simulation.getName(),
                    script3d != null ? script3d : "Ninguno",
                    enabled ? "Si" : "No",
                    simulation.getConfigJson()
            });
        }"""

assert old_refresh in text, "old_refresh not found"
text = text.replace(old_refresh, new_refresh, 1)

with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)

print("TeacherPanel.java patched successfully")

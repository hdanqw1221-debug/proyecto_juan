# -*- coding: utf-8 -*-
with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\StudentPanel.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update simulationsModel column headers
old_table_def = """    private final DefaultTableModel simulationsModel =
            new DefaultTableModel(new Object[]{"ID", "Materia", "Simulacion", "Descripcion"}, 0);"""

new_table_def = """    private final DefaultTableModel simulationsModel =
            new DefaultTableModel(new Object[]{"ID", "Materia", "Simulacion", "Modelo 3D", "Descripcion"}, 0);"""

assert old_table_def in text, "old_table_def not found"
text = text.replace(old_table_def, new_table_def, 1)

# 2. Update run button action listener to call showSimulationPracticeDialog
old_run_action = """        run.addActionListener(e -> {
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
            JTextArea area = new JTextArea(SimulationService.summarizeSimulation(simulation), 18, 55);
            area.setEditable(false);
            area.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Detalle de la practica", JOptionPane.INFORMATION_MESSAGE);
        });"""

new_run_action = """        run.addActionListener(e -> {
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
        });"""

assert old_run_action in text, "old_run_action not found"
text = text.replace(old_run_action, new_run_action, 1)

# 3. Update loadSimulations to populate the 3D model column
old_load = """        List<Simulation> simulations = simulationService.listEnabledByCourse(courseItem.id);
        for (Simulation simulation : simulations) {
            simulationsModel.addRow(new Object[]{
                    simulation.getId(),
                    courseItem.label,
                    simulation.getName(),
                    simulation.getDescription()
            });
        }"""

new_load = """        List<Simulation> simulations = simulationService.listEnabledByCourse(courseItem.id);
        for (Simulation simulation : simulations) {
            String script3d = SimulationService.getScript3D(simulation);
            simulationsModel.addRow(new Object[]{
                    simulation.getId(),
                    courseItem.label,
                    simulation.getName(),
                    script3d != null ? script3d : "Teorica",
                    simulation.getDescription()
            });
        }"""

assert old_load in text, "old_load not found"
text = text.replace(old_load, new_load, 1)

# 4. Add showSimulationPracticeDialog helper method
dialog_method = """    private void showSimulationPracticeDialog(Simulation simulation) {
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
        Path scriptsFolder = resolvePythonScriptsFolder();
        Path scriptPath = (scriptName != null && scriptsFolder != null) ? scriptsFolder.resolve(scriptName) : null;

        if (scriptPath != null && Files.isRegularFile(scriptPath)) {
            JButton launch3d = new JButton("Abrir Modelo 3D (" + simulationDisplayName(scriptPath) + ")");
            UiTheme.styleButton(launch3d);
            launch3d.addActionListener(ev -> {
                auditService.log(currentStudentId(), "SIM_RUN_3D_PRACTICE", "Simulacion " + simulation.getName() + " -> " + scriptName);
                runPythonScript(scriptPath);
                dialog.dispose();
            });
            actions.add(launch3d);
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

"""

# Insert before buildPythonPanel
assert "private JPanel buildPythonPanel()" in text, "buildPythonPanel not found"
text = text.replace("    private JPanel buildPythonPanel()", dialog_method + "    private JPanel buildPythonPanel()")

with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\StudentPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)

print("StudentPanel.java patched successfully")

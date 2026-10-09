# -*- coding: utf-8 -*-
with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\TeacherPanel.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Add imports if needed
old_imports = """import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;"""

new_imports = """import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;"""

assert old_imports in text, "old_imports not found"
text = text.replace(old_imports, new_imports, 1)

# 2. Add candidates constant inside TeacherPanel class
old_class_start = """public class TeacherPanel extends JPanel {
    private final SessionManager sessionManager;"""

new_class_start = """public class TeacherPanel extends JPanel {
    private static final String[] PYTHON_SIM_DIR_CANDIDATES = {
            "app/aplicacion_tres_d/aplicacion_tres_d",
            "app/aplicacion_3_D/aplicacion_3_D",
            "aplicacion_tres_d/aplicacion_tres_d",
            "aplicacion_3_D/aplicacion_3_D",
            "aplicacion_3_D",
            "aplicacion_tres_d"
    };

    private final SessionManager sessionManager;"""

assert old_class_start in text, "old_class_start not found"
text = text.replace(old_class_start, new_class_start, 1)

# 3. Add preview3d and deleteSim in buildSimulationPanel
old_bottom = """        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
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

new_bottom = """        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectedSimulationId.setEditable(false);
        JButton preview3d = new JButton("▶ Probar Modelo 3D");
        app.ui.UiTheme.styleButton(preview3d);
        JButton editSim = new JButton("Editar Simulacion");
        app.ui.UiTheme.styleSecondaryButton(editSim);
        JButton deleteSim = new JButton("Eliminar");
        app.ui.UiTheme.styleSecondaryButton(deleteSim);
        JButton loadTemplate = new JButton("Cargar Plantilla");
        JButton enableJson = new JButton("Habilitar JSON");
        JButton disableJson = new JButton("Deshabilitar JSON");
        bottom.add(new JLabel("Simulacion Seleccionada:"));
        bottom.add(selectedSimulationId);
        bottom.add(preview3d);
        bottom.add(editSim);
        bottom.add(deleteSim);
        bottom.add(loadTemplate);
        bottom.add(enableJson);
        bottom.add(disableJson);"""

assert old_bottom in text, "old_bottom not found"
text = text.replace(old_bottom, new_bottom, 1)

# 4. Add action listeners for preview3d and deleteSim right before enableJson.addActionListener
target_anchor = "        enableJson.addActionListener(e -> toggleSimulationStatus(true));"
new_listeners = """        deleteSim.addActionListener(e -> {
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
                String[] availableModels = {
                        "sistema_cardiovascular.py",
                        "sistema_oseo.py",
                        "sistema_muscular.py",
                        "sistema_organosinternos.py",
                        "piel_caballo.py"
                };
                Object choice = javax.swing.JOptionPane.showInputDialog(
                        this,
                        "Selecciona el modelo 3D que deseas explorar y probar:",
                        "Probar Visor Anatomico 3D",
                        javax.swing.JOptionPane.QUESTION_MESSAGE,
                        null,
                        availableModels,
                        availableModels[0]
                );
                if (choice != null) {
                    scriptToRun = choice.toString();
                } else {
                    return;
                }
            }
            runTeacherPythonScript(scriptToRun);
        });

"""

assert target_anchor in text, "target_anchor not found"
text = text.replace(target_anchor, new_listeners + target_anchor, 1)

# 5. Add Python runner helper methods at end of class
python_helpers = """
    private void runTeacherPythonScript(String scriptName) {
        Path scriptsFolder = resolvePythonScriptsFolder();
        if (scriptsFolder == null) {
            javax.swing.JOptionPane.showMessageDialog(this, "No pudimos localizar la carpeta de modelos 3D.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        Path scriptPath = scriptsFolder.resolve(scriptName);
        if (!Files.isRegularFile(scriptPath)) {
            javax.swing.JOptionPane.showMessageDialog(this, "El archivo " + scriptName + " no existe.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        PythonCommand pythonCommand = detectPythonCommand();
        if (pythonCommand == null) {
            javax.swing.JOptionPane.showMessageDialog(this, "No se detecto un entorno Python compatible con PyQt5 y VTK.", "Python no listo", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        auditService.log(currentTeacherId(), "TEACHER_SIM_3D", "Vista previa 3D: " + scriptName);
        launchSimulation(pythonCommand, scriptPath);
    }

    private void launchSimulation(PythonCommand pythonCommand, Path scriptPath) {
        JDialog progress = progressDialog("Abriendo modelo 3D", "Preparando la ventana interactiva...");
        List<String> command = pythonCommand.append(scriptPath.getFileName().toString());

        SwingWorker<CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CommandResult doInBackground() {
                try {
                    Process process = new ProcessBuilder(command)
                            .directory(scriptPath.getParent().toFile())
                            .redirectErrorStream(true)
                            .start();
                    SwingUtilities.invokeLater(progress::dispose);
                    String output;
                    try (var input = process.getInputStream()) {
                        output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    return new CommandResult(process.waitFor(), output, null);
                } catch (Exception ex) {
                    return new CommandResult(-1, "", ex);
                }
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    CommandResult result = get();
                    if (result.error != null || result.exitCode != 0) {
                        javax.swing.JOptionPane.showMessageDialog(TeacherPanel.this,
                                "El modelo no pudo abrirse correctamente.\\nVerifica aceleracion grafica y librerias.",
                                "Aviso", javax.swing.JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ignored) {}
            }
        };

        worker.execute();
        progress.setVisible(true);
        javax.swing.Timer hideProgress = new javax.swing.Timer(1800, e -> progress.dispose());
        hideProgress.setRepeats(false);
        hideProgress.start();
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

    private Path resolvePythonScriptsFolder() {
        Path workspaceRoot = Path.of(System.getProperty("user.dir"));
        for (String candidate : PYTHON_SIM_DIR_CANDIDATES) {
            Path folder = workspaceRoot.resolve(candidate).normalize();
            if (Files.isDirectory(folder)) {
                return folder;
            }
        }
        return null;
    }

    private PythonCommand detectPythonCommand() {
        for (PythonCommand candidate : pythonCommandCandidates()) {
            if (hasSimulationDependencies(candidate.command)) {
                return candidate;
            }
        }
        return null;
    }

    private List<PythonCommand> pythonCommandCandidates() {
        List<PythonCommand> candidates = new ArrayList<>();
        Path scriptsFolder = resolvePythonScriptsFolder();
        if (scriptsFolder != null) {
            Path venvPython = scriptsFolder.resolve(".venv").resolve("Scripts").resolve("python.exe");
            if (Files.isRegularFile(venvPython)) {
                candidates.add(new PythonCommand(List.of(venvPython.toString()), "Entorno local .venv"));
            }
        }
        candidates.addAll(List.of(
                new PythonCommand(List.of("py", "-3.9"), "py -3.9"),
                new PythonCommand(List.of("py", "-3.11"), "py -3.11"),
                new PythonCommand(List.of("py", "-3.10"), "py -3.10"),
                new PythonCommand(List.of("py", "-3"), "py -3"),
                new PythonCommand(List.of("python"), "python"),
                new PythonCommand(List.of("python3"), "python3")
        ));
        return candidates;
    }

    private boolean hasSimulationDependencies(List<String> baseCommand) {
        return executeProbe(baseCommand, "import PyQt5, vtk; print('OK')");
    }

    private boolean executeProbe(List<String> baseCommand, String pythonSnippet) {
        List<String> command = new ArrayList<>(baseCommand);
        command.add("-c");
        command.add(pythonSnippet);
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2200, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) {
                return false;
            }
            try (var input = process.getInputStream()) {
                String output = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
                return output.contains("OK");
            }
        } catch (Exception ex) {
            return false;
        }
    }

    private record PythonCommand(List<String> command, String label) {
        List<String> append(String... args) {
            List<String> full = new ArrayList<>(command);
            full.addAll(List.of(args));
            return full;
        }
    }

    private record CommandResult(int exitCode, String output, Exception error) {}
}
"""

last_brace_idx = text.rfind("}")
assert last_brace_idx != -1, "closing brace not found"
text = text[:last_brace_idx] + python_helpers

with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)

print("TeacherPanel.java patched successfully with preview3d and deleteSim")

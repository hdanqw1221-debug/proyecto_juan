package app.ui;

import app.model.Simulation;
import app.service.SimulationConfig;
import app.service.SimulationService;
import app.service.Visor3DService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/** Visual editor for the practice settings stored as JSON in PostgreSQL. */
public final class SimulationFormDialog {
    public record Result(String name, String description, String configJson) {}

    private SimulationFormDialog() {}

    public static Result show(Component owner, Simulation existing) {
        String initialScript = existing == null ? "cavidad_toracica.py" : SimulationService.getScript3D(existing);
        String initialJson = existing != null && existing.getConfigJson() != null
                ? existing.getConfigJson() : SimulationService.defaultConfigJson(initialScript);
        SimulationConfig settings;
        try {
            settings = SimulationConfig.fromJson(initialJson);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(owner, "La configuracion de esta practica no se puede abrir: " + ex.getMessage(),
                    "Practica no disponible", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        JTextField name = new JTextField(existing == null ? "" : existing.getName(), 32);
        JTextField description = new JTextField(existing == null || existing.getDescription() == null ? "" : existing.getDescription(), 32);
        JComboBox<String> model = new JComboBox<>();
        model.addItem("");
        for (Visor3DService.Modelo item : Visor3DService.MODELOS) model.addItem(item.script());
        model.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                      boolean selected, boolean focus) {
                String script = (String) value;
                String label = script == null || script.isBlank() ? "Sin modelo 3D" : Visor3DService.titulo(script);
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        String savedScript = settings.string("script3d", initialScript == null ? "" : initialScript);
        if (!savedScript.isBlank() && Visor3DService.modelo(savedScript) == null) model.addItem(savedScript);
        model.setSelectedItem(savedScript);
        JSpinner duration = new JSpinner(new SpinnerNumberModel(Math.max(1, settings.integer("tiempoMinutos", 45)), 1, 600, 1));
        JTextField topic = new JTextField(settings.string("tema", "cavidades_equinas"), 32);
        JTextField level = new JTextField(settings.string("nivel", "intermedio"), 32);
        JTextArea objectives = area(String.join("\n", settings.strings("objetivos")), 4);
        JTextArea instructions = area(String.join("\n", settings.strings("instrucciones")), 5);
        JTextField scenarioTitle = new JTextField(settings.scenario("titulo", ""), 32);
        JTextArea scenarioDescription = area(settings.scenario("descripcion", ""), 3);
        JCheckBox enabled = new JCheckBox("Disponible para estudiantes", settings.bool("habilitado", true));
        enabled.setOpaque(false);

        String[] previous = {(String) model.getSelectedItem()};
        model.addActionListener(event -> {
            String selected = (String) model.getSelectedItem();
            if (selected == null || selected.isBlank() || selected.equals(previous[0])) return;
            SimulationConfig suggested = SimulationConfig.fromJson(SimulationService.defaultConfigJson(selected));
            if (previous[0] != null && !previous[0].isBlank()) {
                SimulationConfig old = SimulationConfig.fromJson(SimulationService.defaultConfigJson(previous[0]));
                if (objectives.getText().strip().equals(String.join("\n", old.strings("objetivos")))) {
                    objectives.setText(String.join("\n", suggested.strings("objetivos")));
                }
                if (instructions.getText().strip().equals(String.join("\n", old.strings("instrucciones")))) {
                    instructions.setText(String.join("\n", suggested.strings("instrucciones")));
                }
                if (scenarioTitle.getText().strip().equals(old.scenario("titulo", ""))) {
                    scenarioTitle.setText(suggested.scenario("titulo", ""));
                }
                if (scenarioDescription.getText().strip().equals(old.scenario("descripcion", ""))) {
                    scenarioDescription.setText(suggested.scenario("descripcion", ""));
                }
            }
            previous[0] = selected;
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        int row = 0;
        row = addRow(form, row, "Nombre de la practica", name);
        row = addRow(form, row, "Descripcion breve", description);
        row = addRow(form, row, "Modelo anatomico 3D", model);
        row = addRow(form, row, "Duracion (minutos)", duration);
        row = addRow(form, row, "Tema", topic);
        row = addRow(form, row, "Nivel", level);
        row = addRow(form, row, "Objetivos (uno por linea)", new JScrollPane(objectives));
        row = addRow(form, row, "Instrucciones (una por linea)", new JScrollPane(instructions));
        row = addRow(form, row, "Titulo del escenario", scenarioTitle);
        row = addRow(form, row, "Descripcion del escenario", new JScrollPane(scenarioDescription));
        addRow(form, row, "Estado", enabled);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(720, 580));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        String title = existing == null ? "Crear practica" : "Editar practica";
        while (true) {
            int choice = JOptionPane.showOptionDialog(owner, scroll, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, new String[]{"Guardar", "Cancelar"}, "Guardar");
            if (choice != 0) return null;
            if (name.getText().isBlank() || objectives.getText().isBlank() || instructions.getText().isBlank()) {
                JOptionPane.showMessageDialog(owner, "Completa el nombre, los objetivos y las instrucciones.",
                        "Faltan datos", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            settings.putBoolean("habilitado", enabled.isSelected());
            settings.putString("tema", topic.getText().strip());
            settings.putString("nivel", level.getText().strip());
            settings.putString("script3d", (String) model.getSelectedItem());
            settings.putInt("tiempoMinutos", (Integer) duration.getValue());
            settings.putStrings("objetivos", lines(objectives));
            settings.putStrings("instrucciones", lines(instructions));
            settings.putScenario(scenarioTitle.getText().strip(), scenarioDescription.getText().strip());
            String json = settings.toJson();
            try {
                SimulationService.validateConfigJson(json);
                SimulationConfig.fromJson(json);
                return new Result(name.getText().strip(), description.getText().strip(), json);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(owner, ex.getMessage(), "Datos no validos", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static JTextArea area(String value, int rows) {
        JTextArea area = new JTextArea(value, rows, 32);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static List<String> lines(JTextArea area) {
        return Arrays.stream(area.getText().split("\\R"))
                .map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private static int addRow(JPanel panel, int row, String label, Component field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0; left.gridy = row; left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(7, 0, 7, 18);
        JLabel title = new JLabel(label);
        title.setForeground(UiTheme.INK);
        panel.add(title, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1; right.gridy = row; right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 0, 5, 0);
        panel.add(field, right);
        return row + 1;
    }
}

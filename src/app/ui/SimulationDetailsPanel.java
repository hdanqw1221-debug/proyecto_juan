package app.ui;

import app.model.Simulation;
import app.service.SimulationConfig;
import app.service.Visor3DService;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Readable presentation of a practice for students. */
public final class SimulationDetailsPanel extends JPanel {
    public SimulationDetailsPanel(Simulation simulation) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        SimulationConfig settings = SimulationConfig.fromJson(simulation.getConfigJson());
        String script = settings.string("script3d", "");

        JPanel overview = section("Datos de la practica");
        addLine(overview, "Tema", settings.string("tema", "Sin definir"));
        addLine(overview, "Nivel", settings.string("nivel", "Sin definir"));
        addLine(overview, "Duracion", settings.integer("tiempoMinutos", 0) + " minutos");
        if (!script.isBlank()) addLine(overview, "Modelo 3D", Visor3DService.titulo(script));
        finish(overview);

        addList("Objetivos", settings.strings("objetivos"));
        addList("Instrucciones", settings.strings("instrucciones"));

        JPanel scenario = section("Escenario");
        String title = settings.scenario("titulo", "");
        if (!title.isBlank()) {
            JLabel heading = UiTheme.title(title, 15f);
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            scenario.add(heading);
            scenario.add(Box.createVerticalStrut(6));
        }
        String description = settings.scenario("descripcion", "");
        scenario.add(paragraph(description.isBlank() ? "Sin descripcion." : description));
        finish(scenario);
    }

    private JPanel section(String title) {
        JPanel card = UiTheme.card(new BorderLayout());
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel heading = UiTheme.title(title, 17f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(10));
        card.add(content, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(card);
        add(Box.createVerticalStrut(10));
        return content;
    }

    private void addList(String title, List<String> items) {
        JPanel section = section(title);
        if (items.isEmpty()) {
            section.add(paragraph("Sin contenido."));
        } else {
            int index = 1;
            for (String item : items) {
                section.add(paragraph(index++ + ". " + item));
                section.add(Box.createVerticalStrut(6));
            }
        }
        finish(section);
    }

    private void addLine(JPanel section, String label, String value) {
        section.add(paragraph(label + ": " + value));
        section.add(Box.createVerticalStrut(5));
    }

    private void finish(JPanel content) {
        JPanel card = (JPanel) content.getParent();
        if (card != null) card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
    }

    private JLabel paragraph(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("\n", "<br>");
        JLabel label = new JLabel("<html><div style='width:540px'>" + safe + "</div></html>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        label.setForeground(UiTheme.INK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}

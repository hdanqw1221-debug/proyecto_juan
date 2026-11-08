package app.ui;

import app.service.Visor3DService;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

/** A catalog whose cards and text always fit the scroll pane's viewport. */
public final class ModelCatalogPanel extends JPanel implements Scrollable {
    private static final int GAP = 14;
    private static final int MIN_CARD_WIDTH = 360;

    public ModelCatalogPanel() {
        super(new GridLayout(0, 1, GAP, GAP));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    public void addModel(Visor3DService.Modelo model, boolean ready, Runnable onOpen) {
        JPanel card = UiTheme.card(new BorderLayout(12, 12));
        JPanel heading = new JPanel(new BorderLayout(0, 6));
        heading.setOpaque(false);
        JLabel type = new JLabel("MODELO 3D");
        type.setForeground(UiTheme.INFO);
        type.setFont(type.getFont().deriveFont(Font.BOLD, 11f));
        JLabel title = UiTheme.title("<html>" + model.titulo() + "</html>", 18f);
        heading.add(type, BorderLayout.NORTH);
        heading.add(title, BorderLayout.CENTER);

        JLabel description = new JLabel("<html>" + model.descripcion() + "</html>");
        description.setForeground(UiTheme.MUTED);
        description.setVerticalAlignment(SwingConstants.TOP);

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        JLabel status = new JLabel(ready ? "Listo para explorar" : "Se preparara al abrir");
        status.setForeground(ready ? UiTheme.SUCCESS : UiTheme.WARNING);
        JButton open = new JButton("Abrir modelo");
        open.addActionListener(e -> onOpen.run());
        footer.add(status, BorderLayout.CENTER);
        footer.add(open, BorderLayout.EAST);

        card.add(heading, BorderLayout.NORTH);
        card.add(description, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        card.putClientProperty("catalog.wrappedLabels", new JLabel[]{title, description});
        UiTheme.styleTree(card);
        add(card);
    }

    private int availableWidth() {
        if (getParent() instanceof JViewport viewport && viewport.getExtentSize().width > 0) {
            return viewport.getExtentSize().width;
        }
        return getWidth() > 0 ? getWidth() : MIN_CARD_WIDTH + 24;
    }

    private void fitCards() {
        Insets insets = getInsets();
        int width = Math.max(1, availableWidth() - insets.left - insets.right);
        int columns = width >= MIN_CARD_WIDTH * 2 + GAP ? 2 : 1;
        ((GridLayout) getLayout()).setColumns(columns);
        int cardWidth = Math.max(1, (width - GAP * (columns - 1)) / columns);
        for (Component component : getComponents()) {
            JPanel card = (JPanel) component;
            Insets padding = card.getInsets();
            int textWidth = Math.max(1, cardWidth - padding.left - padding.right);
            for (JLabel label : (JLabel[]) card.getClientProperty("catalog.wrappedLabels")) {
                View view = (View) label.getClientProperty(BasicHTML.propertyKey);
                view.setSize(textWidth, 0);
                Dimension size = new Dimension(textWidth, (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)));
                if (!size.equals(label.getPreferredSize())) {
                    label.setPreferredSize(size);
                }
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        fitCards();
        Dimension size = super.getPreferredSize();
        return new Dimension(availableWidth(), size.height);
    }

    @Override
    public void doLayout() {
        fitCards();
        super.doLayout();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(760, 420);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(18, visible.height - 18);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

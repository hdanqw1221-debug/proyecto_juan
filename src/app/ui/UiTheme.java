package app.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

public final class UiTheme {
    public static final Color PRIMARY = new Color(177, 45, 55);
    public static final Color PRIMARY_DARK = new Color(111, 27, 36);
    public static final Color PRIMARY_SOFT = new Color(250, 235, 235);
    public static final Color INK = new Color(39, 39, 43);
    public static final Color MUTED = new Color(105, 105, 113);
    public static final Color BACKGROUND = new Color(247, 246, 243);
    public static final Color SURFACE = new Color(255, 255, 253);
    public static final Color BORDER = new Color(226, 222, 216);
    public static final Color NAV = new Color(82, 24, 32);
    public static final Color NAV_DARK = new Color(57, 16, 23);
    public static final Color CONTROL = new Color(250, 249, 247);
    public static final Color SUCCESS = new Color(35, 125, 91);
    public static final Color SUCCESS_SOFT = new Color(229, 244, 237);
    public static final Color WARNING = new Color(168, 105, 18);
    public static final Color WARNING_SOFT = new Color(255, 244, 220);
    public static final Color INFO = new Color(42, 103, 138);
    public static final Color INFO_SOFT = new Color(230, 241, 247);
    public static final Color ACCENT = new Color(205, 145, 63);

    private static final Font BASE_FONT = new Font("Segoe UI Variable", Font.PLAIN, 14);
    private static final Font TITLE_FONT = new Font("Bahnschrift", Font.BOLD, 14);
    private static final int FIELD_RADIUS = 12;
    private static final int BUTTON_RADIUS = 12;
    private static final int CARD_RADIUS = 20;

    private UiTheme() {
    }

    public static void install() {
        UIManager.put("control", BACKGROUND);
        UIManager.put("info", SURFACE);
        UIManager.put("nimbusBase", PRIMARY_DARK);
        UIManager.put("nimbusBlueGrey", new ColorUIResource(206, 211, 219));
        UIManager.put("nimbusFocus", new ColorUIResource(PRIMARY));
        UIManager.put("nimbusSelectionBackground", new ColorUIResource(PRIMARY));
        UIManager.put("textHighlight", new ColorUIResource(PRIMARY_SOFT));
        UIManager.put("ComboBox:\"ComboBox.arrowButton\".background", SURFACE);
        UIManager.put("ComboBox:\"ComboBox.arrowButton\".foreground", MUTED);
        UIManager.put("TabbedPane.contentAreaColor", BACKGROUND);
        UIManager.put("TabbedPane.tabAreaBackground", BACKGROUND);
        UIManager.put("Table.alternateRowColor", new ColorUIResource(new Color(250, 251, 252)));
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("Panel.background", BACKGROUND);

        UIManager.put("Label.font", BASE_FONT);
        UIManager.put("Button.font", BASE_FONT.deriveFont(Font.BOLD));
        UIManager.put("TextField.font", BASE_FONT);
        UIManager.put("PasswordField.font", BASE_FONT);
        UIManager.put("ComboBox.font", BASE_FONT);
        UIManager.put("CheckBox.font", BASE_FONT);
        UIManager.put("RadioButton.font", BASE_FONT);
        UIManager.put("TabbedPane.font", BASE_FONT.deriveFont(Font.BOLD));
        UIManager.put("Table.font", BASE_FONT);
        UIManager.put("TableHeader.font", BASE_FONT.deriveFont(Font.BOLD));
        UIManager.put("TextArea.font", new Font("Segoe UI", Font.PLAIN, 14));
    }

    public static JLabel title(String text, float size) {
        JLabel label = new JLabel(text);
        label.setForeground(INK);
        label.setFont(TITLE_FONT.deriveFont(Font.BOLD, size));
        return label;
    }

    public static JPanel card(LayoutManager layout) {
        JPanel panel = new RoundedPanel(layout, CARD_RADIUS);
        panel.putClientProperty("uiTheme.surface", Boolean.TRUE);
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        return panel;
    }

    public static void enableGlassEffect(JComponent host) {
        host.putClientProperty("uiTheme.glassHost", Boolean.TRUE);
        prepareGlassTree(host);
    }

    public static boolean isGlassEffect(Component component) {
        Component current = component;
        while (current != null) {
            if (current instanceof JComponent jc
                    && Boolean.TRUE.equals(jc.getClientProperty("uiTheme.glassHost"))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static void prepareGlassTree(Component component) {
        if (component instanceof JPanel panel && !(panel instanceof RoundedPanel)
                && sameRgb(panel.getBackground(), BACKGROUND)) {
            panel.setOpaque(false);
        }
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.setOpaque(false);
            Component view = scrollPane.getViewport().getView();
            if (view instanceof JPanel) {
                scrollPane.getViewport().setOpaque(false);
            }
        }
        if (component instanceof JTabbedPane tabs) {
            tabs.setOpaque(false);
        }
        if (component instanceof JSplitPane splitPane) {
            splitPane.setOpaque(false);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                prepareGlassTree(child);
            }
        }
    }

    private static boolean sameRgb(Color first, Color second) {
        return first != null && second != null
                && first.getRed() == second.getRed()
                && first.getGreen() == second.getGreen()
                && first.getBlue() == second.getBlue();
    }

    public static Border lineBorder() {
        return BorderFactory.createLineBorder(BORDER);
    }

    public static Border roundedBorder(Color color) {
        return new RoundedBorder(color, FIELD_RADIUS);
    }

    public static void styleTree(Container root) {
        styleContainer(root);
        for (Component child : root.getComponents()) {
            styleComponent(child);
            if (child instanceof Container container) {
                styleTree(container);
            }
        }
    }

    private static void styleContainer(Container container) {
        if (container instanceof JPanel panel) {
            if (Boolean.TRUE.equals(panel.getClientProperty("uiTheme.surface"))) {
                panel.setBackground(SURFACE);
            } else if (panel.getBackground() == null || panel.getBackground() instanceof UIResource) {
                panel.setBackground(BACKGROUND);
            }
        }
        if (container instanceof JViewport viewport) {
            viewport.setBackground(SURFACE);
        }
        if (container instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(new CompoundBorder(new RoundedBorder(BORDER, 18), new EmptyBorder(1, 1, 1, 1)));
            scrollPane.getViewport().setBackground(SURFACE);
            scrollPane.getVerticalScrollBar().setUI(new SoftScrollBarUI());
            scrollPane.getHorizontalScrollBar().setUI(new SoftScrollBarUI());
            scrollPane.getVerticalScrollBar().setUnitIncrement(18);
            scrollPane.getHorizontalScrollBar().setUnitIncrement(18);
        }
        if (container instanceof JTabbedPane tabs) {
            tabs.setUI(new ModernTabbedPaneUI());
            tabs.setBackground(SURFACE);
            tabs.setForeground(INK);
            tabs.setBorder(new CompoundBorder(new RoundedBorder(BORDER, 20), new EmptyBorder(12, 14, 14, 14)));
            tabs.setOpaque(false);
        }
        if (container instanceof JSplitPane splitPane) {
            splitPane.setBorder(null);
            splitPane.setBackground(BACKGROUND);
        }
    }

    private static void styleComponent(Component component) {
        if (component instanceof JLabel label) {
            if (label.getForeground() == null || label.getForeground() instanceof UIResource) {
                label.setForeground(INK);
            }
        } else if (component instanceof JButton button) {
            if (Boolean.TRUE.equals(button.getClientProperty("uiTheme.comboArrow"))) {
                styleComboArrow(button);
            } else if (SwingUtilities.getAncestorOfClass(JSpinner.class, button) != null) {
                styleSpinnerArrow(button);
            } else if ("secondary".equals(button.getClientProperty("uiTheme.buttonStyle"))) {
                styleSecondaryButton(button);
            } else if ("ghost".equals(button.getClientProperty("uiTheme.buttonStyle"))) {
                styleGhostButton(button);
            } else {
                styleButton(button);
            }
        } else if (component instanceof JTextField textField) {
            styleTextField(textField);
        } else if (component instanceof JPasswordField passwordField) {
            styleTextField(passwordField);
        } else if (component instanceof JTextArea textArea) {
            textArea.setBackground(SURFACE);
            textArea.setForeground(INK);
            textArea.setCaretColor(PRIMARY_DARK);
            textArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        } else if (component instanceof JTable table) {
            styleTable(table);
        } else if (component instanceof JComboBox<?> comboBox) {
            styleComboBox(comboBox);
        } else if (component instanceof JCheckBox checkBox) {
            checkBox.setBackground(BACKGROUND);
            checkBox.setForeground(INK);
        } else if (component instanceof JRadioButton radioButton) {
            radioButton.setBackground(SURFACE);
            radioButton.setForeground(INK);
        }
    }

    public static void styleButton(JButton button) {
        button.putClientProperty("uiTheme.buttonStyle", "primary");
        button.setUI(new RoundedButtonUI(true));
        ButtonPalette palette = paletteFor(button.getText());
        decorateButtonText(button, palette);
        button.setBackground(palette.background);
        button.setForeground(palette.foreground);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(11, 17, 11, 17));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installButtonHover(button, palette.background, palette.hover, true);
    }

    public static void styleSecondaryButton(JButton button) {
        button.putClientProperty("uiTheme.buttonStyle", "secondary");
        button.setUI(new RoundedButtonUI(false));
        button.setBackground(SURFACE);
        button.setForeground(PRIMARY_DARK);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 15, 9, 15));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installButtonHover(button, SURFACE, new Color(247, 248, 251), false);
    }

    public static void styleGhostButton(JButton button) {
        button.putClientProperty("uiTheme.buttonStyle", "ghost");
        button.setUI(new BasicButtonUI());
        button.setBackground(new Color(0, 0, 0, 0));
        button.setForeground(Color.WHITE);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 12, 10, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleSpinnerArrow(JButton button) {
        button.putClientProperty("uiTheme.buttonStyle", "spinner");
        button.setBackground(CONTROL);
        button.setForeground(MUTED);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(1, 5, 1, 5));
        button.setCursor(Cursor.getDefaultCursor());
    }

    private static void styleTextField(JTextField textField) {
        textField.setBackground(CONTROL);
        textField.setForeground(INK);
        textField.setCaretColor(PRIMARY_DARK);
        textField.setOpaque(false);
        textField.setBorder(new CompoundBorder(new RoundedBorder(BORDER, FIELD_RADIUS), new EmptyBorder(10, 12, 10, 12)));
    }

    private static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setUI(new ModernComboBoxUI());
        comboBox.setBackground(CONTROL);
        comboBox.setForeground(INK);
        comboBox.setOpaque(false);
        comboBox.setBorder(new CompoundBorder(new RoundedBorder(BORDER, FIELD_RADIUS), new EmptyBorder(3, 9, 3, 9)));
        comboBox.setFocusable(false);
    }

    private static void styleTable(JTable table) {
        table.setBackground(SURFACE);
        table.setForeground(INK);
        table.setSelectionBackground(PRIMARY_SOFT);
        table.setSelectionForeground(PRIMARY_DARK);
        table.setGridColor(new Color(232, 235, 240));
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setBorder(BorderFactory.createEmptyBorder());

        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer());
        header.setBackground(new Color(252, 252, 253));
        header.setForeground(Color.WHITE);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        header.setFont(BASE_FONT.deriveFont(Font.BOLD, 13f));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 42));

        table.setDefaultRenderer(Object.class, new ModernCellRenderer());
        table.setDefaultRenderer(Number.class, new ModernCellRenderer());
        table.setDefaultRenderer(Boolean.class, new BadgeCellRenderer());
        applyBadgeColumns(table);
    }

    private static void applyBadgeColumns(JTable table) {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            String name = table.getColumnName(i).toLowerCase(Locale.ROOT);
            if (name.contains("estado") || name.contains("activo") || name.contains("resultado")
                    || name.contains("correcta") || name.contains("habilitado") || name.contains("imagen")) {
                column.setCellRenderer(new BadgeCellRenderer());
            }
        }
    }

    private static void decorateButtonText(JButton button, ButtonPalette palette) {
        if (Boolean.TRUE.equals(button.getClientProperty("uiTheme.decoratedText"))) {
            return;
        }
        String text = button.getText();
        if (text == null || text.isBlank() || text.length() <= 2) {
            return;
        }
        button.setText(palette.prefix + text);
        button.putClientProperty("uiTheme.decoratedText", Boolean.TRUE);
    }

    private static ButtonPalette paletteFor(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (value.contains("deshabilitar") || value.contains("eliminar") || value.contains("quitar")
                || value.contains("sin fecha") || value.contains("salir")) {
            return new ButtonPalette("", new Color(170, 54, 58), new Color(139, 40, 46), Color.WHITE);
        }
        if (value.contains("habilitar") || value.contains("asignar")) {
            return new ButtonPalette("", SUCCESS, new Color(30, 116, 68), Color.WHITE);
        }
        if (value.contains("refrescar") || value.contains("actualizar") || value.contains("listar")
                || value.contains("cargar") || value.contains("ver")) {
            return new ButtonPalette("", SURFACE, new Color(247, 248, 251), PRIMARY_DARK);
        }
        if (value.contains("ingresar")) {
            return new ButtonPalette("", PRIMARY, PRIMARY_DARK, Color.WHITE);
        }
        if (value.contains("guardar") || value.contains("crear")
                || value.contains("responder") || value.contains("finalizar") || value.contains("cambiar")) {
            return new ButtonPalette("", PRIMARY, PRIMARY_DARK, Color.WHITE);
        }
        return new ButtonPalette("", PRIMARY, PRIMARY_DARK, Color.WHITE);
    }

    private static void styleComboArrow(JButton button) {
        button.setUI(new BasicButtonUI());
        button.setBackground(CONTROL);
        button.setForeground(MUTED);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(0, 8, 0, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void installButtonHover(JButton button, Color normal, Color hover, boolean filled) {
        for (MouseAdapter adapter : button.getListeners(MouseAdapter.class)) {
            button.removeMouseListener(adapter);
        }
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(normal);
            }
        });
    }

    private static final class ModernComboBoxUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton("v");
            button.putClientProperty("uiTheme.comboArrow", Boolean.TRUE);
            button.setUI(new BasicButtonUI());
            button.setBorder(new EmptyBorder(0, 8, 0, 8));
            button.setBackground(CONTROL);
            button.setForeground(MUTED);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);
            return button;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CONTROL);
            g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, FIELD_RADIUS, FIELD_RADIUS);
            g2.dispose();
        }
    }

    private static final class ModernTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(10, 18, 10, 18);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelected ? PRIMARY_SOFT : new Color(248, 250, 252));
            g2.fillRoundRect(x + 3, y + 5, w - 6, h - 10, 18, 18);
            if (isSelected) {
                g2.setColor(PRIMARY);
                g2.fillRoundRect(x + 16, y + h - 6, w - 32, 3, 3, 3);
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BORDER);
            int y = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) + 8;
            g2.drawLine(18, y, tabPane.getWidth() - 18, y);
            g2.dispose();
        }
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        private HeaderRenderer() {
            setOpaque(true);
            setBorder(new EmptyBorder(0, 12, 0, 12));
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(new Color(249, 247, 244));
            setForeground(PRIMARY_DARK);
            setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
            return this;
        }
    }

    private static final class ModernCellRenderer extends DefaultTableCellRenderer {
        private ModernCellRenderer() {
            setBorder(new EmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? SURFACE : new Color(250, 249, 247));
                setForeground(INK);
            } else {
                setBackground(PRIMARY_SOFT);
                setForeground(PRIMARY_DARK);
            }
            setBorder(new EmptyBorder(0, 12, 0, 12));
            return this;
        }
    }

    private static final class BadgeCellRenderer extends DefaultTableCellRenderer {
        private BadgeCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(new EmptyBorder(6, 10, 6, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "Sin dato" : String.valueOf(value);
            setText(text);
            ColorPair pair = badgeColors(text);
            setBackground(isSelected ? PRIMARY_SOFT : pair.background);
            setForeground(pair.foreground);
            setFont(BASE_FONT.deriveFont(Font.BOLD, 12f));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            return this;
        }

        private ColorPair badgeColors(String raw) {
            String text = raw.toLowerCase(Locale.ROOT);
            if (text.contains("true") || text.contains("activo") || text.contains("disponible")
                    || text.contains("correcta") || text.contains("resultado")) {
                return new ColorPair(SUCCESS_SOFT, SUCCESS);
            }
            if (text.contains("pendiente")) {
                return new ColorPair(WARNING_SOFT, WARNING);
            }
            if (text.contains("false") || text.contains("vencido") || text.contains("inactivo")
                    || text.contains("cerrado")) {
                return new ColorPair(PRIMARY_SOFT, PRIMARY_DARK);
            }
            return new ColorPair(INFO_SOFT, INFO);
        }
    }

    private record ButtonPalette(String prefix, Color background, Color hover, Color foreground) {
    }

    private record ColorPair(Color background, Color foreground) {
    }

    private static final class RoundedPanel extends JPanel {
        private final int radius;

        private RoundedPanel(LayoutManager layout, int radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean glass = isGlassEffect(this);
            g2.setColor(new Color(50, 35, 30, glass ? 18 : 10));
            g2.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 4, radius, radius);
            Color background = getBackground();
            g2.setColor(glass
                    ? new Color(background.getRed(), background.getGreen(), background.getBlue(), 218)
                    : background);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 3, radius, radius);
            if (glass) {
                g2.setPaint(new GradientPaint(
                        0, 0, new Color(255, 255, 255, 78),
                        0, Math.max(1, getHeight()), new Color(255, 255, 255, 8)
                ));
                g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 5, radius, radius);
            }
            g2.setColor(glass
                    ? new Color(BORDER.getRed(), BORDER.getGreen(), BORDER.getBlue(), 175)
                    : BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 3, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class SoftScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(188, 197, 210);
            trackColor = new Color(246, 248, 251);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x + 3, thumbBounds.y + 3,
                    thumbBounds.width - 6, thumbBounds.height - 6, 12, 12);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(trackColor);
            g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            g2.dispose();
        }

        private JButton invisibleButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    private static final class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        private RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.hasFocus() && c instanceof JTextField ? PRIMARY : color);
            if (c.hasFocus() && c instanceof JTextField) {
                g2.setStroke(new BasicStroke(2f));
            }
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }

    private static final class RoundedButtonUI extends BasicButtonUI {
        private final boolean primary;

        private RoundedButtonUI(boolean primary) {
            this.primary = primary;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton button = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(button.getBackground());
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, BUTTON_RADIUS, BUTTON_RADIUS);
            g2.setColor(primary ? button.getBackground() : BORDER);
            g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, BUTTON_RADIUS, BUTTON_RADIUS);
            if (button.hasFocus()) {
                g2.setColor(INFO);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(2, 2, c.getWidth() - 5, c.getHeight() - 5,
                        BUTTON_RADIUS - 2, BUTTON_RADIUS - 2);
            }
            g2.dispose();
            super.paint(g, c);
        }
    }
}

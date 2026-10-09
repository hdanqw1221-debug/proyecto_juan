package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.LinkedHashMap;
import java.util.Map;

public class PieChartPanel extends JPanel {
    private final Map<String, Double> data = new LinkedHashMap<>();
    private final Map<String, Color> colors = new LinkedHashMap<>();
    private final String title;

    public PieChartPanel(String title) {
        this.title = title;
        setOpaque(false);
        setPreferredSize(new Dimension(260, 220));
    }

    public void addSlice(String name, double value, Color color) {
        data.put(name, value);
        colors.put(name, color);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setFont(new Font("Bahnschrift", Font.BOLD, 17));
        g2.setColor(UiTheme.INK);
        g2.drawString(title, 4, 24);

        double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
        int chartSize = Math.max(92, Math.min(138, Math.min(getHeight() - 72, getWidth() / 2 - 18)));
        int chartX = 8;
        int chartY = 48;
        int strokeWidth = Math.max(14, chartSize / 7);

        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(233, 230, 225));
        g2.draw(new Arc2D.Double(chartX, chartY, chartSize, chartSize, 0, 360, Arc2D.OPEN));

        if (total > 0) {
            double currentAngle = 90;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                double angle = (entry.getValue() / total) * 360.0;
                g2.setColor(colors.get(entry.getKey()));
                g2.draw(new Arc2D.Double(chartX, chartY, chartSize, chartSize,
                        currentAngle, -angle, Arc2D.OPEN));
                currentAngle -= angle;
            }
        }

        String totalText = String.valueOf(Math.round(total));
        g2.setFont(new Font("Bahnschrift", Font.BOLD, 25));
        g2.setColor(UiTheme.INK);
        FontMetrics totalMetrics = g2.getFontMetrics();
        int centerX = chartX + chartSize / 2;
        int centerY = chartY + chartSize / 2;
        g2.drawString(totalText, centerX - totalMetrics.stringWidth(totalText) / 2,
                centerY + totalMetrics.getAscent() / 3);

        int legendX = chartX + chartSize + 24;
        int legendY = chartY + 12;
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            g2.setColor(colors.get(entry.getKey()));
            g2.fillRoundRect(legendX, legendY, 9, 9, 5, 5);
            g2.setColor(UiTheme.INK);
            g2.drawString(entry.getKey(), legendX + 16, legendY + 9);
            g2.setColor(UiTheme.MUTED);
            String value = String.valueOf(Math.round(entry.getValue()));
            g2.drawString(value, legendX + 16, legendY + 25);
            legendY += 44;
        }

        g2.dispose();
    }
}

import app.ui.UiTheme;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;

/**
 * Captura (C14) las tablas de notas y de evaluaciones del docente con los estilos de la aplicacion, sin abrir ventanas
 * ni conectarse a la base de datos. Se compila contra el codigo anterior (8518bc9) o el nuevo para comparar.
 *
 * Uso: java -cp <clases>;lib/* CapturaTablas antes|despues <carpeta_salida>
 * "antes": la vista tal como estaba (identificador del estudiante, fecha larga con nanosegundos).
 * "despues": nombre del estudiante y datos tal como los entrega ahora la aplicacion.
 * Los datos son de muestra (sinteticos); lo que se compara es la presentacion.
 */
public class CapturaTablas {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        boolean despues = args[0].equals("despues");
        File out = new File(args[1]);
        out.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        javax.swing.UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            UiTheme.install();
            try {
                ImageIO.write(render("Notas (vista del docente)", notas(despues)), "png", new File(out, "notas_" + args[0] + ".png"));
                ImageIO.write(render("Evaluaciones (vista del docente)", evaluaciones(despues)), "png",
                        new File(out, "evaluaciones_" + args[0] + ".png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("capturas escritas en " + out.getAbsolutePath());
    }

    private static DefaultTableModel notas(boolean despues) {
        DefaultTableModel m = new DefaultTableModel(new Object[]{"Intento", "Examen", "Estudiante", "Inicio", "Fin", "Puntaje"}, 0);
        String[] nombres = {"maria.rodriguez", "juan.perez", "ana.gomez", "luis.martinez", "sofia.castro"};
        for (int i = 0; i < nombres.length; i++) {
            LocalDateTime inicio = LocalDateTime.of(2026, 11, 18, 14, 5 + i, 33, 987_654_321 - i * 1000);
            m.addRow(new Object[]{
                    100L + i,
                    despues ? "Parcial 1 - Cavidad toracica" : (Object) 12L,
                    despues ? nombres[i] : (Object) (30L + i),
                    inicio, inicio.plusMinutes(34).plusSeconds(7), 3.0 + i * 0.4});
        }
        return m;
    }

    private static DefaultTableModel evaluaciones(boolean despues) {
        DefaultTableModel m = new DefaultTableModel(new Object[]{"ID", "Curso", "Titulo", "Activo", "Vence", "Intentos",
                "Duracion (min)", "Estado", "Creado"}, 0);
        m.addRow(new Object[]{12L, "Anatomia Veterinaria Equina I", "Parcial 1 - Cavidad toracica", true,
                LocalDateTime.of(2026, 12, 1, 23, 59, 0, 123_456_000), 2, 60, "Disponible",
                LocalDateTime.of(2026, 11, 10, 9, 15, 42, 555_000_000)});
        m.addRow(new Object[]{13L, "Anatomia Veterinaria Equina I", "Parcial 2 - Cavidad abdominal y region inguinal", false,
                LocalDateTime.of(2026, 12, 15, 18, 0, 0, 1), 1, 90, "Cerrada",
                LocalDateTime.of(2026, 11, 12, 16, 30, 5, 999_000_000)});
        return m;
    }

    private static BufferedImage render(String titulo, DefaultTableModel model) {
        JTable table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());  // sin ventana real el panel no lo instala solo
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new JLabel(titulo), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        UiTheme.styleTree(panel);
        int w = 1000, h = 70 + 40 * (model.getRowCount() + 1) + 20;
        panel.setPreferredSize(new Dimension(w, h));
        panel.setSize(w, h);
        layout(panel);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        panel.printAll(g);
        g.dispose();
        return img;
    }

    private static void layout(Component c) {
        c.doLayout();
        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                layout(child);
            }
        }
    }
}

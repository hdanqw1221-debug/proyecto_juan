package app.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** C14: las tablas ajustan sus columnas al contenido y muestran fechas cortas. */
class TableLayoutTest {
    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
        UiTheme.install();
    }

    private static JTable grades() {
        ReadOnlyTableModel model = new ReadOnlyTableModel(
                new Object[]{"Intento", "Examen", "Estudiante", "Inicio", "Fin", "Puntaje"}, 0);
        model.addRow(new Object[]{1L, "Parcial 1 - Cavidad toracica", "maria.rodriguez", LocalDateTime.of(2026, 11, 18, 14, 5, 33, 987_654_321),
                LocalDateTime.of(2026, 11, 18, 14, 40, 1, 5), 4.2});
        model.addRow(new Object[]{2L, "Parcial 1 - Cavidad toracica", "ana", LocalDateTime.of(2026, 11, 18, 15, 0), null, null});
        JTable table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);
        UiTheme.styleTree(scroll);
        return table;
    }

    private static int width(JTable t, String header) {
        for (int i = 0; i < t.getColumnCount(); i++) {
            TableColumn c = t.getColumnModel().getColumn(i);
            if (header.equals(c.getHeaderValue())) return c.getPreferredWidth();
        }
        throw new AssertionError(header);
    }

    @Test
    void lasFechasSeVenCortas() {
        JTable t = grades();
        Component cell = t.prepareRenderer(t.getCellRenderer(0, 3), 0, 3);
        assertEquals("18/11/2026 14:05", ((javax.swing.JLabel) cell).getText());
        Component score = t.prepareRenderer(t.getCellRenderer(0, 5), 0, 5);
        assertEquals("4.2", ((javax.swing.JLabel) score).getText());
    }

    @Test
    void losAnchosSeAjustanAlContenido() {
        JTable t = grades();
        assertTrue(width(t, "Intento") < width(t, "Examen"), "el id es mas angosto que el titulo del examen");
        assertTrue(width(t, "Examen") > 180, "el titulo largo cabe: " + width(t, "Examen"));
        assertTrue(width(t, "Inicio") >= 100, "la fecha corta cabe: " + width(t, "Inicio"));
        assertTrue(width(t, "Inicio") < 200, "la fecha ya no ocupa el ancho de una fecha larga: " + width(t, "Inicio"));
    }

    @Test
    void elAnchoTieneTopeYSeReajustaAlCambiarLosDatos() throws Exception {
        JTable t = grades();
        ReadOnlyTableModel model = (ReadOnlyTableModel) t.getModel();
        int before = width(t, "Estudiante");
        model.addRow(new Object[]{3L, "x".repeat(300), "estudiante.con.un.nombre.de.usuario.muy.largo.para.la.tabla", null, null, null});
        javax.swing.SwingUtilities.invokeAndWait(() -> { });  // el ajuste se programa con invokeLater
        assertTrue(width(t, "Estudiante") > before, "crece con el contenido");
        assertTrue(width(t, "Examen") <= 420, "con tope: " + width(t, "Examen"));
    }
}

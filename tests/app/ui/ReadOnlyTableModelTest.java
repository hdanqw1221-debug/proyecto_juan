package app.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** C03: ninguna tabla de la interfaz permite editar sus celdas (p. ej. el nombre de la evaluacion). */
class ReadOnlyTableModelTest {

    @Test
    void ningunaCeldaEsEditable() {
        ReadOnlyTableModel model = new ReadOnlyTableModel(new Object[]{"ID", "Materia", "Evaluacion", "Descripcion", "Cierre", "Estado"}, 0);
        model.addRow(new Object[]{7L, "Anatomia", "Parcial torax", "", "", "Disponible"});
        model.addRow(new Object[]{8L, "Anatomia", "Parcial abdomen", "", "", "Cerrada"});
        for (int r = 0; r < model.getRowCount(); r++) {
            for (int c = 0; c < model.getColumnCount(); c++) {
                assertFalse(model.isCellEditable(r, c), "celda " + r + "," + c);
            }
        }
        javax.swing.JTable table = new javax.swing.JTable(model);
        assertFalse(table.editCellAt(0, 2), "la tabla no abre el editor sobre el nombre de la evaluacion");
        assertEquals("Parcial torax", model.getValueAt(0, 2));
    }

    /** Revision automatica: ningun panel o dialogo crea un DefaultTableModel editable. */
    @Test
    void laInterfazNoCreaModelosEditables() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/app/ui"))) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("new DefaultTableModel(");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertEquals(List.of(), offenders);
        }
    }
}

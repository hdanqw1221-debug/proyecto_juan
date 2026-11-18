package app.ui;

import javax.swing.table.DefaultTableModel;

/**
 * Modelo de tabla de solo lectura (C03). Todas las tablas de la interfaz son consultas: los datos se cambian con
 * los formularios de cada panel, nunca escribiendo en la celda.
 */
public class ReadOnlyTableModel extends DefaultTableModel {
    public ReadOnlyTableModel(Object[] columnNames, int rowCount) {
        super(columnNames, rowCount);
    }

    @Override
    public final boolean isCellEditable(int row, int column) {
        return false;
    }
}

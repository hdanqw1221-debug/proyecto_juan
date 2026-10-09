with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

old_csv = """                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                    for (int i = 0; i < gradeModel.getColumnCount(); i++) {
                        writer.print(gradeModel.getColumnName(i) + (i == gradeModel.getColumnCount() - 1 ? "" : ","));
                    }
                    writer.println();
                    for (int i = 0; i < gradeModel.getRowCount(); i++) {
                        for (int j = 0; j < gradeModel.getColumnCount(); j++) {
                            Object val = gradeModel.getValueAt(i, j);
                            writer.print(val != null ? val.toString().replace(",", ";") : "");
                            writer.print(j == gradeModel.getColumnCount() - 1 ? "" : ",");
                        }
                        writer.println();
                    }
                    javax.swing.JOptionPane.showMessageDialog(this, "Archivo CSV exportado exitosamente.");
                }"""

new_csv = """                try (java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8);
                     java.io.PrintWriter writer = new java.io.PrintWriter(osw)) {
                    writer.write('\\ufeff'); // UTF-8 BOM para compatibilidad con Excel
                    for (int i = 0; i < gradeModel.getColumnCount(); i++) {
                        writer.print(escapeCsvCell(gradeModel.getColumnName(i)));
                        if (i < gradeModel.getColumnCount() - 1) writer.print(",");
                    }
                    writer.println();
                    for (int i = 0; i < gradeModel.getRowCount(); i++) {
                        for (int j = 0; j < gradeModel.getColumnCount(); j++) {
                            Object val = gradeModel.getValueAt(i, j);
                            writer.print(escapeCsvCell(val != null ? val.toString() : ""));
                            if (j < gradeModel.getColumnCount() - 1) writer.print(",");
                        }
                        writer.println();
                    }
                    javax.swing.JOptionPane.showMessageDialog(this, "Archivo CSV exportado exitosamente con codificacion UTF-8.");
                }"""

assert old_csv in text, 'old_csv not found'
text = text.replace(old_csv, new_csv, 1)

# Add escapeCsvCell helper
old_helpers_end = '    private long tableLongValue(JTable table, int viewRow, int column) {'
new_helpers = """    private String escapeCsvCell(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\\\"") || value.contains("\\n") || value.contains("\\r")) {
            return "\\\"" + value.replace("\\\"", "\\\"\\\"") + "\\\"";
        }
        return value;
    }

    private long tableLongValue(JTable table, int viewRow, int column) {"""

assert old_helpers_end in text, 'old_helpers_end not found'
text = text.replace(old_helpers_end, new_helpers, 1)

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('TeacherPanel CSV export updated successfully!')

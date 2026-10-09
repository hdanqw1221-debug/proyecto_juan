package app.service;

import app.model.RoleType;
import app.model.User;
import app.security.InputValidator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserImportService {
    private final UserService userService;

    public static class ImportResult {
        private int totalProcessed;
        private int successCount;
        private final List<String> errors = new ArrayList<>();
        private final List<User> createdUsers = new ArrayList<>();

        public int getTotalProcessed() {
            return totalProcessed;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailedCount() {
            return errors.size();
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public List<User> getCreatedUsers() {
            return Collections.unmodifiableList(createdUsers);
        }

        public void addSuccess(User user) {
            totalProcessed++;
            successCount++;
            createdUsers.add(user);
        }

        public void addError(String error) {
            totalProcessed++;
            errors.add(error);
        }
    }

    public UserImportService(UserService userService) {
        this.userService = userService;
    }

    public ImportResult importFromCsv(File csvFile) throws IOException {
        try (InputStream in = new FileInputStream(csvFile)) {
            return importFromStream(in);
        }
    }

    public ImportResult importFromStream(InputStream in) throws IOException {
        ImportResult result = new ImportResult();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            char delimiter = ',';
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Eliminar BOM de UTF-8 si esta presente
                if (firstLine && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Detectar delimitador en la primera linea util (; o ,)
                if (firstLine) {
                    if (trimmed.contains(";") && (!trimmed.contains(",") || countMatches(trimmed, ';') >= countMatches(trimmed, ','))) {
                        delimiter = ';';
                    }
                    firstLine = false;

                    // Si la linea es cabecera, omitirla
                    String lower = trimmed.toLowerCase();
                    if (lower.contains("usuario") || lower.contains("username") || lower.contains("email") || lower.contains("correo")) {
                        continue;
                    }
                }

                List<String> tokens = parseCsvLine(trimmed, delimiter);
                if (tokens.isEmpty()) {
                    continue;
                }

                if (tokens.size() < 3) {
                    result.addError("Linea " + lineNumber + ": Se requieren al menos 3 columnas (usuario, email, contrasena).");
                    continue;
                }

                String username = tokens.get(0).trim();
                String email = tokens.get(1).trim();
                String password = tokens.get(2).trim();
                String roleStr = tokens.size() > 3 ? tokens.get(3).trim() : "ESTUDIANTE";
                String activeStr = tokens.size() > 4 ? tokens.get(4).trim() : "true";

                if (username.isEmpty()) {
                    result.addError("Linea " + lineNumber + ": El nombre de usuario no puede estar vacio.");
                    continue;
                }

                if (email.isEmpty() || !email.contains("@")) {
                    result.addError("Linea " + lineNumber + ": El correo electronico '" + email + "' es invalido.");
                    continue;
                }

                if (!InputValidator.isStrongPassword(password)) {
                    result.addError("Linea " + lineNumber + " (" + username + "): La contrasena es debil (minimo 6 caracteres, 1 mayuscula y 1 numero).");
                    continue;
                }

                RoleType role = RoleType.ESTUDIANTE;
                if (!roleStr.isEmpty()) {
                    try {
                        role = RoleType.valueOf(roleStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        result.addError("Linea " + lineNumber + " (" + username + "): Rol desconocido '" + roleStr + "'. Opciones: ESTUDIANTE, PROFESOR, ADMINISTRADOR.");
                        continue;
                    }
                }

                boolean active = true;
                if (!activeStr.isEmpty()) {
                    String a = activeStr.toLowerCase();
                    active = a.equals("true") || a.equals("1") || a.equals("si") || a.equals("s") || a.equals("yes") || a.equals("verdadero");
                }

                // Verificar si el usuario ya existe
                try {
                    User existing = userService.findByUsername(username);
                    if (existing != null) {
                        result.addError("Linea " + lineNumber + ": El usuario '" + username + "' ya existe en el sistema.");
                        continue;
                    }

                    User created = userService.createUser(username, email, password, active);
                    userService.assignRoles(created.getId(), List.of(role));
                    result.addSuccess(created);
                } catch (Exception ex) {
                    result.addError("Linea " + lineNumber + " (" + username + "): Error al registrar: " + ex.getMessage());
                }
            }
        }

        return result;
    }

    public static String getTemplateContent() {
        return "usuario,email,password,rol,activo\n" +
                "estudiante1,estudiante1@unipamplona.edu.co,ClaveSegura1,ESTUDIANTE,true\n" +
                "profesor1,profesor1@unipamplona.edu.co,DocenteSeguro2,PROFESOR,true\n";
    }

    public static void generateTemplateFile(File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
            writer.write(getTemplateContent());
        }
    }

    private static List<String> parseCsvLine(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private static int countMatches(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }
}

package app.service;

import app.model.RoleType;
import app.session.SessionManager;
import app.security.ServiceGuard;
import app.config.AppConfig;
import app.db.EmbeddedDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DatabaseBackupService {
    private final AppConfig config;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    public static class BackupResult {
        private final boolean success;
        private final String message;
        private final File file;
        private final long fileSizeBytes;
        private final long durationMs;

        public BackupResult(boolean success, String message, File file, long fileSizeBytes, long durationMs) {
            this.success = success;
            this.message = message;
            this.file = file;
            this.fileSizeBytes = fileSizeBytes;
            this.durationMs = durationMs;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public File getFile() {
            return file;
        }

        public long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public String getFormattedSize() {
            if (fileSizeBytes < 1024) return fileSizeBytes + " B";
            if (fileSizeBytes < 1024 * 1024) return String.format("%.2f KB", fileSizeBytes / 1024.0);
            return String.format("%.2f MB", fileSizeBytes / (1024.0 * 1024.0));
        }
    }

    public static class RestoreResult {
        private final boolean success;
        private final String message;
        private final long durationMs;

        public RestoreResult(boolean success, String message, long durationMs) {
            this.success = success;
            this.message = message;
            this.durationMs = durationMs;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    private final ServiceGuard guard;

    /** Sin sesion: crear y restaurar respaldos se deniega. */
    public DatabaseBackupService(AppConfig config) {
        this(config, null);
    }

    public DatabaseBackupService(AppConfig config, SessionManager sessionManager) {
        this.guard = ServiceGuard.of(sessionManager);
        this.config = config;
        this.dbUser = config.getDbUser() != null ? config.getDbUser() : "postgres";
        this.dbPassword = config.getDbPassword() != null ? config.getDbPassword() : "";

        String host = "localhost";
        int port = 5432;
        String name = "gestion";

        String url = config.getDbUrl();
        if (url != null && url.startsWith("jdbc:postgresql://")) {
            String clean = url.substring("jdbc:postgresql://".length());
            int slashIndex = clean.indexOf('/');
            if (slashIndex > 0) {
                String hostPort = clean.substring(0, slashIndex);
                String dbAndQuery = clean.substring(slashIndex + 1);
                int qIdx = dbAndQuery.indexOf('?');
                if (qIdx >= 0) {
                    name = dbAndQuery.substring(0, qIdx);
                } else {
                    name = dbAndQuery;
                }

                int colonIdx = hostPort.indexOf(':');
                if (colonIdx > 0) {
                    host = hostPort.substring(0, colonIdx);
                    try {
                        port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    host = hostPort;
                }
            }
        }

        this.dbHost = host;
        this.dbPort = port;
        this.dbName = name;
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public boolean isPgDumpAvailable() {
        return findExecutable("pg_dump") != null;
    }

    public String getPgDumpPath() {
        return findExecutable("pg_dump");
    }

    public BackupResult createBackup(File targetFile) throws IOException, InterruptedException {
        guard.require(RoleType.ADMINISTRADOR);
        String pgDumpExe = findExecutable("pg_dump");
        if (pgDumpExe == null) {
            throw new IllegalStateException("No se encontro el utilitario 'pg_dump' de PostgreSQL. Verifica que este instalado en el equipo.");
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        long startTime = System.currentTimeMillis();
        boolean isPlainSql = targetFile.getName().toLowerCase().endsWith(".sql");

        List<String> cmd = new ArrayList<>();
        cmd.add(pgDumpExe);
        cmd.add("-h");
        cmd.add(dbHost);
        cmd.add("-p");
        cmd.add(String.valueOf(dbPort));
        cmd.add("-U");
        cmd.add(dbUser);

        if (isPlainSql) {
            cmd.add("--clean");
            cmd.add("--if-exists");
            cmd.add("--format=plain");
        } else {
            cmd.add("-F");
            cmd.add("c");
            cmd.add("-b");
            cmd.add("-v");
        }

        cmd.add("-f");
        cmd.add(targetFile.getAbsolutePath());
        cmd.add(dbName);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (dbPassword != null && !dbPassword.isEmpty()) {
            pb.environment().put("PGPASSWORD", dbPassword);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        long duration = System.currentTimeMillis() - startTime;

        if (exitCode == 0 && targetFile.exists() && targetFile.length() > 0) {
            return new BackupResult(true, "Copia de seguridad creada correctamente.", targetFile, targetFile.length(), duration);
        } else {
            String errMsg = output.toString().trim();
            if (errMsg.isEmpty()) errMsg = "Codigo de salida no exitoso: " + exitCode;
            throw new RuntimeException("Error al ejecutar pg_dump:\n" + errMsg);
        }
    }

    public RestoreResult restoreBackup(File sourceFile) throws IOException, InterruptedException {
        guard.require(RoleType.ADMINISTRADOR);
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("El archivo de respaldo no existe: " + sourceFile.getAbsolutePath());
        }

        boolean isPlainSql = sourceFile.getName().toLowerCase().endsWith(".sql");
        String exeName = isPlainSql ? "psql" : "pg_restore";
        String exePath = findExecutable(exeName);

        if (exePath == null) {
            throw new IllegalStateException("No se encontro el utilitario '" + exeName + "' de PostgreSQL para restaurar.");
        }

        long startTime = System.currentTimeMillis();
        List<String> cmd = new ArrayList<>();
        cmd.add(exePath);
        cmd.add("-h");
        cmd.add(dbHost);
        cmd.add("-p");
        cmd.add(String.valueOf(dbPort));
        cmd.add("-U");
        cmd.add(dbUser);
        cmd.add("-d");
        cmd.add(dbName);

        if (!isPlainSql) {
            cmd.add("--clean");
            cmd.add("--if-exists");
            cmd.add("-v");
            cmd.add(sourceFile.getAbsolutePath());
        } else {
            cmd.add("-f");
            cmd.add(sourceFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (dbPassword != null && !dbPassword.isEmpty()) {
            pb.environment().put("PGPASSWORD", dbPassword);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        long duration = System.currentTimeMillis() - startTime;

        // Nota: pg_restore puede retornar codigo 1 por advertencias de objetos no existentes al limpiar
        if (exitCode == 0 || exitCode == 1) {
            return new RestoreResult(true, "Base de datos restaurada correctamente.", duration);
        } else {
            String errMsg = output.toString().trim();
            if (errMsg.isEmpty()) errMsg = "Codigo de salida no exitoso: " + exitCode;
            throw new RuntimeException("Error al restaurar la base de datos:\n" + errMsg);
        }
    }

    public String findExecutable(String name) {
        String exeName = name.endsWith(".exe") ? name : name + ".exe";

        // 0. Con la base embebida, los utilitarios de su misma version incluidos en el paquete
        String bundled = findBundledExecutable(exeName);
        if (config.isEmbeddedDatabase() && bundled != null) {
            return bundled;
        }

        // 1. Variable de entorno especifica si existe (ej. PG_BIN o PG_DUMP_PATH)
        String envPath = System.getenv("PG_BIN");
        if (envPath != null && !envPath.isBlank()) {
            File f = new File(envPath, exeName);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        // 2. Comprobar si esta en PATH directamente
        try {
            ProcessBuilder test = new ProcessBuilder(name, "--version");
            Process p = test.start();
            if (p.waitFor() == 0) {
                return name;
            }
        } catch (Exception ignored) {
        }

        // 3. Buscar en directorios estandar de instalacion de PostgreSQL en Windows
        String[] baseDirs = new String[]{
                "C:\\Program Files\\PostgreSQL",
                "C:\\Program Files (x86)\\PostgreSQL"
        };

        for (String base : baseDirs) {
            File baseDir = new File(base);
            if (baseDir.isDirectory()) {
                File[] versions = baseDir.listFiles(File::isDirectory);
                if (versions != null) {
                    // Ordenar descendente (versiones mas nuevas primero: 18, 17, 16...)
                    Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
                    for (File versionDir : versions) {
                        File binExe = new File(new File(versionDir, "bin"), exeName);
                        if (binExe.isFile()) {
                            return binExe.getAbsolutePath();
                        }
                    }
                }
            }
        }

        // 4. Los incluidos en el paquete, aunque se use un servidor externo
        return bundled;
    }

    private static String findBundledExecutable(String exeName) {
        Path bin = EmbeddedDatabase.findBinDir();
        if (bin != null && Files.isRegularFile(bin.resolve(exeName))) {
            return bin.resolve(exeName).toString();
        }
        return null;
    }
}

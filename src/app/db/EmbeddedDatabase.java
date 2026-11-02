package app.db;

import app.config.AppConfig;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * PostgreSQL incluido en el paquete (app/pgsql): con app.db.embedded=true el equipo no necesita
 * tener PostgreSQL instalado.
 *
 * La primera vez crea el cluster en %LOCALAPPDATA%\GestionAcademica\basedatos con una clave aleatoria
 * y la base "gestion" con su esquema (carpeta db/). Despues solo lo arranca, escuchando unicamente en
 * 127.0.0.1, en el puerto configurado o en el siguiente libre, y lo detiene al cerrar la aplicacion
 * si fue ella quien lo arranco.
 */
public final class EmbeddedDatabase {
    public static final String DATABASE = "gestion";
    public static final String SUPERUSER = "postgres";
    public static final String HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 15432;

    /** Scripts del esquema, en orden; test_users.sql se anade si app.db.embedded.demoData=true. */
    static final List<String> SCHEMA_SCRIPTS = List.of("schema.sql", "cavidades_schema.sql", "cavidades_fichas.sql");
    static final String DEMO_SCRIPT = "test_users.sql";

    private static final String[] BIN_DIRS = {"app/pgsql/bin", "pgsql/bin", "build/pgsql-runtime/bin"};
    private static final String[] SQL_DIRS = {"app/db", "db"};
    private static final String PREPARING_DATABASE = DATABASE + "_preparando";
    private static final int START_TIMEOUT_S = 90;
    private static final int LOG_LINES = 15;

    private final Path bin;
    private final Path home;
    private final Path data;
    private final Path sqlDir;
    private final int preferredPort;
    private final boolean demoData;

    private volatile int port;
    private FileChannel instanceChannel;
    private Path instanceFile;

    EmbeddedDatabase(Path bin, Path home, Path sqlDir, int preferredPort, boolean demoData) {
        // Absolutas: los programas de PostgreSQL se ejecutan con la carpeta de datos como directorio de trabajo.
        this.bin = bin.toAbsolutePath();
        this.home = home.toAbsolutePath();
        this.data = this.home.resolve("datos");
        this.sqlDir = sqlDir.toAbsolutePath();
        this.preferredPort = preferredPort;
        this.demoData = demoData;
    }

    public static EmbeddedDatabase fromConfig(AppConfig config) {
        Path bin = findBinDir();
        if (bin == null) {
            throw new IllegalStateException("No se encontro el PostgreSQL incluido en la aplicacion (carpeta app/pgsql). "
                    + "Reinstala la aplicacion o desactiva app.db.embedded en config/app.properties.");
        }
        Path sql = findSqlDir();
        if (sql == null) {
            throw new IllegalStateException("No se encontraron los scripts de la base de datos (carpeta app/db).");
        }
        return new EmbeddedDatabase(bin, config.getEmbeddedHome(), sql, config.getEmbeddedPort(), config.isEmbeddedDemoData());
    }

    /** Carpeta bin del PostgreSQL del paquete (o del preparado en build/ al desarrollar), o null. */
    public static Path findBinDir() {
        for (Path root : roots()) {
            for (String candidate : BIN_DIRS) {
                Path dir = root.resolve(candidate).normalize();
                if (Files.isRegularFile(dir.resolve("pg_ctl.exe")) || Files.isRegularFile(dir.resolve("pg_ctl"))) {
                    return dir;
                }
            }
        }
        return null;
    }

    private static Path findSqlDir() {
        for (Path root : roots()) {
            for (String candidate : SQL_DIRS) {
                Path dir = root.resolve(candidate).normalize();
                if (Files.isRegularFile(dir.resolve(SCHEMA_SCRIPTS.get(0)))) {
                    return dir;
                }
            }
        }
        return null;
    }

    private static List<Path> roots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(System.getProperty("user.dir")));
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher != null && !launcher.isBlank() && Path.of(launcher).getParent() != null) {
            roots.add(Path.of(launcher).getParent());
        }
        return roots;
    }

    // ------------------------------------------------------------------ ciclo de vida
    /** El cluster ya existe (la primera vez el arranque tarda mas porque hay que crearlo). */
    public boolean isInitialized() {
        return Files.isRegularFile(data.resolve("PG_VERSION"));
    }

    /**
     * Crea el cluster y la base si faltan, arranca el servidor (o reutiliza el que ya este en marcha)
     * y devuelve la configuracion con la conexion a esta base.
     */
    public synchronized AppConfig start(AppConfig config) throws IOException, InterruptedException, SQLException {
        Files.createDirectories(home);
        registerInstance();
        if (!isInitialized()) {
            initCluster();
        }
        String password = readPassword();

        Integer running = runningPort();
        if (running != null) {
            port = running;
        } else {
            port = choosePort(preferredPort);
            startServer();
        }
        ensureDatabase(password);
        return config.withDatabase(jdbcUrl(DATABASE), SUPERUSER, password);
    }

    /**
     * Se llama al salir. Detiene el servidor si ninguna otra instancia de la aplicacion lo esta usando,
     * aunque lo haya arrancado otra (por ejemplo, una que se cerro de forma forzada).
     */
    public synchronized void stop() {
        if (instanceChannel == null) {
            return;
        }
        boolean othersRunning = otherInstancesRunning();
        releaseInstance();
        if (othersRunning || !Files.isRegularFile(data.resolve("postmaster.pid"))) {
            return;
        }
        try {
            run(List.of(exe("pg_ctl"), "stop", "-D", data.toString(), "-m", "fast", "-w", "-t", "30"), 40);
        } catch (Exception ex) {
            System.err.println("Aviso al detener PostgreSQL embebido: " + ex.getMessage());
        }
    }

    public int getPort() {
        return port;
    }

    public Path getHome() {
        return home;
    }

    public Path getLogFile() {
        return home.resolve("postgresql.log");
    }

    // ------------------------------------------------------------------ instancias
    /**
     * Cada instancia de la aplicacion mantiene bloqueado su archivo en instancias/ mientras se ejecuta.
     * El sistema libera el bloqueo aunque el proceso termine de forma forzada.
     */
    private void registerInstance() throws IOException {
        if (instanceChannel != null) {
            return;
        }
        Path dir = Files.createDirectories(home.resolve("instancias"));
        instanceFile = dir.resolve(ProcessHandle.current().pid() + "-" + System.nanoTime() + ".lock");
        instanceChannel = FileChannel.open(instanceFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        instanceChannel.lock();
    }

    /** Package-private para las pruebas: simula el cierre forzado de esta instancia. */
    synchronized void releaseInstance() {
        try {
            instanceChannel.close();
            Files.deleteIfExists(instanceFile);
        } catch (IOException ignored) {
        }
        instanceChannel = null;
    }

    private boolean otherInstancesRunning() {
        try (Stream<Path> files = Files.list(instanceFile.getParent())) {
            boolean running = false;
            for (Path other : files.filter(f -> !f.equals(instanceFile)).toList()) {
                running |= isLocked(other);
            }
            return running;
        } catch (IOException ex) {
            return false;
        }
    }

    /** El archivo lo bloquea otra instancia viva; si no, es de una que termino y se borra. */
    private static boolean isLocked(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return true;
            }
            lock.release();
        } catch (OverlappingFileLockException | IOException ex) {
            return true;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------ cluster
    private void initCluster() throws IOException, InterruptedException {
        Path pending = home.resolve("datos.nuevo");
        deleteRecursively(pending);
        String password = randomPassword();
        Path pwfile = home.resolve("clave.tmp");
        Files.writeString(pwfile, password + System.lineSeparator(), StandardCharsets.US_ASCII);
        try {
            Result result = run(List.of(exe("initdb"), "-D", pending.toString(), "-U", SUPERUSER,
                    "-A", "scram-sha-256", "--pwfile=" + pwfile, "-E", "UTF8",
                    "--locale-provider=icu", "--icu-locale=es"), 180);
            if (result.exitCode() != 0) {
                deleteRecursively(pending);
                throw new IOException("initdb no pudo crear la base de datos:\n" + result.output());
            }
        } finally {
            Files.deleteIfExists(pwfile);
        }
        Files.writeString(passwordFile(), password, StandardCharsets.US_ASCII);
        Files.move(pending, data, StandardCopyOption.ATOMIC_MOVE);
    }

    private String readPassword() throws IOException {
        Path file = passwordFile();
        if (!Files.isRegularFile(file)) {
            throw new IOException("Falta la clave de la base de datos embebida (" + file + "). "
                    + "Si no hay datos que conservar, borra la carpeta " + home + " para crearla de nuevo.");
        }
        return Files.readString(file, StandardCharsets.US_ASCII).trim();
    }

    private Path passwordFile() {
        return home.resolve("superusuario.clave");
    }

    /** Puerto del servidor de este cluster si ya esta en marcha (por ejemplo, tras un cierre forzado). */
    private Integer runningPort() throws IOException, InterruptedException {
        Path pidFile = data.resolve("postmaster.pid");
        if (!Files.isRegularFile(pidFile)) {
            return null;
        }
        if (run(List.of(exe("pg_ctl"), "status", "-D", data.toString()), 30).exitCode() != 0) {
            return null;  // postmaster.pid de un cierre forzado: postgres lo descarta al arrancar
        }
        return readPidFilePort(Files.readAllLines(pidFile));
    }

    /** La cuarta linea de postmaster.pid es el puerto. */
    static Integer readPidFilePort(List<String> lines) {
        if (lines.size() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(lines.get(3).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void startServer() throws IOException, InterruptedException {
        Path log = getLogFile();
        // postgres hereda los handles de pg_ctl: su salida va a un archivo, nunca a una tuberia que haya que leer.
        Path ctlLog = home.resolve("pg_ctl.log");
        ProcessBuilder builder = processBuilder(List.of(exe("pg_ctl"), "start", "-D", data.toString(),
                "-l", log.toString(), "-w", "-t", String.valueOf(START_TIMEOUT_S),
                "-o", "-p " + port + " -h " + HOST))
                .redirectErrorStream(true)
                .redirectOutput(ctlLog.toFile());
        Process process = builder.start();
        process.getOutputStream().close();
        if (!process.waitFor(START_TIMEOUT_S + 15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("PostgreSQL no arranco en " + START_TIMEOUT_S + " s. Registro: " + log);
        }
        if (process.exitValue() != 0) {
            throw new IOException("PostgreSQL no pudo arrancar (puerto " + port + ").\n"
                    + tail(ctlLog) + "\n" + tail(log) + "\nRegistro completo: " + log);
        }
    }

    /** El puerto preferido o el siguiente libre en 127.0.0.1. */
    static int choosePort(int preferred) throws IOException {
        for (int candidate = preferred; candidate < preferred + 100 && candidate <= 65535; candidate++) {
            if (isFree(candidate)) {
                return candidate;
            }
        }
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(HOST))) {
            return socket.getLocalPort();
        }
    }

    private static boolean isFree(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName(HOST))) {
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ base y esquema
    /**
     * Crea la base "gestion" con su esquema si no existe. Se prepara con otro nombre y se renombra al
     * terminar, asi un cierre a mitad de la carga no deja una base incompleta.
     */
    private void ensureDatabase(String password) throws SQLException, IOException {
        try (Connection admin = DriverManager.getConnection(jdbcUrl("postgres"), SUPERUSER, password);
             Statement st = admin.createStatement()) {
            if (databaseExists(admin, DATABASE)) {
                return;
            }
            st.execute("DROP DATABASE IF EXISTS " + PREPARING_DATABASE);
            st.execute("CREATE DATABASE " + PREPARING_DATABASE + " ENCODING 'UTF8' TEMPLATE template0");
            try (Connection db = DriverManager.getConnection(jdbcUrl(PREPARING_DATABASE), SUPERUSER, password)) {
                for (Path script : schemaScripts()) {
                    runScript(db, script);
                }
            }
            st.execute("ALTER DATABASE " + PREPARING_DATABASE + " RENAME TO " + DATABASE);
        }
    }

    private static boolean databaseExists(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    List<Path> schemaScripts() throws IOException {
        List<String> names = new ArrayList<>(SCHEMA_SCRIPTS);
        if (demoData) {
            names.add(DEMO_SCRIPT);
        }
        List<Path> scripts = new ArrayList<>();
        for (String name : names) {
            Path script = sqlDir.resolve(name);
            if (!Files.isRegularFile(script)) {
                throw new IOException("Falta el script de la base de datos: " + script);
            }
            scripts.add(script);
        }
        return scripts;
    }

    private static void runScript(Connection conn, Path script) throws SQLException, IOException {
        try (Statement st = conn.createStatement()) {
            st.execute(Files.readString(script, StandardCharsets.UTF_8));
        } catch (SQLException ex) {
            throw new SQLException("Error al ejecutar " + script.getFileName() + ": " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://" + HOST + ":" + port + "/" + database;
    }

    // ------------------------------------------------------------------ procesos
    private record Result(int exitCode, String output) {
    }

    private String exe(String name) {
        Path windows = bin.resolve(name + ".exe");
        return (Files.isRegularFile(windows) ? windows : bin.resolve(name)).toString();
    }

    private ProcessBuilder processBuilder(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(home.toFile());
        // Que ninguna variable de un PostgreSQL instalado en el equipo cambie el cluster o el puerto.
        for (String var : List.of("PGDATA", "PGPORT", "PGHOST", "PGUSER", "PGDATABASE", "PGOPTIONS")) {
            builder.environment().remove(var);
        }
        return builder;
    }

    /** Ejecuta un programa sin procesos hijos persistentes (initdb, pg_ctl status/stop) y recoge su salida. */
    private Result run(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        Path output = Files.createTempFile(home, "pg", ".log");
        try {
            Process process = processBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            process.getOutputStream().close();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(new File(command.get(0)).getName() + " no termino en " + timeoutSeconds + " s.");
            }
            return new Result(process.exitValue(), Files.readString(output, consoleCharset()).trim());
        } finally {
            Files.deleteIfExists(output);
        }
    }

    /** Los programas de PostgreSQL escriben en la pagina de codigos del sistema (por ejemplo, windows-1252). */
    private static Charset consoleCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "UTF-8"));
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String tail(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, consoleCharset());
            return String.join("\n", lines.subList(Math.max(0, lines.size() - LOG_LINES), lines.size())).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    private static String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}

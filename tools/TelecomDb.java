import app.config.AppConfig;
import app.db.ConnectionFactory;
import app.model.RoleType;
import app.model.User;
import app.security.BCryptPasswordHasher;
import app.service.AuthService;
import app.service.ExamAttemptService;
import app.service.SimulationService;
import app.service.UserService;
import app.session.SessionManager;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pruebas de telecomunicaciones T02 (latencia de la base de datos) y T03 (concurrencia cliente-servidor).
 *
 * Usa SIEMPRE la base de pruebas (nunca "gestion"): crea un docente, un estudiante, una materia, una practica y una
 * evaluacion temporales y los borra al terminar. Las operaciones son las reales de la aplicacion (JDBC sin pool,
 * BCrypt coste 12, transaccion de envio de la evaluacion).
 *
 * Uso: java -cp ... TelecomDb t02|t03 <etiqueta> <url_jdbc> <carpeta_salida> [iteraciones|rondas] [clientes,...] [sslmode]
 */
public class TelecomDb {
    static String url;
    static String user = System.getenv().getOrDefault("APP_TEST_DB_USER", "postgres");
    static String password;
    static Properties connProps = new Properties();

    public static void main(String[] a) throws Exception {
        String prueba = a[0];
        String etiqueta = a[1];
        url = a[2];
        Path salida = Path.of(a[3]);
        Files.createDirectories(salida);
        int n = a.length > 4 ? Integer.parseInt(a[4]) : 1000;
        int[] clientes = a.length > 5 ? Arrays.stream(a[5].split(",")).mapToInt(Integer::parseInt).toArray() : new int[]{1, 5, 10, 30};
        password = System.getenv("APP_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            Properties p = new Properties();
            try (var in = Files.newInputStream(Path.of("config/app.properties"))) {
                p.load(in);
            }
            password = p.getProperty("app.db.password", "");
        }
        if (!url.contains("gestion_pruebas")) {
            throw new IllegalStateException("Solo se mide sobre la base de pruebas (gestion_pruebas)");
        }
        connProps.setProperty("user", user);
        connProps.setProperty("password", password);

        Fixture fx = Fixture.create();
        try {
            if (prueba.equals("t02")) {
                t02(fx, etiqueta, salida, n);
            } else {
                t03(fx, etiqueta, salida, n, clientes);
            }
        } finally {
            fx.drop();
        }
    }

    // ------------------------------------------------------------------ datos temporales
    record Fixture(long teacher, long student, long course, long exam, long simulation, String studentName) {
        static Fixture create() throws Exception {
            try (Connection c = DriverManager.getConnection(url, connProps)) {
                String tag = Long.toHexString(System.nanoTime());
                String hash = new BCryptPasswordHasher().hash("ClaveTelecom123");
                long t = insertUser(c, "tel_prof_" + tag, hash);
                String studentName = "tel_est_" + tag;
                long s = insertUser(c, studentName, hash);
                long course = one(c, "INSERT INTO cursos (nombre, profesor_id) VALUES ('Materia telecom', " + t + ") RETURNING id");
                long exam = one(c, "INSERT INTO examenes (curso_id, titulo) VALUES (" + course + ", 'Evaluacion telecom') RETURNING id");
                long sim = one(c, "INSERT INTO simulaciones (curso_id, nombre, configuracion) VALUES (" + course
                        + ", 'Practica telecom', '" + SimulationService.defaultConfigJson().replace("'", "''") + "'::jsonb) RETURNING id");
                return new Fixture(t, s, course, exam, sim, studentName);
            }
        }

        void drop() throws Exception {
            try (Connection c = DriverManager.getConnection(url, connProps); Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM cursos WHERE id = " + course);
                st.executeUpdate("DELETE FROM logs WHERE usuario_id IN (" + teacher + "," + student + ")");
                st.executeUpdate("DELETE FROM intentos_examen WHERE estudiante_id = " + student);
                st.executeUpdate("DELETE FROM usuarios WHERE id IN (" + teacher + "," + student + ")");
            }
        }

        private static long insertUser(Connection c, String name, String hash) throws Exception {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO usuarios (username, email, password_hash) VALUES (?, ?, ?) RETURNING id")) {
                ps.setString(1, name);
                ps.setString(2, name + "@telecom.local");
                ps.setString(3, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        }

        private static long one(Connection c, String sql) throws Exception {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    static ConnectionFactory factory() {
        Properties p = new Properties();
        p.setProperty("app.db.url", url);
        p.setProperty("app.db.user", user);
        p.setProperty("app.db.password", password);
        return new ConnectionFactory(AppConfig.fromProperties(p, n -> null));
    }

    static long newAttempt(Connection c, long exam, long student) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO intentos_examen (examen_id, estudiante_id) VALUES (?, ?) RETURNING id")) {
            ps.setLong(1, exam);
            ps.setLong(2, student);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ------------------------------------------------------------------ T02
    static void t02(Fixture fx, String etiqueta, Path salida, int n) throws Exception {
        ConnectionFactory factory = factory();
        var hasher = new BCryptPasswordHasher();
        SessionManager sm = new SessionManager();
        UserService users = new UserService(factory, hasher, sm);
        AuthService auth = new AuthService(users, hasher, sm);
        String storedHash = users.findByUsername(fx.studentName()).getPasswordHash();

        // calentamiento (clases, JIT y cache de PostgreSQL)
        for (int i = 0; i < 30; i++) {
            try (Connection c = DriverManager.getConnection(url, connProps)) {
                c.createStatement().execute("SELECT 1");
            }
            auth.login(fx.studentName(), "ClaveTelecom123");
        }

        List<double[]> raw = new ArrayList<>();
        List<String> ops = new ArrayList<>();
        Stats stats = new Stats();

        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            try (Connection c = DriverManager.getConnection(url, connProps)) {
                stats.add("conexion_jdbc", i, (System.nanoTime() - t0) / 1e6);
            }
        }
        try (Connection c = DriverManager.getConnection(url, connProps); Statement st = c.createStatement()) {
            for (int i = 0; i < n; i++) {
                long t0 = System.nanoTime();
                try (ResultSet rs = st.executeQuery("SELECT 1")) {
                    rs.next();
                }
                stats.add("select_1", i, (System.nanoTime() - t0) / 1e6);
            }
        }
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            User u = users.findByUsername(fx.studentName());
            long t1 = System.nanoTime();
            boolean ok = hasher.verify("ClaveTelecom123", storedHash);
            long t2 = System.nanoTime();
            if (u == null || !ok) throw new IllegalStateException("login de prueba fallo");
            stats.add("login_consulta_usuario", i, (t1 - t0) / 1e6);
            stats.add("login_bcrypt", i, (t2 - t1) / 1e6);
            stats.add("login_total", i, (t2 - t0) / 1e6);
        }
        SessionManager studentSession = new SessionManager();
        new AuthService(new UserService(factory, hasher, studentSession), hasher, studentSession)
                .login(fx.studentName(), "ClaveTelecom123");
        ExamAttemptService attempts = new ExamAttemptService(factory, studentSession);
        for (int i = 0; i < n; i++) {
            long attempt;
            try (Connection c = DriverManager.getConnection(url, connProps)) {
                attempt = newAttempt(c, fx.exam(), fx.student());
            }
            long t0 = System.nanoTime();
            attempts.submitAttempt(attempt, fx.exam(), fx.student(), 4.2, 4, 5, "{\"calificacionFinal\":4.2,\"items\":[]}");
            stats.add("envio_evaluacion_transaccion", i, (System.nanoTime() - t0) / 1e6);
        }
        stats.write(salida, "t02_" + etiqueta);
    }

    // ------------------------------------------------------------------ T03
    static void t03(Fixture fx, String etiqueta, Path salida, int rondas, int[] clientes) throws Exception {
        ConnectionFactory factory = factory();
        var hasher = new BCryptPasswordHasher();
        // calentamiento
        for (int i = 0; i < 5; i++) {
            SessionManager sm = new SessionManager();
            new AuthService(new UserService(factory, hasher, sm), hasher, sm).login(fx.studentName(), "ClaveTelecom123");
        }
        List<String> resumen = new ArrayList<>();
        resumen.add("clientes,transacciones,errores,duracion_s,tps,conexiones_activas_max,conexiones_activas_media,"
                + "op,n,media_ms,mediana_ms,p95_ms,min_ms,max_ms");
        for (int clientesN : clientes) {
            Stats stats = new Stats();
            AtomicInteger errores = new AtomicInteger();
            AtomicInteger ok = new AtomicInteger();
            ConcurrentLinkedQueue<Integer> activas = new ConcurrentLinkedQueue<>();
            AtomicBoolean fin = new AtomicBoolean();
            Thread monitor = new Thread(() -> {
                try (Connection c = DriverManager.getConnection(url, connProps);
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()")) {
                    while (!fin.get()) {
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            activas.add(rs.getInt(1));
                        }
                        Thread.sleep(50);
                    }
                } catch (Exception ignored) {
                }
            });
            monitor.start();
            CountDownLatch listos = new CountDownLatch(clientesN);
            CountDownLatch salida0 = new CountDownLatch(1);
            CountDownLatch terminados = new CountDownLatch(clientesN);
            for (int k = 0; k < clientesN; k++) {
                int cliente = k;
                new Thread(() -> {
                    SessionManager sm = new SessionManager();
                    UserService users = new UserService(factory, hasher, sm);
                    AuthService auth = new AuthService(users, hasher, sm);
                    SimulationService sims = new SimulationService(factory, sm);
                    ExamAttemptService attempts = new ExamAttemptService(factory, sm);
                    listos.countDown();
                    try {
                        salida0.await();
                        for (int r = 0; r < rondas; r++) {
                            try {
                                long t0 = System.nanoTime();
                                if (auth.login(fx.studentName(), "ClaveTelecom123") == null) throw new IllegalStateException("login");
                                stats.add("inicio_sesion", cliente * rondas + r, (System.nanoTime() - t0) / 1e6);
                                ok.incrementAndGet();

                                t0 = System.nanoTime();
                                if (sims.listEnabledByCourse(fx.course()).isEmpty()) throw new IllegalStateException("practica");
                                stats.add("consulta_practica", cliente * rondas + r, (System.nanoTime() - t0) / 1e6);
                                ok.incrementAndGet();

                                long attempt;
                                t0 = System.nanoTime();
                                attempt = attempts.startAttempt(fx.exam(), fx.student());
                                stats.add("inicio_intento", cliente * rondas + r, (System.nanoTime() - t0) / 1e6);
                                ok.incrementAndGet();

                                t0 = System.nanoTime();
                                attempts.submitAttempt(attempt, fx.exam(), fx.student(), 4.0, 4, 5, "{\"calificacionFinal\":4.0}");
                                stats.add("envio_evaluacion", cliente * rondas + r, (System.nanoTime() - t0) / 1e6);
                                ok.incrementAndGet();
                            } catch (Exception e) {
                                errores.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        terminados.countDown();
                    }
                }).start();
            }
            listos.await();
            long t0 = System.nanoTime();
            salida0.countDown();
            terminados.await();
            double dur = (System.nanoTime() - t0) / 1e9;
            fin.set(true);
            monitor.join();
            List<Integer> act = new ArrayList<>(activas);
            int max = act.stream().mapToInt(Integer::intValue).max().orElse(0);
            double media = act.stream().mapToInt(Integer::intValue).average().orElse(0);
            for (String op : List.of("inicio_sesion", "consulta_practica", "inicio_intento", "envio_evaluacion")) {
                double[] v = stats.values(op);
                resumen.add(String.format(Locale.ROOT, "%d,%d,%d,%.2f,%.2f,%d,%.1f,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f",
                        clientesN, ok.get(), errores.get(), dur, ok.get() / dur, max, media, op, v.length,
                        Stats.mean(v), Stats.pct(v, 50), Stats.pct(v, 95), Stats.pct(v, 0), Stats.pct(v, 100)));
            }
            System.out.printf(Locale.ROOT, "%2d clientes: %d operaciones, %d errores, %.1f s, %.1f op/s, conexiones activas max %d%n",
                    clientesN, ok.get(), errores.get(), dur, ok.get() / dur, max);
            stats.writeRaw(salida, "t03_" + etiqueta + "_c" + clientesN);
        }
        Files.write(salida.resolve("t03_" + etiqueta + "_resumen.csv"), resumen);
    }

    // ------------------------------------------------------------------ estadistica
    static final class Stats {
        private final java.util.Map<String, ConcurrentLinkedQueue<double[]>> data = new java.util.concurrent.ConcurrentHashMap<>();

        void add(String op, int i, double ms) {
            data.computeIfAbsent(op, k -> new ConcurrentLinkedQueue<>()).add(new double[]{i, ms});
        }

        double[] values(String op) {
            var q = data.get(op);
            return q == null ? new double[0] : q.stream().mapToDouble(x -> x[1]).sorted().toArray();
        }

        static double mean(double[] v) {
            return Arrays.stream(v).average().orElse(Double.NaN);
        }

        /** Percentil por interpolacion lineal; p=0 es el minimo y p=100 el maximo. v debe venir ordenado. */
        static double pct(double[] v, double p) {
            if (v.length == 0) return Double.NaN;
            double pos = p / 100.0 * (v.length - 1);
            int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
            return v[lo] + (v[hi] - v[lo]) * (pos - lo);
        }

        void write(Path dir, String name) throws Exception {
            List<String> lines = new ArrayList<>(List.of("operacion,n,media_ms,mediana_ms,p95_ms,min_ms,max_ms"));
            List<String> keys = new ArrayList<>(data.keySet());
            Collections.sort(keys);
            for (String op : keys) {
                double[] v = values(op);
                lines.add(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f", op, v.length, mean(v), pct(v, 50),
                        pct(v, 95), pct(v, 0), pct(v, 100)));
                System.out.println(lines.get(lines.size() - 1));
            }
            Files.write(dir.resolve(name + "_resumen.csv"), lines);
            writeRaw(dir, name);
        }

        void writeRaw(Path dir, String name) throws Exception {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve(name + "_muestras.csv")))) {
                w.println("operacion,indice,ms");
                for (var e : new java.util.TreeMap<>(data).entrySet()) {
                    for (double[] x : e.getValue()) {
                        w.printf(Locale.ROOT, "%s,%d,%.4f%n", e.getKey(), (int) x[0], x[1]);
                    }
                }
            }
        }
    }
}

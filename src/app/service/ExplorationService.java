package app.service;

import app.dao.jdbc.ExplorationDaoJdbc;
import app.db.ConnectionFactory;
import app.model.VisorEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Registro de la exploracion en los modelos 3D (C05): cada evento del visor se guarda en exploracion_eventos a
 * medida que llega, y el profesor consulta por estudiante las estructuras vistas y el tiempo por cavidad y ficha.
 */
public class ExplorationService {
    public static final Map<String, String> MODEL_NAMES = Map.of(
            "toracica", "Cavidad toracica",
            "abdominal", "Cavidad abdominal",
            "inguinal", "Region inguinal",
            "general", "Vista general (todas las cavidades)");

    private final ExplorationDaoJdbc dao;

    public ExplorationService(ConnectionFactory factory) {
        this.dao = new ExplorationDaoJdbc(factory);
    }

    /** Contadores de una sesion de grabacion. */
    public static final class Recorder implements Consumer<VisorEvent> {
        private final ExplorationService service;
        private final long userId;
        private final Long simulationId;
        private final AtomicInteger saved = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private volatile String lastError;

        private Recorder(ExplorationService service, long userId, Long simulationId) {
            this.service = service;
            this.userId = userId;
            this.simulationId = simulationId;
        }

        /** Guarda el evento; un fallo de la base no interrumpe la lectura del visor (se cuenta y se informa). */
        @Override
        public void accept(VisorEvent event) {
            try {
                service.dao.insert(userId, simulationId, event);
                saved.incrementAndGet();
            } catch (RuntimeException ex) {
                failed.incrementAndGet();
                lastError = ex.getMessage();
            }
        }

        public int saved() {
            return saved.get();
        }

        public int failed() {
            return failed.get();
        }

        public String lastError() {
            return lastError;
        }
    }

    /** Consumidor para {@link Visor3DService#ejecutar}: graba los eventos del usuario (y de la practica, si hay). */
    public Recorder recorder(long userId, Long simulationId) {
        return new Recorder(this, userId, simulationId);
    }

    public List<ExplorationDaoJdbc.StudentSummary> summaryByCourses(List<Long> courseIds) {
        return dao.summaryByCourses(courseIds);
    }

    public List<ExplorationDaoJdbc.TimeRow> timeByModel(long studentId) {
        return dao.timeByModel(studentId).stream()
                .map(r -> new ExplorationDaoJdbc.TimeRow(r.key(), MODEL_NAMES.getOrDefault(r.key(), r.key()), r.count(), r.seconds()))
                .toList();
    }

    public List<ExplorationDaoJdbc.TimeRow> timeByFicha(long studentId) {
        return dao.timeByFicha(studentId);
    }
}

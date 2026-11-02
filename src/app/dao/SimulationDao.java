package app.dao;

import app.model.Simulation;

import java.util.List;

public interface SimulationDao {
    long create(Simulation simulation);
    List<Simulation> listByCourse(long courseId);
    Simulation findById(long simulationId);
    void updateEnabled(long simulationId, boolean enabled);
    void updateDetails(long simulationId, String name, String description, String configJson);
    void delete(long simulationId);
}

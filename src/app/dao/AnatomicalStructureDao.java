package app.dao;

import app.model.AnatomicalStructure;

import java.util.List;

public interface AnatomicalStructureDao {
    AnatomicalStructure findByFichaId(String fichaId);
    AnatomicalStructure findByObjectId(String objectId);
    List<AnatomicalStructure> listByCavity(String cavityCode);
    List<AnatomicalStructure> listAll();
}

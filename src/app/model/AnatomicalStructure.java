package app.model;

import java.util.List;

/** Ficha anatómica de una estructura de las cavidades equinas (tabla estructuras_anatomicas). */
public class AnatomicalStructure {
    private String fichaId;
    private String name;
    private String latinName;
    private String cavityCode;
    private String systemCode;
    private String description;
    private String topography;
    private String surfaceProjection;
    private String relations;
    private String fixation;
    private String vascularization;
    private String dimensions;
    private String clinicalNotes;
    private String examination;
    private List<String> references;
    private boolean pendingValidation;
    private List<String> pendingFields;
    private String applicableSex;

    public AnatomicalStructure() {}

    public String getFichaId() { return fichaId; }
    public void setFichaId(String fichaId) { this.fichaId = fichaId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLatinName() { return latinName; }
    public void setLatinName(String latinName) { this.latinName = latinName; }

    public String getCavityCode() { return cavityCode; }
    public void setCavityCode(String cavityCode) { this.cavityCode = cavityCode; }

    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTopography() { return topography; }
    public void setTopography(String topography) { this.topography = topography; }

    public String getSurfaceProjection() { return surfaceProjection; }
    public void setSurfaceProjection(String surfaceProjection) { this.surfaceProjection = surfaceProjection; }

    public String getRelations() { return relations; }
    public void setRelations(String relations) { this.relations = relations; }

    public String getFixation() { return fixation; }
    public void setFixation(String fixation) { this.fixation = fixation; }

    public String getVascularization() { return vascularization; }
    public void setVascularization(String vascularization) { this.vascularization = vascularization; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }

    public String getExamination() { return examination; }
    public void setExamination(String examination) { this.examination = examination; }

    public List<String> getReferences() { return references; }
    public void setReferences(List<String> references) { this.references = references; }

    public boolean isPendingValidation() { return pendingValidation; }
    public void setPendingValidation(boolean pendingValidation) { this.pendingValidation = pendingValidation; }

    public List<String> getPendingFields() { return pendingFields; }
    public void setPendingFields(List<String> pendingFields) { this.pendingFields = pendingFields; }

    public String getApplicableSex() { return applicableSex; }
    public void setApplicableSex(String applicableSex) { this.applicableSex = applicableSex; }
}

package app.model;

public class Question {
    private long id;
    private long examId;
    private String statement;
    private String type;
    private double score;
    private byte[] imageData;
    private String imageName;
    private String imageContentType;
    private String objectId;

    public Question() {}

    public Question(long id, long examId, String statement, String type, double score) {
        this.id = id;
        this.examId = examId;
        this.statement = statement;
        this.type = type;
        this.score = score;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getExamId() { return examId; }
    public void setExamId(long examId) { this.examId = examId; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

    public String getImageContentType() { return imageContentType; }
    public void setImageContentType(String imageContentType) { this.imageContentType = imageContentType; }

    /** Estructura del modelo 3D (modelos_3d.objeto_id) a la que se refiere la pregunta; null si no se enlaza (C07). */
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId == null || objectId.isBlank() ? null : objectId.strip(); }
}

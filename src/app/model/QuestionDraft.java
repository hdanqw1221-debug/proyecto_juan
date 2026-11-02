package app.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuestionDraft {
    private Long id;
    private String statement;
    private String type;
    private double score;
    private List<Answer> answers = new ArrayList<>();
    private byte[] imageData;
    private String imageName;
    private String imageContentType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public List<Answer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<Answer> answers) {
        this.answers = answers == null ? new ArrayList<>() : new ArrayList<>(answers);
    }

    public byte[] getImageData() {
        return imageData == null ? null : Arrays.copyOf(imageData, imageData.length);
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData == null ? null : Arrays.copyOf(imageData, imageData.length);
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public void setImageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
    }
}

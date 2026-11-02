package app.dao;

import app.model.Question;

import java.util.List;

public interface QuestionDao {
    long create(Question question);
    List<Question> listByExam(long examId);
}

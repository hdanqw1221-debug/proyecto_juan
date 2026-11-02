package app.dao;

import app.model.Answer;

import java.util.List;

public interface AnswerDao {
    long create(Answer answer);
    List<Answer> listByQuestion(long questionId);
}

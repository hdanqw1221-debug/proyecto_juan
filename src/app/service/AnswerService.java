package app.service;

import app.dao.AnswerDao;
import app.dao.jdbc.AnswerDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Answer;

import java.util.List;

public class AnswerService {
    private final AnswerDao answerDao;

    public AnswerService(ConnectionFactory factory) {
        this.answerDao = new AnswerDaoJdbc(factory);
    }

    public long createAnswer(long questionId, String content, boolean correct) {
        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setContent(content);
        answer.setCorrect(correct);
        return answerDao.create(answer);
    }

    public List<Answer> listByQuestion(long questionId) {
        return answerDao.listByQuestion(questionId);
    }
}

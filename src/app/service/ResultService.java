package app.service;

import app.dao.ResultDao;
import app.dao.jdbc.ResultDaoJdbc;
import app.db.ConnectionFactory;
import app.model.Result;

import java.util.List;

public class ResultService {
    private final ResultDao resultDao;

    public ResultService(ConnectionFactory factory) {
        this.resultDao = new ResultDaoJdbc(factory);
    }

    public long createResult(long attemptId, String detailJson) {
        Result result = new Result();
        result.setAttemptId(attemptId);
        result.setDetailJson(detailJson);
        return resultDao.create(result);
    }

    public List<Result> listByStudent(long studentId) {
        return resultDao.listByStudent(studentId);
    }

    public Result findByAttemptId(long attemptId) {
        return resultDao.findByAttemptId(attemptId);
    }
}

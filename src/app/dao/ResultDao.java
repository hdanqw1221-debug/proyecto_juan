package app.dao;

import app.model.Result;

import java.util.List;

public interface ResultDao {
    long create(Result result);
    List<Result> listByStudent(long studentId);
    Result findByAttemptId(long attemptId);
}

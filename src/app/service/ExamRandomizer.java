package app.service;

import app.model.Answer;
import app.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Servicio utilitario para el mecanismo anti-fraude en evaluaciones:
 * Barajado pseudoaleatorio determinista por intento de preguntas y alternativas.
 */
public class ExamRandomizer {

    /**
     * Baraja la lista de preguntas utilizando una semilla única por intento y estudiante.
     *
     * @param questions Lista original de preguntas del examen
     * @param attemptId ID del intento en curso
     * @param studentId ID del estudiante
     * @param examId    ID de la evaluación
     * @return Nueva lista con las preguntas en orden aleatorio para este intento
     */
    public static List<Question> shuffleQuestions(List<Question> questions, long attemptId, long studentId, long examId) {
        if (questions == null) {
            return new ArrayList<>();
        }
        List<Question> copy = new ArrayList<>(questions);
        if (copy.size() > 1) {
            long seed = attemptId * 31L + studentId * 17L + examId;
            Collections.shuffle(copy, new Random(seed));
        }
        return copy;
    }

    /**
     * Baraja la lista de alternativas/respuestas para una pregunta en un intento específico.
     *
     * @param answers    Lista original de respuestas
     * @param questionId ID de la pregunta
     * @param attemptId  ID del intento en curso
     * @return Nueva lista con las alternativas en orden aleatorio para este intento
     */
    public static List<Answer> shuffleAnswers(List<Answer> answers, long questionId, long attemptId) {
        if (answers == null) {
            return new ArrayList<>();
        }
        List<Answer> copy = new ArrayList<>(answers);
        if (copy.size() > 1) {
            long seed = attemptId * 37L + questionId * 13L;
            Collections.shuffle(copy, new Random(seed));
        }
        return copy;
    }
}

package app.service;

import app.model.Answer;
import app.model.Question;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ExamRandomizerTest {

    @Test
    public void testShuffleQuestionsPreservesData() {
        List<Question> questions = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            Question q = new Question();
            q.setId(i);
            q.setStatement("Pregunta " + i);
            questions.add(q);
        }

        List<Question> shuffled = ExamRandomizer.shuffleQuestions(questions, 101L, 5L, 1L);

        assertEquals(10, shuffled.size());
        Set<Long> ids = new HashSet<>();
        for (Question q : shuffled) {
            ids.add(q.getId());
        }
        assertEquals(10, ids.size(), "Todas las preguntas originales deben estar presentes");
    }

    @Test
    public void testDifferentAttemptsProduceDifferentPermutations() {
        List<Question> questions = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            Question q = new Question();
            q.setId(i);
            q.setStatement("Pregunta " + i);
            questions.add(q);
        }

        // Intento de estudiante 1
        List<Question> attempt1 = ExamRandomizer.shuffleQuestions(questions, 1001L, 10L, 5L);
        // Intento de estudiante 2 (o segundo intento del mismo estudiante)
        List<Question> attempt2 = ExamRandomizer.shuffleQuestions(questions, 1002L, 11L, 5L);

        // Mismo intento repetido (debe ser determinista)
        List<Question> attempt1Repeat = ExamRandomizer.shuffleQuestions(questions, 1001L, 10L, 5L);

        // Verificar consistencia determinista
        for (int i = 0; i < questions.size(); i++) {
            assertEquals(attempt1.get(i).getId(), attempt1Repeat.get(i).getId());
        }

        // Verificar que los intentos 1 y 2 no tengan exactamente el mismo orden
        boolean hasDifference = false;
        for (int i = 0; i < questions.size(); i++) {
            if (attempt1.get(i).getId() != attempt2.get(i).getId()) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "Dos intentos diferentes deben tener un orden de preguntas distinto");
    }

    @Test
    public void testShuffleAnswersPreservesData() {
        List<Answer> answers = new ArrayList<>();
        for (long i = 1; i <= 4; i++) {
            Answer a = new Answer();
            a.setId(i);
            a.setContent("Opcion " + i);
            a.setCorrect(i == 2);
            answers.add(a);
        }

        List<Answer> shuffled = ExamRandomizer.shuffleAnswers(answers, 50L, 1001L);
        assertEquals(4, shuffled.size());

        Set<Long> ids = new HashSet<>();
        boolean foundCorrect = false;
        for (Answer a : shuffled) {
            ids.add(a.getId());
            if (a.isCorrect()) foundCorrect = true;
        }

        assertEquals(4, ids.size());
        assertTrue(foundCorrect, "La alternativa correcta debe conservarse");
    }

    @Test
    public void testNullOrSingleItemsHandling() {
        assertNotNull(ExamRandomizer.shuffleQuestions(null, 1, 1, 1));
        assertTrue(ExamRandomizer.shuffleQuestions(null, 1, 1, 1).isEmpty());

        List<Question> single = List.of(new Question());
        assertEquals(1, ExamRandomizer.shuffleQuestions(single, 1, 1, 1).size());

        assertNotNull(ExamRandomizer.shuffleAnswers(null, 1, 1));
        assertTrue(ExamRandomizer.shuffleAnswers(null, 1, 1).isEmpty());
    }
}

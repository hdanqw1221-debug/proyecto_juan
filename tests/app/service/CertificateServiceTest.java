package app.service;

import app.model.Course;
import app.model.Exam;
import app.model.ExamAttempt;
import app.model.User;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class CertificateServiceTest {

    @Test
    public void testPassingThreshold() {
        CertificateService service = new CertificateService();

        ExamAttempt nullScore = new ExamAttempt();
        assertFalse(service.isPassing(nullScore));

        ExamAttempt failing = new ExamAttempt();
        failing.setTotalScore(2.9);
        assertFalse(service.isPassing(failing));

        ExamAttempt passing = new ExamAttempt();
        passing.setTotalScore(3.0);
        assertTrue(service.isPassing(passing));

        ExamAttempt honor = new ExamAttempt();
        honor.setTotalScore(5.0);
        assertTrue(service.isPassing(honor));
    }

    @Test
    public void testVerificationCodeGeneration() {
        CertificateService service = new CertificateService();

        User student = new User();
        student.setId(10L);

        Exam exam = new Exam();
        exam.setId(20L);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(30L);
        attempt.setTotalScore(4.5);
        attempt.setEndAt(LocalDateTime.of(2026, 9, 24, 15, 0));

        String code1 = service.generateVerificationCode(student, exam, attempt);
        String code2 = service.generateVerificationCode(student, exam, attempt);

        assertNotNull(code1);
        assertTrue(code1.startsWith("UP-2026-"));
        assertEquals(code1, code2, "El codigo debe ser determinista para los mismos datos");
    }

    @Test
    public void testDistinctionLabels() {
        CertificateService service = new CertificateService();

        assertTrue(service.getDistinctionLabel(4.9).contains("Summa Cum Laude"));
        assertTrue(service.getDistinctionLabel(4.5).contains("Distinción"));
        assertTrue(service.getDistinctionLabel(4.0).contains("Sobresaliente"));
        assertTrue(service.getDistinctionLabel(3.5).contains("Satisfactoriamente"));
    }

    @Test
    public void testHtmlCertificateGeneration() throws IOException {
        CertificateService service = new CertificateService();

        User student = new User();
        student.setId(100L);
        student.setUsername("juan_perez");
        student.setEmail("juan@unipamplona.edu.co");

        Course course = new Course(1L, "Anatomia Humana I", "Curso clinico", 1L, LocalDateTime.now());
        Exam exam = new Exam(5L, 1L, "Examen Parcial Osteologia", "Teoria", true, null, null, 1, 60);

        ExamAttempt attempt = new ExamAttempt(123L, 5L, 100L, LocalDateTime.now().minusMinutes(45), LocalDateTime.now(), 4.8);

        File tempFile = File.createTempFile("test_cert_", ".html");
        try {
            service.generateCertificateHtmlFile(student, course, exam, attempt, tempFile);
            assertTrue(tempFile.exists());
            assertTrue(tempFile.length() > 0);

            String html = Files.readString(tempFile.toPath());
            assertTrue(html.contains("JUAN_PEREZ"));
            assertTrue(html.contains("Anatomia Humana I"));
            assertTrue(html.contains("Examen Parcial Osteologia"));
            assertTrue(html.contains("4.8"));
            assertTrue(html.contains("UNIVERSIDAD DE PAMPLONA"));
            assertTrue(html.contains("@media print"));
        } finally {
            tempFile.delete();
        }
    }
}

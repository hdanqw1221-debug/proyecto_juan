with open('src/app/ui/panels/StudentPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

old_detail = """        ExamAttempt attempt = examAttemptService.findById(attemptId);
        String detail = result.getDetailJson();
        String quote = Character.toString(34);
        int totalAnswers = countOccurrences(detail, quote + "questionId" + quote);
        int correctAnswers = countOccurrences(detail, quote + "correct" + quote + ":true");
        String score = attempt != null && attempt.getTotalScore() != null
                ? String.format(Locale.ROOT, "%.1f / 5.0", attempt.getTotalScore())
                : "Pendiente";

        JPanel summary = UiTheme.card(new GridLayout(0, 1, 6, 6));
        summary.add(UiTheme.title("Resultado de la evaluacion", 20f));
        summary.add(new JLabel("Calificacion: " + score));
        summary.add(new JLabel("Respuestas correctas: " + correctAnswers + " de " + totalAnswers));
        if (attempt != null) {
            summary.add(new JLabel("Inicio: " + formatDate(attempt.getStartAt())));
            summary.add(new JLabel("Finalizacion: " + formatDate(attempt.getEndAt())));
        }
        UiTheme.styleTree(summary);
        JOptionPane.showMessageDialog(this, summary, "Tu resultado", JOptionPane.INFORMATION_MESSAGE);"""

new_detail = """        ExamAttempt attempt = examAttemptService.findById(attemptId);
        Exam exam = attempt != null ? examService.findExam(attempt.getExamId()) : null;
        app.ui.ExamAttemptReviewDialog reviewDialog = new app.ui.ExamAttemptReviewDialog(
                (Frame) SwingUtilities.getWindowAncestor(this),
                attempt, exam, result, examService, answerService
        );
        reviewDialog.setVisible(true);"""

assert old_detail in text, 'old_detail not found'
text = text.replace(old_detail, new_detail, 1)

with open('src/app/ui/panels/StudentPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('StudentPanel showResultDetail updated successfully!')

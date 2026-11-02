package app.ui;

import app.model.Answer;
import app.model.Exam;
import app.model.Question;
import app.service.AnswerService;
import app.service.ExamAttemptService;
import app.service.ExamRandomizer;
import app.service.ExamService;
import app.service.ResultService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ExamDialog extends JDialog {
    private final long examId;
    private final long studentId;
    private final ExamService examService;
    private final AnswerService answerService;
    private final ExamAttemptService attemptService;
    private final ResultService resultService;

    private long attemptId;
    private Exam exam;
    private LocalDateTime examStartedAt;
    private Timer timer;
    private boolean finishing;

    private final List<Question> questions = new ArrayList<>();
    private final Map<Long, QuestionResponse> responsesByQuestion = new HashMap<>();
    private final Map<Long, List<Answer>> answersByQuestion = new HashMap<>();
    private final JProgressBar answerProgress = new JProgressBar();
    private final JLabel progressLabel = new JLabel();
    private final JLabel timerLabel = new JLabel();

    public ExamDialog(Frame owner, long examId, long studentId,
                      ExamService examService, AnswerService answerService,
                      ExamAttemptService attemptService, ResultService resultService) {
        super(owner, "Evaluacion", true);
        this.examId = examId;
        this.studentId = studentId;
        this.examService = examService;
        this.answerService = answerService;
        this.attemptService = attemptService;
        this.resultService = resultService;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(820, 620));
        setSize(920, 700);
        initUi();
        setLocationRelativeTo(owner);
    }

    private void initUi() {
        exam = examService.findExam(examId);
        if (!examService.isAvailable(exam)) {
            JOptionPane.showMessageDialog(this, "Esta evaluacion ya no esta disponible.");
            dispose();
            return;
        }

        long usedAttempts = attemptService.listByStudent(studentId).stream()
                .filter(attempt -> attempt.getExamId() == examId)
                .count();
        if (usedAttempts >= Math.max(1, exam.getMaxAttempts())) {
            JOptionPane.showMessageDialog(this,
                    "Ya utilizaste todos los intentos permitidos para esta evaluacion.");
            dispose();
            return;
        }

        List<Question> rawQuestions = examService.listQuestionsByExam(examId);
        if (rawQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Esta evaluacion todavia no tiene preguntas disponibles.");
            dispose();
            return;
        }

        attemptId = attemptService.startAttempt(examId, studentId);
        examStartedAt = LocalDateTime.now();

        // Aplicar barajado anti-copia con semilla determinista por intento
        questions.addAll(ExamRandomizer.shuffleQuestions(rawQuestions, attemptId, studentId, examId));

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        root.setBackground(UiTheme.BACKGROUND);
        root.add(buildHeader(usedAttempts), BorderLayout.NORTH);
        root.add(buildQuestions(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        UiTheme.styleTree(root);
        setContentPane(root);
        startTimer();
        updateProgress();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });
    }

    private JPanel buildHeader(long usedAttempts) {
        JPanel header = UiTheme.card(new BorderLayout(18, 12));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("EVALUACION EN CURSO");
        eyebrow.setForeground(UiTheme.PRIMARY);
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = UiTheme.title(exam.getTitle(), 25f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel meta = new JLabel(questions.size() + " pregunta(s)  |  Intento "
                + (usedAttempts + 1) + " de " + Math.max(1, exam.getMaxAttempts()));
        meta.setForeground(UiTheme.MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(4));
        copy.add(title);
        copy.add(Box.createVerticalStrut(4));
        copy.add(meta);
        copy.add(Box.createVerticalStrut(6));

        JPanel securityBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        securityBadge.setOpaque(false);
        securityBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel securityPill = new JLabel("MODO SEGURO");
        securityPill.setFont(new Font("Segoe UI", Font.BOLD, 10));
        securityPill.setForeground(UiTheme.SUCCESS);
        securityPill.setBackground(UiTheme.SUCCESS_SOFT);
        securityPill.setOpaque(true);
        securityPill.setBorder(BorderFactory.createCompoundBorder(
                UiTheme.roundedBorder(new Color(UiTheme.SUCCESS.getRed(), UiTheme.SUCCESS.getGreen(), UiTheme.SUCCESS.getBlue(), 80)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));

        JLabel antiCopyNotice = new JLabel("Preguntas y alternativas aleatorizadas para este intento.");
        antiCopyNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        antiCopyNotice.setForeground(UiTheme.MUTED);

        securityBadge.add(securityPill);
        securityBadge.add(antiCopyNotice);
        copy.add(securityBadge);

        timerLabel.setForeground(UiTheme.PRIMARY_DARK);
        timerLabel.setFont(new Font("Bahnschrift", Font.BOLD, 18));
        timerLabel.setBorder(BorderFactory.createCompoundBorder(
                UiTheme.roundedBorder(UiTheme.PRIMARY_SOFT),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        header.add(copy, BorderLayout.CENTER);
        header.add(timerLabel, BorderLayout.EAST);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 4));
        progressPanel.setOpaque(false);
        answerProgress.setMinimum(0);
        answerProgress.setMaximum(questions.size());
        answerProgress.setStringPainted(false);
        progressLabel.setForeground(UiTheme.MUTED);
        progressPanel.add(answerProgress, BorderLayout.CENTER);
        progressPanel.add(progressLabel, BorderLayout.EAST);
        header.add(progressPanel, BorderLayout.SOUTH);
        return header;
    }

    private JScrollPane buildQuestions() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UiTheme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(2, 2, 12, 2));

        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            List<Answer> rawAnswers = answerService.listByQuestion(question.getId());
            // Aplicar barajado anti-copia de alternativas para preguntas de seleccion
            List<Answer> answers = ExamRandomizer.shuffleAnswers(rawAnswers, question.getId(), attemptId);
            answersByQuestion.put(question.getId(), answers);

            JPanel questionCard = UiTheme.card(new BorderLayout(10, 10));
            questionCard.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel number = new JLabel("PREGUNTA " + (index + 1) + " DE " + questions.size());
            number.setForeground(UiTheme.PRIMARY);
            number.setFont(number.getFont().deriveFont(Font.BOLD, 11f));
            JLabel typeBadge = new JLabel(questionTypeLabel(question.getType())
                    + "  |  " + question.getScore() + " punto(s)");
            typeBadge.setForeground(UiTheme.MUTED);
            typeBadge.setFont(typeBadge.getFont().deriveFont(Font.BOLD, 11f));
            JPanel questionMeta = new JPanel(new BorderLayout());
            questionMeta.setOpaque(false);
            questionMeta.add(number, BorderLayout.WEST);
            questionMeta.add(typeBadge, BorderLayout.EAST);
            questionCard.add(questionMeta, BorderLayout.NORTH);

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            JLabel statement = new JLabel("<html><div style='width:650px'><b>"
                    + escapeHtml(question.getStatement()) + "</b></div></html>");
            statement.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(statement);
            if (question.getImageData() != null && question.getImageData().length > 0) {
                ImageIcon imageIcon = questionImage(question.getImageData(), 700, 330);
                if (imageIcon != null) {
                    body.add(Box.createVerticalStrut(12));
                    JLabel image = new JLabel(imageIcon);
                    image.setAlignmentX(Component.LEFT_ALIGNMENT);
                    image.setBorder(BorderFactory.createCompoundBorder(
                            UiTheme.roundedBorder(UiTheme.BORDER),
                            BorderFactory.createEmptyBorder(6, 6, 6, 6)
                    ));
                    image.setToolTipText(question.getImageName());
                    body.add(image);
                }
            }
            body.add(Box.createVerticalStrut(12));

            QuestionResponse response = new QuestionResponse(question.getType());
            if (response.isShortAnswer()) {
                JTextField shortAnswer = new JTextField();
                shortAnswer.setToolTipText("Escribe una respuesta corta");
                shortAnswer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
                shortAnswer.setAlignmentX(Component.LEFT_ALIGNMENT);
                shortAnswer.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        updateProgress();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        updateProgress();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        updateProgress();
                    }
                });
                response.textAnswer = shortAnswer;
                body.add(shortAnswer);
            } else {
                ButtonGroup singleChoiceGroup = response.isMultipleChoice() ? null : new ButtonGroup();
                for (int aIdx = 0; aIdx < answers.size(); aIdx++) {
                    Answer answer = answers.get(aIdx);
                    char letter = (char) ('A' + aIdx);
                    String displayLabel = letter + ")  " + answer.getContent();
                    AbstractButton option = response.isMultipleChoice()
                            ? new JCheckBox(displayLabel)
                            : new JRadioButton(displayLabel);
                    option.setActionCommand(String.valueOf(answer.getId()));
                    option.setAlignmentX(Component.LEFT_ALIGNMENT);
                    option.setBorder(BorderFactory.createEmptyBorder(6, 3, 6, 3));
                    option.addActionListener(e -> updateProgress());
                    if (singleChoiceGroup != null) {
                        singleChoiceGroup.add(option);
                    }
                    response.choiceButtons.add(option);
                    body.add(option);
                }
            }
            responsesByQuestion.put(question.getId(), response);
            questionCard.add(body, BorderLayout.CENTER);
            content.add(questionCard);
            content.add(Box.createVerticalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        JLabel hint = new JLabel("Puedes revisar y cambiar tus respuestas antes de enviar.");
        hint.setForeground(UiTheme.MUTED);

        JButton submit = new JButton("Enviar evaluacion");
        submit.addActionListener(e -> confirmSubmission());
        footer.add(hint, BorderLayout.WEST);
        footer.add(submit, BorderLayout.EAST);
        return footer;
    }

    private void startTimer() {
        int durationMinutes = Math.max(1, exam.getDurationMinutes());
        updateTimerLabel(durationMinutes * 60L);
        timer = new Timer(1000, e -> {
            long elapsedSeconds = Duration.between(examStartedAt, LocalDateTime.now()).getSeconds();
            long remainingSeconds = (durationMinutes * 60L) - elapsedSeconds;
            if (remainingSeconds <= 0) {
                timer.stop();
                JOptionPane.showMessageDialog(this,
                        "El tiempo termino. Tus respuestas se enviaran automaticamente.");
                finishExam();
                return;
            }
            updateTimerLabel(remainingSeconds);
        });
        timer.start();
    }

    private void updateTimerLabel(long remainingSeconds) {
        timerLabel.setText(String.format("Tiempo  %02d:%02d",
                remainingSeconds / 60, remainingSeconds % 60));
        timerLabel.setForeground(remainingSeconds <= 300 ? UiTheme.PRIMARY : UiTheme.PRIMARY_DARK);
    }

    private void updateProgress() {
        int answered = answeredQuestions();
        answerProgress.setValue(answered);
        progressLabel.setText(answered + " de " + questions.size() + " respondidas");
    }

    private int answeredQuestions() {
        int answered = 0;
        for (QuestionResponse response : responsesByQuestion.values()) {
            if (response.isAnswered()) {
                answered++;
            }
        }
        return answered;
    }

    private void confirmSubmission() {
        int pending = questions.size() - answeredQuestions();
        String message = pending > 0
                ? "Aun tienes " + pending + " pregunta(s) sin responder.\n"
                        + "Las preguntas pendientes se calificaran con cero.\n\n¿Deseas enviar?"
                : "Respondiste todas las preguntas.\n\n¿Deseas enviar la evaluacion?";
        int confirmation = JOptionPane.showConfirmDialog(
                this, message, "Confirmar envio", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
        );
        if (confirmation == JOptionPane.YES_OPTION) {
            finishExam();
        }
    }

    private void requestClose() {
        if (finishing) {
            return;
        }
        Object[] options = {"Enviar y salir", "Continuar evaluacion"};
        int option = JOptionPane.showOptionDialog(
                this,
                "Si sales ahora, se enviaran las respuestas marcadas hasta este momento.",
                "¿Deseas salir?",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]
        );
        if (option == 0) {
            finishExam();
        }
    }

    private void finishExam() {
        if (finishing) {
            return;
        }
        finishing = true;
        if (timer != null) {
            timer.stop();
        }

        double rawScore = 0.0;
        double maxRawScore = 0.0;
        int correctAnswers = 0;
        StringBuilder json = new StringBuilder();
        json.append("{\"examId\":").append(examId)
                .append(",\"attemptId\":").append(attemptId)
                .append(",\"items\":[");

        boolean first = true;
        for (Question question : questions) {
            maxRawScore += question.getScore();
            QuestionResponse response = responsesByQuestion.get(question.getId());
            List<Answer> availableAnswers = answersByQuestion.getOrDefault(question.getId(), List.of());
            Set<Long> selectedIds = response.selectedAnswerIds();
            Set<Long> correctIds = new LinkedHashSet<>();
            for (Answer answer : availableAnswers) {
                if (answer.isCorrect()) {
                    correctIds.add(answer.getId());
                }
            }

            boolean correct;
            if (response.isShortAnswer()) {
                String submittedText = response.textAnswer.getText();
                correct = availableAnswers.stream()
                        .filter(Answer::isCorrect)
                        .map(Answer::getContent)
                        .anyMatch(expected -> normalizeAnswer(expected).equals(normalizeAnswer(submittedText)));
            } else {
                correct = !selectedIds.isEmpty() && selectedIds.equals(correctIds);
            }

            double score = correct ? question.getScore() : 0.0;
            rawScore += score;
            if (correct) {
                correctAnswers++;
            }

            if (!first) {
                json.append(",");
            }
            json.append("{\"questionId\":").append(question.getId())
                    .append(",\"type\":\"").append(escapeJson(question.getType())).append("\"")
                    .append(",\"selectedAnswerId\":").append(selectedIds.stream().findFirst().orElse(-1L))
                    .append(",\"selectedAnswerIds\":[");
            boolean firstSelected = true;
            for (Long selectedId : selectedIds) {
                if (!firstSelected) {
                    json.append(",");
                }
                json.append(selectedId);
                firstSelected = false;
            }
            json.append("]");
            if (response.isShortAnswer()) {
                json.append(",\"textAnswer\":\"")
                        .append(escapeJson(response.textAnswer.getText()))
                        .append("\"");
            }
            json
                    .append(",\"correct\":").append(correct)
                    .append(",\"score\":").append(score)
                    .append("}");
            first = false;
        }

        double finalScore = maxRawScore > 0 ? (rawScore / maxRawScore) * 5.0 : 0.0;
        finalScore = Math.round(finalScore * 10.0) / 10.0;
        json.append("],\"totalRaw\":").append(rawScore)
                .append(",\"maxRaw\":").append(maxRawScore)
                .append(",\"calificacionFinal\":").append(finalScore)
                .append("}");

        attemptService.finishAttempt(attemptId, finalScore);
        resultService.createResult(attemptId, json.toString());

        JPanel result = UiTheme.card(new GridLayout(0, 1, 7, 7));
        result.add(UiTheme.title("Evaluacion enviada", 21f));
        result.add(new JLabel("Calificacion: " + finalScore + " / 5.0"));
        result.add(new JLabel("Respuestas correctas: " + correctAnswers + " de " + questions.size()));
        result.add(new JLabel("Ya puedes consultar el detalle en Calificaciones."));
        UiTheme.styleTree(result);
        JOptionPane.showMessageDialog(this, result, "Resultado", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private ImageIcon questionImage(byte[] data, int maxWidth, int maxHeight) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                return null;
            }
            double scale = Math.min(1.0, Math.min(
                    (double) maxWidth / image.getWidth(),
                    (double) maxHeight / image.getHeight()
            ));
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    private String questionTypeLabel(String type) {
        return switch (normalizeQuestionType(type)) {
            case "seleccion_multiple" -> "SELECCION MULTIPLE";
            case "verdadero_falso" -> "VERDADERO / FALSO";
            case "abierta" -> "RESPUESTA CORTA";
            default -> "SELECCION UNICA";
        };
    }

    private String normalizeQuestionType(String type) {
        return type == null ? "seleccion_unica" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAnswer(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        clean = Normalizer.normalize(clean, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return clean.replaceAll("\\s+", " ");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private final class QuestionResponse {
        private final String type;
        private final List<AbstractButton> choiceButtons = new ArrayList<>();
        private JTextField textAnswer;

        private QuestionResponse(String type) {
            this.type = normalizeQuestionType(type);
        }

        private boolean isMultipleChoice() {
            return "seleccion_multiple".equals(type);
        }

        private boolean isShortAnswer() {
            return "abierta".equals(type);
        }

        private boolean isAnswered() {
            if (isShortAnswer()) {
                return textAnswer != null && !textAnswer.getText().trim().isEmpty();
            }
            return choiceButtons.stream().anyMatch(AbstractButton::isSelected);
        }

        private Set<Long> selectedAnswerIds() {
            Set<Long> selected = new LinkedHashSet<>();
            for (AbstractButton button : choiceButtons) {
                if (button.isSelected()) {
                    selected.add(Long.parseLong(button.getActionCommand()));
                }
            }
            return selected;
        }
    }
}

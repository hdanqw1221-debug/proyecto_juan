package app.ui;

import app.model.Answer;
import app.model.Exam;
import app.model.ExamAttempt;
import app.model.Question;
import app.model.Result;
import app.service.AnswerService;
import app.service.ExamService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.model.Course;
import app.model.User;
import app.service.CertificateService;
import java.io.File;

public class ExamAttemptReviewDialog extends JDialog {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExamAttempt attempt;
    private final Exam exam;
    private final Result result;
    private final ExamService examService;
    private final AnswerService answerService;
    private final Course course;
    private final User student;

    public ExamAttemptReviewDialog(Frame owner, ExamAttempt attempt, Exam exam, Result result,
                                   ExamService examService, AnswerService answerService) {
        this(owner, attempt, exam, result, examService, answerService, null, null);
    }

    public ExamAttemptReviewDialog(Frame owner, ExamAttempt attempt, Exam exam, Result result,
                                   ExamService examService, AnswerService answerService,
                                   Course course, User student) {
        super(owner, "Revision de Evaluacion", true);
        this.attempt = attempt;
        this.exam = exam;
        this.result = result;
        this.examService = examService;
        this.answerService = answerService;
        this.course = course;
        this.student = student;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(850, 650));
        setSize(920, 720);
        setLocationRelativeTo(owner);

        initUi();
    }

    private void initUi() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(UiTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildQuestionsList(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        UiTheme.styleTree(root);
        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel header = UiTheme.card(new BorderLayout(16, 10));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel eyebrow = new JLabel("DETALLE Y REVISION DE INTENTO #" + attempt.getId());
        eyebrow.setForeground(UiTheme.PRIMARY);
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = UiTheme.title(exam != null ? exam.getTitle() : "Evaluacion", 22f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String dates = "Inicio: " + (attempt.getStartAt() != null ? attempt.getStartAt().format(DATE_FORMAT) : "N/D")
                + "  |  Fin: " + (attempt.getEndAt() != null ? attempt.getEndAt().format(DATE_FORMAT) : "En curso");
        JLabel meta = new JLabel(dates);
        meta.setForeground(UiTheme.MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(4));
        copy.add(title);
        copy.add(Box.createVerticalStrut(4));
        copy.add(meta);

        JPanel scoreCard = new JPanel();
        scoreCard.setOpaque(false);
        scoreCard.setLayout(new BoxLayout(scoreCard, BoxLayout.Y_AXIS));

        String scoreText = attempt.getTotalScore() != null
                ? String.format(Locale.ROOT, "%.1f / 5.0", attempt.getTotalScore())
                : "Pendiente";
        JLabel scoreVal = UiTheme.title(scoreText, 24f);
        scoreVal.setForeground(attempt.getTotalScore() != null && attempt.getTotalScore() >= 3.0 ? UiTheme.SUCCESS : UiTheme.PRIMARY);
        scoreVal.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel scoreCaption = new JLabel("Calificacion Final");
        scoreCaption.setForeground(UiTheme.MUTED);
        scoreCaption.setFont(scoreCaption.getFont().deriveFont(Font.BOLD, 11f));
        scoreCaption.setAlignmentX(Component.RIGHT_ALIGNMENT);

        scoreCard.add(scoreCaption);
        scoreCard.add(Box.createVerticalStrut(2));
        scoreCard.add(scoreVal);

        header.add(copy, BorderLayout.CENTER);
        header.add(scoreCard, BorderLayout.EAST);
        return header;
    }

    private JScrollPane buildQuestionsList() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(2, 2, 12, 2));

        List<Question> questions = examService.listQuestionsByExam(attempt.getExamId());
        Map<Long, AttemptItemResult> itemsMap = new HashMap<>();
        if (result != null && result.getDetailJson() != null) {
            for (AttemptItemResult item : parseItems(result.getDetailJson())) {
                itemsMap.put(item.questionId, item);
            }
        }

        if (questions.isEmpty()) {
            JPanel empty = UiTheme.card(new BorderLayout());
            empty.add(new JLabel("No hay preguntas registradas para esta evaluacion."));
            content.add(empty);
        }

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            AttemptItemResult itemRes = itemsMap.get(q.getId());
            List<Answer> answers = answerService.listByQuestion(q.getId());

            JPanel card = UiTheme.card(new BorderLayout(10, 10));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Card Header
            JPanel meta = new JPanel(new BorderLayout());
            meta.setOpaque(false);
            JLabel number = new JLabel("PREGUNTA " + (i + 1) + " DE " + questions.size());
            number.setForeground(UiTheme.PRIMARY);
            number.setFont(number.getFont().deriveFont(Font.BOLD, 11f));

            boolean isCorrect = itemRes != null && itemRes.correct;
            JLabel badge = new JLabel(isCorrect ? "CORRECTA (+ " + q.getScore() + " pts)" : "INCORRECTA (0 pts)");
            badge.setForeground(isCorrect ? UiTheme.SUCCESS : UiTheme.PRIMARY);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11f));

            meta.add(number, BorderLayout.WEST);
            meta.add(badge, BorderLayout.EAST);
            card.add(meta, BorderLayout.NORTH);

            // Card Body
            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

            JLabel statement = new JLabel("<html><div style='width:650px'><b>" + escapeHtml(q.getStatement()) + "</b></div></html>");
            statement.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(statement);

            if (q.getImageData() != null && q.getImageData().length > 0) {
                ImageIcon icon = questionImage(q.getImageData(), 680, 260);
                if (icon != null) {
                    body.add(Box.createVerticalStrut(10));
                    JLabel imgLabel = new JLabel(icon);
                    imgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    imgLabel.setBorder(BorderFactory.createCompoundBorder(
                            UiTheme.roundedBorder(UiTheme.BORDER),
                            BorderFactory.createEmptyBorder(4, 4, 4, 4)
                    ));
                    body.add(imgLabel);
                }
            }

            body.add(Box.createVerticalStrut(10));

            // Options / Answers review
            if ("abierta".equalsIgnoreCase(q.getType())) {
                String answeredText = itemRes != null && itemRes.textAnswer != null ? itemRes.textAnswer : "(Sin respuesta)";
                JLabel answeredLabel = new JLabel("<html><b>Respuesta ingresada:</b> <i>" + escapeHtml(answeredText) + "</i></html>");
                answeredLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(answeredLabel);

                body.add(Box.createVerticalStrut(4));
                String expected = answers.stream().filter(Answer::isCorrect).map(Answer::getContent).findFirst().orElse("");
                JLabel expectedLabel = new JLabel("<html><b>Respuesta esperada:</b> <span style='color:#2e7d32'>" + escapeHtml(expected) + "</span></html>");
                expectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(expectedLabel);
            } else {
                for (Answer a : answers) {
                    boolean wasSelected = itemRes != null && itemRes.selectedAnswerIds.contains(a.getId());
                    boolean wasCorrect = a.isCorrect();

                    StringBuilder line = new StringBuilder();
                    line.append("<html><div style='width:620px'>");
                    if (wasSelected && wasCorrect) {
                        line.append("<b style='color:#2e7d32'>[x] ").append(escapeHtml(a.getContent())).append(" (Marcada - Correcta)</b>");
                    } else if (wasSelected && !wasCorrect) {
                        line.append("<b style='color:#c62828'>[x] ").append(escapeHtml(a.getContent())).append(" (Marcada - Incorrecta)</b>");
                    } else if (!wasSelected && wasCorrect) {
                        line.append("<span style='color:#2e7d32'>[ ] ").append(escapeHtml(a.getContent())).append(" (Respuesta correcta)</span>");
                    } else {
                        line.append("<span style='color:#757575'>[ ] ").append(escapeHtml(a.getContent())).append("</span>");
                    }
                    line.append("</div></html>");

                    JLabel optLabel = new JLabel(line.toString());
                    optLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    optLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));
                    body.add(optLabel);
                }
            }

            card.add(body, BorderLayout.CENTER);
            content.add(card);
            content.add(Box.createVerticalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        footer.setOpaque(false);

        if (attempt != null && attempt.getTotalScore() != null && attempt.getTotalScore() >= CertificateService.PASSING_SCORE) {
            JButton certBtn = new JButton("Descargar Certificado");
            UiTheme.styleButton(certBtn);
            certBtn.addActionListener(e -> {
                try {
                    CertificateService certService = new CertificateService();
                    User certStudent = student;
                    if (certStudent == null) {
                        certStudent = new User();
                        certStudent.setId(attempt.getStudentId());
                        certStudent.setUsername("Estudiante #" + attempt.getStudentId());
                    }
                    Course certCourse = course;
                    if (certCourse == null && exam != null) {
                        certCourse = new Course();
                        certCourse.setId(exam.getCourseId());
                        certCourse.setName("Asignatura #" + exam.getCourseId());
                    }
                    File generated = certService.exportAndOpenCertificate(certStudent, certCourse, exam, attempt);
                    JOptionPane.showMessageDialog(this,
                            "Certificado generado exitosamente:\n" + generated.getAbsolutePath() + "\n\nSe ha abierto en tu navegador web.",
                            "Certificado de Aprobacion",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error al generar certificado: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            footer.add(certBtn);
        }

        JButton close = new JButton("Cerrar Revision");
        UiTheme.styleSecondaryButton(close);
        close.addActionListener(e -> dispose());
        footer.add(close);
        return footer;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private ImageIcon questionImage(byte[] data, int maxWidth, int maxHeight) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) return null;
            double scale = Math.min(1.0, Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight()));
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    public static class AttemptItemResult {
        public long questionId;
        public String type = "";
        public Set<Long> selectedAnswerIds = new HashSet<>();
        public String textAnswer = "";
        public boolean correct;
        public double score;
    }

    public static List<AttemptItemResult> parseItems(String json) {
        List<AttemptItemResult> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;

        Pattern itemPattern = Pattern.compile("\\{([^{}]+)\\}");
        Matcher m = itemPattern.matcher(json);
        while (m.find()) {
            String block = m.group(1);
            if (!block.contains("questionId")) continue;

            AttemptItemResult item = new AttemptItemResult();
            Matcher qm = Pattern.compile("\"questionId\"\\s*:\\s*(\\d+)").matcher(block);
            if (qm.find()) item.questionId = Long.parseLong(qm.group(1));

            Matcher tm = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]*)\"").matcher(block);
            if (tm.find()) item.type = tm.group(1);

            Matcher corrM = Pattern.compile("\"correct\"\\s*:\\s*(true|false)").matcher(block);
            if (corrM.find()) item.correct = Boolean.parseBoolean(corrM.group(1));

            Matcher scoreM = Pattern.compile("\"score\"\\s*:\\s*([0-9.]+)").matcher(block);
            if (scoreM.find()) item.score = Double.parseDouble(scoreM.group(1));

            Matcher txtM = Pattern.compile("\"textAnswer\"\\s*:\\s*\"([^\"]*)\"").matcher(block);
            if (txtM.find()) item.textAnswer = txtM.group(1);

            Matcher idsM = Pattern.compile("\"selectedAnswerIds\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(block);
            if (idsM.find()) {
                String idsStr = idsM.group(1).trim();
                if (!idsStr.isEmpty()) {
                    for (String part : idsStr.split(",")) {
                        try {
                            item.selectedAnswerIds.add(Long.parseLong(part.trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                Matcher singleIdM = Pattern.compile("\"selectedAnswerId\"\\s*:\\s*(\\d+)").matcher(block);
                if (singleIdM.find()) {
                    long id = Long.parseLong(singleIdM.group(1));
                    if (id > 0) item.selectedAnswerIds.add(id);
                }
            }

            list.add(item);
        }
        return list;
    }
}

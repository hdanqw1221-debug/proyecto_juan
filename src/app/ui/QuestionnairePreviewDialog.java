package app.ui;

import app.model.Answer;
import app.model.Exam;
import app.model.Question;
import app.service.AnswerService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;

public class QuestionnairePreviewDialog extends JDialog {
    private final Exam exam;
    private final List<Question> questions;
    private final AnswerService answerService;

    public QuestionnairePreviewDialog(Frame owner, Exam exam, List<Question> questions,
                                      AnswerService answerService) {
        super(owner, "Vista previa del cuestionario", true);
        this.exam = exam;
        this.questions = questions;
        this.answerService = answerService;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(800, 600));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1040, screen.width - 70), Math.min(780, screen.height - 70));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(UiTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildQuestions(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        UiTheme.styleTree(root);
        setContentPane(root);
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader() {
        JPanel header = UiTheme.card(new BorderLayout(16, 8));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, UiTheme.INFO),
                BorderFactory.createEmptyBorder(15, 18, 15, 18)
        ));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("VISTA PREVIA DOCENTE");
        eyebrow.setForeground(UiTheme.INFO);
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(4));
        copy.add(UiTheme.title(exam.getTitle(), 24f));
        if (exam.getDescription() != null && !exam.getDescription().isBlank()) {
            JLabel description = new JLabel("<html><div style='width:620px'>"
                    + escapeHtml(exam.getDescription()) + "</div></html>");
            description.setForeground(UiTheme.MUTED);
            copy.add(Box.createVerticalStrut(4));
            copy.add(description);
        }

        double points = questions.stream().mapToDouble(Question::getScore).sum();
        JLabel summary = new JLabel("<html><b>" + questions.size() + " preguntas</b><br>"
                + formatPoints(points) + " puntos totales</html>");
        summary.setForeground(UiTheme.PRIMARY_DARK);
        header.add(copy, BorderLayout.CENTER);
        header.add(summary, BorderLayout.EAST);
        return header;
    }

    private JScrollPane buildQuestions() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(1, 1, 12, 1));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        if (questions.isEmpty()) {
            JPanel empty = UiTheme.card(new BorderLayout());
            empty.add(UiTheme.title("Este cuestionario aun no tiene preguntas", 19f), BorderLayout.NORTH);
            JLabel help = new JLabel("Abre Editar cuestionario para agregar contenido.");
            help.setForeground(UiTheme.MUTED);
            empty.add(help, BorderLayout.CENTER);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(empty);
        }

        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            List<Answer> answers = answerService.listByQuestion(question.getId());
            JPanel card = UiTheme.card(new BorderLayout(10, 10));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel meta = new JPanel(new BorderLayout());
            meta.setOpaque(false);
            JLabel number = new JLabel("PREGUNTA " + (index + 1));
            number.setForeground(UiTheme.PRIMARY);
            number.setFont(number.getFont().deriveFont(Font.BOLD, 11f));
            JLabel type = new JLabel(typeLabel(question.getType()) + "  |  "
                    + formatPoints(question.getScore()) + " punto(s)");
            type.setForeground(UiTheme.MUTED);
            type.setFont(type.getFont().deriveFont(Font.BOLD, 11f));
            meta.add(number, BorderLayout.WEST);
            meta.add(type, BorderLayout.EAST);
            card.add(meta, BorderLayout.NORTH);

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            JLabel statement = new JLabel("<html><div style='width:720px'><b>"
                    + escapeHtml(question.getStatement()) + "</b></div></html>");
            statement.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(statement);

            if (question.getImageData() != null && question.getImageData().length > 0) {
                ImageIcon icon = scaledIcon(question.getImageData(), 720, 330);
                if (icon != null) {
                    body.add(Box.createVerticalStrut(12));
                    JLabel image = new JLabel(icon);
                    image.setAlignmentX(Component.LEFT_ALIGNMENT);
                    image.setBorder(BorderFactory.createCompoundBorder(
                            UiTheme.roundedBorder(UiTheme.BORDER),
                            BorderFactory.createEmptyBorder(6, 6, 6, 6)
                    ));
                    body.add(image);
                }
            }

            body.add(Box.createVerticalStrut(12));
            if (answers.isEmpty()) {
                JLabel warning = new JLabel("Sin respuestas configuradas");
                warning.setForeground(UiTheme.WARNING);
                warning.setFont(warning.getFont().deriveFont(Font.BOLD));
                body.add(warning);
            } else if ("abierta".equalsIgnoreCase(question.getType())) {
                JLabel label = new JLabel("Respuesta(s) esperada(s):");
                label.setForeground(UiTheme.MUTED);
                body.add(label);
                for (Answer answer : answers) {
                    if (answer.isCorrect()) {
                        body.add(correctAnswerRow(answer.getContent(), true));
                    }
                }
            } else {
                boolean multiple = "seleccion_multiple".equalsIgnoreCase(question.getType());
                for (Answer answer : answers) {
                    JPanel row = new JPanel(new BorderLayout(8, 0));
                    row.setOpaque(false);
                    AbstractButton option = multiple
                            ? new JCheckBox(answer.getContent())
                            : new JRadioButton(answer.getContent());
                    option.setSelected(answer.isCorrect());
                    option.setEnabled(false);
                    option.setOpaque(false);
                    row.add(option, BorderLayout.CENTER);
                    if (answer.isCorrect()) {
                        row.add(correctBadge(), BorderLayout.EAST);
                    }
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
                    body.add(row);
                    body.add(Box.createVerticalStrut(4));
                }
            }
            card.add(body, BorderLayout.CENTER);
            content.add(card);
            content.add(Box.createVerticalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JPanel correctAnswerRow(String text, boolean correct) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel answer = new JLabel(text);
        answer.setForeground(UiTheme.INK);
        row.add(answer, BorderLayout.CENTER);
        if (correct) {
            row.add(correctBadge(), BorderLayout.EAST);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        return row;
    }

    private JLabel correctBadge() {
        JLabel badge = new JLabel("CORRECTA");
        badge.setOpaque(true);
        badge.setBackground(UiTheme.SUCCESS_SOFT);
        badge.setForeground(UiTheme.SUCCESS);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
        badge.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        return badge;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel hint = new JLabel("Esta vista no crea intentos ni modifica calificaciones.");
        hint.setForeground(UiTheme.MUTED);
        JButton close = new JButton("Cerrar vista previa");
        UiTheme.styleSecondaryButton(close);
        close.addActionListener(e -> dispose());
        footer.add(hint, BorderLayout.WEST);
        footer.add(close, BorderLayout.EAST);
        return footer;
    }

    private ImageIcon scaledIcon(byte[] data, int maxWidth, int maxHeight) {
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

    private String typeLabel(String type) {
        if ("seleccion_multiple".equalsIgnoreCase(type)) {
            return "SELECCION MULTIPLE";
        }
        if ("verdadero_falso".equalsIgnoreCase(type)) {
            return "VERDADERO / FALSO";
        }
        if ("abierta".equalsIgnoreCase(type)) {
            return "RESPUESTA CORTA";
        }
        return "SELECCION UNICA";
    }

    private String formatPoints(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

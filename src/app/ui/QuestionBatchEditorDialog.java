package app.ui;

import app.model.Answer;
import app.model.QuestionDraft;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class QuestionBatchEditorDialog extends JDialog {
    private static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_IMAGE_WIDTH = 1600;
    private static final int MAX_IMAGE_HEIGHT = 1200;

    private final DefaultListModel<DraftState> draftModel = new DefaultListModel<>();
    private final JList<DraftState> draftList = new JList<>(draftModel);
    private final JTextArea statement = new JTextArea(5, 34);
    private final JComboBox<QuestionType> type = new JComboBox<>(QuestionType.values());
    private final JSpinner score = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 100.0, 0.5));
    /** Estructura del modelo 3D a la que se refiere la pregunta (opcional, C07). */
    private final JComboBox<Object> modelObject = new JComboBox<>(new Object[]{NO_MODEL_OBJECT});
    private static final String NO_MODEL_OBJECT = "(sin estructura 3D)";
    private final JPanel optionsPanel = new JPanel();
    private final JPanel previewBody = new JPanel();
    private final JLabel editorTitle = UiTheme.title("Pregunta 1", 20f);
    private final JLabel statementCount = new JLabel("0 caracteres");
    private final JLabel batchSummary = new JLabel();
    private final JLabel optionsTitle = UiTheme.title("Opciones de respuesta", 17f);
    private final JLabel optionsHint = new JLabel();
    private final JButton addOption = new JButton("+ Agregar opcion");
    private final JLabel imagePreview = new JLabel("Sin imagen", SwingConstants.CENTER);
    private final JLabel imageInfo = new JLabel("PNG o JPG, maximo 6 MB");
    private final JButton chooseImageButton = new JButton("Seleccionar imagen");
    private final JButton removeImageButton = new JButton("Quitar");
    private final List<OptionRow> optionRows = new ArrayList<>();

    private ButtonGroup correctGroup = new ButtonGroup();
    private DraftState current;
    private boolean loading;
    private List<QuestionDraft> result;

    public QuestionBatchEditorDialog(Frame owner, String examLabel) {
        this(owner, examLabel, List.of());
    }

    public QuestionBatchEditorDialog(Frame owner, String examLabel, List<QuestionDraft> existingQuestions) {
        super(owner, "Constructor de cuestionario", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 700));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1380, screen.width - 50), Math.min(860, screen.height - 50));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(UiTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        root.add(buildHeader(examLabel), BorderLayout.NORTH);
        root.add(buildWorkspace(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
        installKeyboardShortcuts(root);

        installListeners();
        UiTheme.styleTree(root);
        if (existingQuestions == null || existingQuestions.isEmpty()) {
            addQuestion();
        } else {
            loading = true;
            for (QuestionDraft draft : existingQuestions) {
                draftModel.addElement(new DraftState(draft));
            }
            draftList.setSelectedIndex(0);
            current = draftModel.get(0);
            loading = false;
            loadCurrent();
        }
        setLocationRelativeTo(owner);
    }

    public List<QuestionDraft> getQuestions() {
        return result;
    }

    private JPanel buildHeader(String examLabel) {
        JPanel header = new BuilderHeaderPanel(new BorderLayout(18, 4));
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JPanel copy = verticalPanel();
        JLabel eyebrow = new JLabel("CONSTRUCTOR DE CUESTIONARIOS");
        eyebrow.setForeground(new Color(244, 202, 132));
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(3));
        JLabel title = UiTheme.title("Crea varias preguntas en una sola sesion", 23f);
        title.setForeground(Color.WHITE);
        copy.add(title);

        JPanel meta = verticalPanel();
        JLabel exam = new JLabel("Examen: " + examLabel);
        exam.setForeground(Color.WHITE);
        exam.setFont(exam.getFont().deriveFont(Font.BOLD));
        batchSummary.setForeground(new Color(244, 220, 184));
        batchSummary.setHorizontalAlignment(SwingConstants.RIGHT);
        meta.add(exam);
        meta.add(Box.createVerticalStrut(3));
        meta.add(batchSummary);
        header.add(copy, BorderLayout.CENTER);
        header.add(meta, BorderLayout.EAST);
        return header;
    }

    private JSplitPane buildWorkspace() {
        JSplitPane editorPreview = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, buildEditor(), buildPreview()
        );
        editorPreview.setBorder(null);
        editorPreview.setResizeWeight(0.66);
        editorPreview.setDividerSize(4);
        editorPreview.setContinuousLayout(true);
        editorPreview.setDividerLocation(660);

        JSplitPane workspace = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, buildQuestionNavigator(), editorPreview
        );
        workspace.setBorder(null);
        workspace.setResizeWeight(0.21);
        workspace.setDividerSize(4);
        workspace.setContinuousLayout(true);
        workspace.setDividerLocation(270);
        return workspace;
    }

    private JPanel buildQuestionNavigator() {
        JPanel card = UiTheme.card(new BorderLayout(8, 10));
        card.setMinimumSize(new Dimension(240, 400));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel copy = verticalPanel();
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel navigatorTitle = UiTheme.title("Preguntas", 18f);
        navigatorTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(navigatorTitle);
        JLabel help = new JLabel("ORDEN DEL CUESTIONARIO");
        help.setForeground(UiTheme.MUTED);
        help.setFont(help.getFont().deriveFont(Font.BOLD, 9f));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.add(Box.createVerticalStrut(2));
        copy.add(help);
        header.add(copy);
        header.add(Box.createVerticalStrut(9));
        JButton add = new JButton("+ Nueva pregunta");
        UiTheme.styleButton(add);
        add.addActionListener(e -> addQuestion());
        add.setAlignmentX(Component.LEFT_ALIGNMENT);
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        header.add(add);
        header.add(Box.createVerticalStrut(2));

        draftList.setCellRenderer(new DraftRenderer());
        draftList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        draftList.setFixedCellHeight(66);
        draftList.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        draftList.setBackground(UiTheme.SURFACE);
        JScrollPane scroll = new JScrollPane(draftList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel actions = new JPanel(new GridLayout(2, 2, 6, 6));
        actions.setOpaque(false);
        JButton duplicate = new JButton("Duplicar");
        JButton up = new JButton("Subir");
        JButton down = new JButton("Bajar");
        JButton remove = new JButton("Eliminar");
        UiTheme.styleSecondaryButton(duplicate);
        UiTheme.styleSecondaryButton(up);
        UiTheme.styleSecondaryButton(down);
        UiTheme.styleSecondaryButton(remove);
        duplicate.addActionListener(e -> duplicateQuestion());
        up.addActionListener(e -> moveQuestion(-1));
        down.addActionListener(e -> moveQuestion(1));
        remove.addActionListener(e -> removeQuestion());
        actions.add(duplicate);
        actions.add(remove);
        actions.add(up);
        actions.add(down);

        card.add(header, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JScrollPane buildEditor() {
        JPanel editor = new JPanel();
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
        editor.add(buildQuestionCard());
        editor.add(Box.createVerticalStrut(12));
        editor.add(buildOptionsCard());

        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setMinimumSize(new Dimension(510, 400));
        return scroll;
    }

    private JPanel buildQuestionCard() {
        JPanel card = UiTheme.card(new GridBagLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, UiTheme.INFO),
                BorderFactory.createEmptyBorder(17, 18, 17, 18)
        ));
        GridBagConstraints gbc = constraints();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        card.add(editorTitle, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 5, 0);
        JPanel statementHeader = new JPanel(new BorderLayout());
        statementHeader.setOpaque(false);
        statementHeader.add(fieldLabel("Enunciado"), BorderLayout.WEST);
        statementCount.setForeground(UiTheme.MUTED);
        statementCount.setFont(statementCount.getFont().deriveFont(11f));
        statementHeader.add(statementCount, BorderLayout.EAST);
        card.add(statementHeader, gbc);
        statement.setLineWrap(true);
        statement.setWrapStyleWord(true);
        JScrollPane statementScroll = new JScrollPane(statement);
        statementScroll.setPreferredSize(new Dimension(510, 88));
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        gbc.fill = GridBagConstraints.BOTH;
        card.add(statementScroll, gbc);

        gbc.gridy++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 5, 0);
        card.add(fieldLabel("Apoyo visual (opcional)"), gbc);
        gbc.gridy++;
        card.add(buildImagePicker(), gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.weightx = 0.72;
        gbc.insets = new Insets(13, 0, 4, 8);
        card.add(fieldLabel("Tipo de pregunta"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.28;
        gbc.insets = new Insets(13, 8, 4, 0);
        card.add(fieldLabel("Puntaje"), gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 0, 8);
        card.add(type, gbc);
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 8, 0, 0);
        card.add(score, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(13, 0, 4, 0);
        card.add(fieldLabel("Estructura del modelo 3D (opcional)"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(modelObject, gbc);
        return card;
    }

    private JPanel buildImagePicker() {
        JPanel media = new JPanel(new BorderLayout(12, 0));
        media.setOpaque(false);
        imagePreview.setPreferredSize(new Dimension(170, 92));
        imagePreview.setBorder(BorderFactory.createCompoundBorder(
                UiTheme.roundedBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        imagePreview.setForeground(UiTheme.MUTED);

        JPanel controls = new JPanel(new BorderLayout(0, 7));
        controls.setOpaque(false);
        imageInfo.setForeground(UiTheme.MUTED);
        UiTheme.styleButton(chooseImageButton);
        UiTheme.styleSecondaryButton(removeImageButton);
        chooseImageButton.addActionListener(e -> chooseImage());
        removeImageButton.addActionListener(e -> removeImage());
        JPanel imageActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        imageActions.setOpaque(false);
        imageActions.add(chooseImageButton);
        imageActions.add(removeImageButton);
        controls.add(imageInfo, BorderLayout.NORTH);
        controls.add(imageActions, BorderLayout.CENTER);

        media.add(imagePreview, BorderLayout.WEST);
        media.add(controls, BorderLayout.CENTER);
        return media;
    }

    private JPanel buildOptionsCard() {
        JPanel card = UiTheme.card(new BorderLayout(8, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, UiTheme.SUCCESS),
                BorderFactory.createEmptyBorder(17, 18, 17, 18)
        ));
        JPanel header = new JPanel(new BorderLayout(8, 2));
        header.setOpaque(false);
        JPanel copy = verticalPanel();
        copy.add(optionsTitle);
        optionsHint.setForeground(UiTheme.MUTED);
        copy.add(Box.createVerticalStrut(2));
        copy.add(optionsHint);
        header.add(copy, BorderLayout.CENTER);
        header.add(addOption, BorderLayout.EAST);

        optionsPanel.setOpaque(false);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        card.add(header, BorderLayout.NORTH);
        card.add(optionsPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPreview() {
        JPanel preview = UiTheme.card(new BorderLayout(8, 12));
        preview.setMinimumSize(new Dimension(295, 400));
        preview.setBackground(new Color(252, 250, 246));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UiTheme.title("Vista previa", 18f), BorderLayout.WEST);
        JLabel badge = new JLabel("ESTUDIANTE");
        badge.setForeground(UiTheme.INFO);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
        header.add(badge, BorderLayout.EAST);

        previewBody.setOpaque(false);
        previewBody.setBorder(BorderFactory.createEmptyBorder(4, 2, 8, 4));
        previewBody.setLayout(new BoxLayout(previewBody, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(previewBody);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        preview.add(header, BorderLayout.NORTH);
        preview.add(scroll, BorderLayout.CENTER);
        return preview;
    }

    private JPanel buildFooter() {
        JPanel footer = UiTheme.card(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 13));
        JLabel hint = new JLabel("Ctrl+N: nueva pregunta   |   Ctrl+Enter: guardar cuestionario");
        hint.setForeground(UiTheme.MUTED);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancelar");
        JButton save = new JButton("Guardar cuestionario");
        UiTheme.styleSecondaryButton(cancel);
        UiTheme.styleButton(save);
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> saveAll());
        buttons.add(cancel);
        buttons.add(save);
        footer.add(hint, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void installListeners() {
        draftList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !loading) {
                saveCurrentFields();
                current = draftList.getSelectedValue();
                loadCurrent();
            }
        });
        statement.getDocument().addDocumentListener(documentListener(this::editorChanged));
        score.addChangeListener(e -> editorChanged());
        modelObject.addActionListener(e -> editorChanged());
        type.addActionListener(e -> {
            if (loading || current == null) {
                return;
            }
            QuestionType selected = selectedType();
            if (current.type != selected) {
                current.type = selected;
                current.options = defaultOptions(selected);
                rebuildOptions();
                editorChanged();
            }
        });
        addOption.addActionListener(e -> {
            if (current != null) {
                current.options.add(new OptionState("", false));
                rebuildOptions();
                editorChanged();
            }
        });
    }

    private void installKeyboardShortcuts(JComponent root) {
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "newQuestion");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "saveQuestionnaire");
        actionMap.put("newQuestion", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                addQuestion();
            }
        });
        actionMap.put("saveQuestionnaire", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveAll();
            }
        });
    }

    private void addQuestion() {
        saveCurrentFields();
        DraftState draft = new DraftState();
        draftModel.addElement(draft);
        draftList.setSelectedIndex(draftModel.size() - 1);
        refreshSummary();
    }

    private void duplicateQuestion() {
        if (current == null) {
            return;
        }
        saveCurrentFields();
        int index = draftList.getSelectedIndex();
        DraftState copy = current.copy();
        draftModel.add(index + 1, copy);
        draftList.setSelectedIndex(index + 1);
        refreshSummary();
    }

    private void removeQuestion() {
        if (draftModel.size() <= 1) {
            JOptionPane.showMessageDialog(this, "El cuestionario debe conservar al menos una pregunta.");
            return;
        }
        int index = draftList.getSelectedIndex();
        int answer = JOptionPane.showConfirmDialog(
                this, "¿Eliminar esta pregunta del borrador?", "Eliminar pregunta",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        loading = true;
        draftModel.remove(index);
        int next = Math.min(index, draftModel.size() - 1);
        draftList.setSelectedIndex(next);
        current = draftModel.get(next);
        loading = false;
        loadCurrent();
        refreshSummary();
    }

    private void moveQuestion(int direction) {
        int index = draftList.getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= draftModel.size()) {
            return;
        }
        saveCurrentFields();
        loading = true;
        DraftState moving = draftModel.remove(index);
        draftModel.add(target, moving);
        draftList.setSelectedIndex(target);
        current = moving;
        loading = false;
        loadCurrent();
    }

    private void loadCurrent() {
        if (current == null) {
            return;
        }
        loading = true;
        int index = draftList.getSelectedIndex();
        editorTitle.setText("Pregunta " + (index + 1));
        statement.setText(current.statement);
        statementCount.setText(current.statement.length() + " caracteres");
        type.setSelectedItem(current.type);
        score.setValue(current.score);
        selectModelObject(current.objectId);
        updateImagePreview();
        rebuildOptions();
        loading = false;
        refreshPreview();
        refreshSummary();
    }

    private void saveCurrentFields() {
        if (current == null || loading) {
            return;
        }
        current.statement = statement.getText().trim();
        Object scoreValue = score.getValue();
        current.score = scoreValue instanceof Number number
                ? number.doubleValue()
                : Double.parseDouble(String.valueOf(scoreValue).trim());
        current.type = selectedType();
        current.objectId = modelObject.getSelectedItem() instanceof app.service.ExamService.ModelObject o ? o.objectId() : null;
    }

    /** Carga las estructuras del modelo 3D que se pueden enlazar a las preguntas. */
    public void setModelObjects(List<app.service.ExamService.ModelObject> objects) {
        boolean wasLoading = loading;
        loading = true;
        modelObject.removeAllItems();
        modelObject.addItem(NO_MODEL_OBJECT);
        for (var o : objects) {
            modelObject.addItem(o);
        }
        loading = wasLoading;
        if (current != null) {
            selectModelObject(current.objectId);
        }
    }

    private void selectModelObject(String objectId) {
        boolean wasLoading = loading;
        loading = true;
        Object selected = NO_MODEL_OBJECT;
        for (int i = 0; i < modelObject.getItemCount(); i++) {
            if (modelObject.getItemAt(i) instanceof app.service.ExamService.ModelObject o && o.objectId().equals(objectId)) {
                selected = o;
                break;
            }
        }
        modelObject.setSelectedItem(selected);
        loading = wasLoading;
    }

    private void editorChanged() {
        if (loading || current == null) {
            return;
        }
        saveCurrentFields();
        statementCount.setText(statement.getText().length() + " caracteres");
        draftList.repaint();
        refreshPreview();
        refreshSummary();
    }

    private void rebuildOptions() {
        if (current == null) {
            return;
        }
        optionRows.clear();
        correctGroup = new ButtonGroup();
        optionsPanel.removeAll();
        optionsTitle.setText(current.type == QuestionType.SHORT_ANSWER
                ? "Respuesta esperada" : "Opciones de respuesta");
        optionsHint.setText(current.type.hint);
        addOption.setVisible(current.type.allowsAdding);
        for (int i = 0; i < current.options.size(); i++) {
            OptionRow row = new OptionRow(current.options.get(i), i);
            optionRows.add(row);
            optionsPanel.add(row.panel);
            optionsPanel.add(Box.createVerticalStrut(7));
        }
        optionsPanel.revalidate();
        optionsPanel.repaint();
        refreshPreview();
    }

    private void removeOption(OptionState option) {
        if (current.options.size() <= 2) {
            JOptionPane.showMessageDialog(this, "Se necesitan al menos dos opciones.");
            return;
        }
        current.options.remove(option);
        rebuildOptions();
        editorChanged();
    }

    private void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar imagen para la pregunta");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Imagenes PNG o JPG", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            PreparedImage image = prepareImage(chooser.getSelectedFile());
            current.imageData = image.data;
            current.imageName = image.name;
            current.imageContentType = image.contentType;
            updateImagePreview();
            draftList.repaint();
            refreshPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this, "No se pudo cargar la imagen: " + ex.getMessage(),
                    "Imagen no valida", JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void removeImage() {
        if (current == null) {
            return;
        }
        current.imageData = null;
        current.imageName = null;
        current.imageContentType = null;
        updateImagePreview();
        draftList.repaint();
        refreshPreview();
    }

    private PreparedImage prepareImage(File file) throws Exception {
        if (!Files.isRegularFile(file.toPath())) {
            throw new IllegalArgumentException("El archivo no existe.");
        }
        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IllegalArgumentException("Selecciona un archivo PNG o JPG valido.");
        }
        double scale = Math.min(1.0, Math.min(
                (double) MAX_IMAGE_WIDTH / source.getWidth(),
                (double) MAX_IMAGE_HEIGHT / source.getHeight()
        ));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage output = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = output.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (!alpha) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
        }
        g2.drawImage(source, 0, 0, width, height, null);
        g2.dispose();

        String format = alpha ? "png" : "jpg";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(output, format, buffer);
        byte[] data = buffer.toByteArray();
        if (data.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("La imagen optimizada supera 6 MB.");
        }
        String original = file.getName();
        String name = original.length() > 255 ? original.substring(original.length() - 255) : original;
        return new PreparedImage(data, name, alpha ? "image/png" : "image/jpeg");
    }

    private void updateImagePreview() {
        if (current == null || current.imageData == null) {
            imagePreview.setIcon(null);
            imagePreview.setText("Sin imagen");
            imageInfo.setText("PNG o JPG, maximo 6 MB");
            imageInfo.setToolTipText(null);
            removeImageButton.setEnabled(false);
            return;
        }
        imagePreview.setText("");
        imagePreview.setIcon(scaledIcon(current.imageData, 156, 78));
        String name = current.imageName == null ? "Imagen" : current.imageName;
        String shortName = name.length() > 28 ? name.substring(0, 25) + "..." : name;
        imageInfo.setText(shortName + "  |  " + formatBytes(current.imageData.length));
        imageInfo.setToolTipText(name);
        removeImageButton.setEnabled(true);
    }

    private void refreshPreview() {
        if (current == null) {
            return;
        }
        previewBody.removeAll();
        JLabel meta = new JLabel(current.type.label + "  |  " + score.getValue() + " punto(s)");
        meta.setForeground(UiTheme.PRIMARY);
        meta.setFont(meta.getFont().deriveFont(Font.BOLD, 11f));
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewBody.add(meta);
        previewBody.add(Box.createVerticalStrut(12));

        String prompt = statement.getText().trim();
        JLabel promptLabel = new JLabel("<html><div style='width:290px'><b>"
                + escapeHtml(prompt.isEmpty() ? "Escribe el enunciado de la pregunta..." : prompt)
                + "</b></div></html>");
        promptLabel.setForeground(prompt.isEmpty() ? UiTheme.MUTED : UiTheme.INK);
        promptLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewBody.add(promptLabel);

        if (current.imageData != null) {
            previewBody.add(Box.createVerticalStrut(12));
            JLabel image = new JLabel(scaledIcon(current.imageData, 300, 220));
            image.setAlignmentX(Component.LEFT_ALIGNMENT);
            image.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
            previewBody.add(image);
        }
        previewBody.add(Box.createVerticalStrut(14));

        if (current.type == QuestionType.SHORT_ANSWER) {
            JTextField sample = new JTextField("Respuesta del estudiante");
            sample.setEnabled(false);
            sample.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            sample.setAlignmentX(Component.LEFT_ALIGNMENT);
            previewBody.add(sample);
        } else {
            for (int i = 0; i < current.options.size(); i++) {
                String value = current.options.get(i).text.trim();
                AbstractButton sample = current.type == QuestionType.MULTIPLE_CHOICE
                        ? new JCheckBox(value.isEmpty() ? "Opcion " + (i + 1) : value)
                        : new JRadioButton(value.isEmpty() ? "Opcion " + (i + 1) : value);
                sample.setEnabled(false);
                sample.setOpaque(false);
                sample.setAlignmentX(Component.LEFT_ALIGNMENT);
                previewBody.add(sample);
                previewBody.add(Box.createVerticalStrut(5));
            }
        }
        previewBody.revalidate();
        previewBody.repaint();
    }

    private void refreshSummary() {
        double total = 0;
        for (int i = 0; i < draftModel.size(); i++) {
            total += draftModel.get(i).score;
        }
        batchSummary.setText(draftModel.size() + " pregunta(s)  |  " + formatPoints(total) + " puntos");
    }

    private void saveAll() {
        saveCurrentFields();
        for (int i = 0; i < draftModel.size(); i++) {
            String error = validateDraft(draftModel.get(i));
            if (error != null) {
                draftList.setSelectedIndex(i);
                JOptionPane.showMessageDialog(
                        this, "Pregunta " + (i + 1) + ": " + error,
                        "Revisa el cuestionario", JOptionPane.WARNING_MESSAGE
                );
                return;
            }
        }

        List<QuestionDraft> questions = new ArrayList<>();
        for (int i = 0; i < draftModel.size(); i++) {
            questions.add(toQuestionDraft(draftModel.get(i)));
        }
        result = List.copyOf(questions);
        dispose();
    }

    private String validateDraft(DraftState draft) {
        if (draft.statement == null || draft.statement.trim().length() < 5) {
            return "escribe un enunciado de al menos 5 caracteres.";
        }
        if (draft.score <= 0) {
            return "el puntaje debe ser mayor que cero.";
        }
        Set<String> unique = new HashSet<>();
        int correct = 0;
        for (OptionState option : draft.options) {
            String value = option.text.trim();
            if (value.isEmpty()) {
                return draft.type == QuestionType.SHORT_ANSWER
                        ? "escribe la respuesta esperada." : "completa todas las opciones.";
            }
            if (!unique.add(value.toLowerCase(Locale.ROOT))) {
                return "las opciones no pueden estar repetidas.";
            }
            if (draft.type == QuestionType.SHORT_ANSWER || option.correct) {
                correct++;
            }
        }
        if (draft.type != QuestionType.SHORT_ANSWER && correct == 0) {
            return "marca al menos una respuesta correcta.";
        }
        if (draft.type != QuestionType.MULTIPLE_CHOICE && correct != 1) {
            return "debe existir una sola respuesta correcta.";
        }
        return null;
    }

    private QuestionDraft toQuestionDraft(DraftState state) {
        QuestionDraft draft = new QuestionDraft();
        draft.setId(state.id);
        draft.setStatement(state.statement.trim());
        draft.setType(state.type.code);
        draft.setScore(state.score);
        List<Answer> answers = new ArrayList<>();
        for (OptionState option : state.options) {
            Answer answer = new Answer();
            answer.setContent(option.text.trim());
            answer.setCorrect(state.type == QuestionType.SHORT_ANSWER || option.correct);
            answers.add(answer);
        }
        draft.setAnswers(answers);
        draft.setImageData(state.imageData);
        draft.setImageName(state.imageName);
        draft.setImageContentType(state.imageContentType);
        draft.setObjectId(state.objectId);
        return draft;
    }

    private List<OptionState> defaultOptions(QuestionType selected) {
        List<OptionState> options = new ArrayList<>();
        if (selected == QuestionType.TRUE_FALSE) {
            options.add(new OptionState("Verdadero", true));
            options.add(new OptionState("Falso", false));
        } else if (selected == QuestionType.SHORT_ANSWER) {
            options.add(new OptionState("", true));
        } else {
            options.add(new OptionState("", true));
            options.add(new OptionState("", false));
            options.add(new OptionState("", false));
        }
        return options;
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

    private JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private GridBagConstraints constraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        return gbc;
    }

    private QuestionType selectedType() {
        QuestionType selected = (QuestionType) type.getSelectedItem();
        return selected == null ? QuestionType.SINGLE_CHOICE : selected;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String formatBytes(int bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String formatPoints(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private DocumentListener documentListener(Runnable callback) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { callback.run(); }
            public void removeUpdate(DocumentEvent e) { callback.run(); }
            public void changedUpdate(DocumentEvent e) { callback.run(); }
        };
    }

    private final class OptionRow {
        private final JPanel panel = new JPanel(new BorderLayout(8, 0));

        private OptionRow(OptionState option, int index) {
            AbstractButton correct = current.type == QuestionType.MULTIPLE_CHOICE
                    ? new JCheckBox("Correcta") : new JRadioButton("Correcta");
            if (correct instanceof JRadioButton) {
                correctGroup.add(correct);
            }
            correct.setSelected(option.correct);
            correct.setVisible(current.type != QuestionType.SHORT_ANSWER);
            correct.addActionListener(e -> {
                if (current.type != QuestionType.MULTIPLE_CHOICE) {
                    for (OptionState item : current.options) {
                        item.correct = item == option;
                    }
                } else {
                    option.correct = correct.isSelected();
                }
                editorChanged();
            });

            JTextField text = new JTextField(option.text);
            text.setEditable(current.type != QuestionType.TRUE_FALSE);
            text.getDocument().addDocumentListener(documentListener(() -> {
                option.text = text.getText();
                editorChanged();
            }));
            JLabel order = new JLabel(String.valueOf(index + 1), SwingConstants.CENTER);
            order.setForeground(UiTheme.MUTED);
            order.setPreferredSize(new Dimension(24, 38));
            JPanel left = new JPanel(new BorderLayout(5, 0));
            left.setOpaque(false);
            left.add(order, BorderLayout.WEST);
            left.add(correct, BorderLayout.CENTER);

            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            panel.add(left, BorderLayout.WEST);
            panel.add(text, BorderLayout.CENTER);
            if (current.type.allowsAdding) {
                JButton remove = new JButton("Quitar");
                UiTheme.styleSecondaryButton(remove);
                remove.addActionListener(e -> removeOption(option));
                panel.add(remove, BorderLayout.EAST);
            }
            UiTheme.styleTree(panel);
        }
    }

    private static final class BuilderHeaderPanel extends JPanel {
        private BuilderHeaderPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(
                    0, 0, UiTheme.NAV_DARK,
                    getWidth(), getHeight(), new Color(137, 36, 47)
            ));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g2.setColor(new Color(255, 255, 255, 20));
            g2.fillOval(getWidth() - 190, -105, 250, 250);
            g2.setColor(new Color(205, 145, 63, 55));
            g2.fillOval(getWidth() - 340, getHeight() - 70, 230, 130);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private final class DraftRenderer extends JPanel implements ListCellRenderer<DraftState> {
        private final JLabel number = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private boolean selected;

        private DraftRenderer() {
            super(new BorderLayout(8, 2));
            setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
            number.setHorizontalAlignment(SwingConstants.CENTER);
            number.setFont(number.getFont().deriveFont(Font.BOLD, 13f));
            number.setPreferredSize(new Dimension(28, 28));
            JPanel copy = verticalPanel();
            title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
            detail.setFont(detail.getFont().deriveFont(11f));
            copy.add(title);
            copy.add(Box.createVerticalStrut(3));
            copy.add(detail);
            add(number, BorderLayout.WEST);
            add(copy, BorderLayout.CENTER);
            setOpaque(false);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DraftState> list, DraftState value,
                                                      int index, boolean selected, boolean focus) {
            number.setText(String.valueOf(index + 1));
            String text = value.statement == null || value.statement.isBlank()
                    ? "Sin enunciado" : value.statement.trim();
            title.setText(text.length() > 24 ? text.substring(0, 24) + "..." : text);
            detail.setText(value.type.shortLabel + (value.imageData == null ? "" : "  |  IMG"));
            this.selected = selected;
            number.setForeground(selected ? UiTheme.PRIMARY_DARK : UiTheme.MUTED);
            title.setForeground(selected ? UiTheme.PRIMARY_DARK : UiTheme.INK);
            detail.setForeground(UiTheme.MUTED);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(selected ? UiTheme.PRIMARY_SOFT : UiTheme.SURFACE);
            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 14, 14);
            if (selected) {
                g2.setColor(UiTheme.PRIMARY);
                g2.fillRoundRect(3, 15, 4, getHeight() - 30, 4, 4);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private final class DraftState {
        private Long id;
        private String statement = "";
        private QuestionType type = QuestionType.SINGLE_CHOICE;
        private double score = 1.0;
        private List<OptionState> options = defaultOptions(type);
        private byte[] imageData;
        private String imageName;
        private String imageContentType;
        private String objectId;

        private DraftState() {
        }

        private DraftState(QuestionDraft draft) {
            id = draft.getId();
            statement = draft.getStatement() == null ? "" : draft.getStatement();
            type = QuestionType.fromCode(draft.getType());
            score = draft.getScore();
            options = new ArrayList<>();
            for (Answer answer : draft.getAnswers()) {
                options.add(new OptionState(answer.getContent(), answer.isCorrect()));
            }
            if (options.isEmpty()) {
                options = defaultOptions(type);
            }
            imageData = draft.getImageData();
            imageName = draft.getImageName();
            imageContentType = draft.getImageContentType();
            objectId = draft.getObjectId();
        }

        private DraftState copy() {
            DraftState copy = new DraftState();
            copy.statement = statement;
            copy.type = type;
            copy.score = score;
            copy.options = new ArrayList<>();
            for (OptionState option : options) {
                copy.options.add(new OptionState(option.text, option.correct));
            }
            copy.imageData = imageData == null ? null : imageData.clone();
            copy.imageName = imageName;
            copy.imageContentType = imageContentType;
            copy.objectId = objectId;
            return copy;
        }
    }

    private static final class OptionState {
        private String text;
        private boolean correct;

        private OptionState(String text, boolean correct) {
            this.text = text;
            this.correct = correct;
        }
    }

    private record PreparedImage(byte[] data, String name, String contentType) {
    }

    private enum QuestionType {
        SINGLE_CHOICE("Seleccion unica", "Unica", "seleccion_unica",
                "Marca una opcion como correcta.", true),
        MULTIPLE_CHOICE("Casillas multiples", "Multiple", "seleccion_multiple",
                "Puedes marcar varias respuestas correctas.", true),
        TRUE_FALSE("Verdadero o falso", "V / F", "verdadero_falso",
                "Selecciona cual respuesta es correcta.", false),
        SHORT_ANSWER("Respuesta corta", "Abierta", "abierta",
                "Escribe la respuesta esperada para calificar automaticamente.", false);

        private final String label;
        private final String shortLabel;
        private final String code;
        private final String hint;
        private final boolean allowsAdding;

        QuestionType(String label, String shortLabel, String code, String hint, boolean allowsAdding) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.code = code;
            this.hint = hint;
            this.allowsAdding = allowsAdding;
        }

        private static QuestionType fromCode(String code) {
            if (code != null) {
                for (QuestionType type : values()) {
                    if (type.code.equalsIgnoreCase(code.trim())) {
                        return type;
                    }
                }
            }
            return SINGLE_CHOICE;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

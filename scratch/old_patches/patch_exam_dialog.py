import re

with open('src/app/ui/ExamDialog.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace initUi and finishExam
start_idx = content.find('private void initUi() {')
if start_idx != -1:
    new_methods = """private javax.swing.Timer timer;
    private java.time.LocalDateTime examStartedAt;
    private Exam exam;

    private void initUi() {
        exam = examService.findExam(examId);
        if (!examService.isAvailable(exam)) {
            JOptionPane.showMessageDialog(this, "Este cuestionario no esta disponible.");
            dispose();
            return;
        }
        
        List<app.model.ExamAttempt> attempts = attemptService.listByStudent(studentId).stream().filter(a -> a.getExamId() == examId).toList();
        if (attempts.size() >= exam.getMaxAttempts()) {
            JOptionPane.showMessageDialog(this, "Ya alcanzaste el limite maximo de intentos para este examen (" + exam.getMaxAttempts() + ").");
            dispose();
            return;
        }

        this.attemptId = attemptService.startAttempt(examId, studentId);
        this.examStartedAt = java.time.LocalDateTime.now();

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setBackground(app.ui.UiTheme.BACKGROUND);
        
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(new JLabel("<html><h2>" + exam.getTitle() + "</h2></html>"), BorderLayout.WEST);
        
        JLabel timerLabel = new JLabel("Tiempo: " + exam.getDurationMinutes() + ":00");
        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 16f));
        timerLabel.setForeground(new Color(220, 53, 69)); // Rojo
        header.add(timerLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        timer = new javax.swing.Timer(1000, e -> {
            long elapsedSeconds = java.time.Duration.between(examStartedAt, java.time.LocalDateTime.now()).getSeconds();
            long remainingSeconds = (exam.getDurationMinutes() * 60L) - elapsedSeconds;
            if (remainingSeconds <= 0) {
                timer.stop();
                JOptionPane.showMessageDialog(ExamDialog.this, "Se acabo el tiempo. El examen se enviara automaticamente.");
                finishExam();
            } else {
                timerLabel.setText(String.format("Tiempo: %02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
            }
        });
        timer.start();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(app.ui.UiTheme.BACKGROUND);

        questions.addAll(examService.listQuestionsByExam(examId));
        for (Question q : questions) {
            List<Answer> answers = answerService.listByQuestion(q.getId());
            answersByQuestion.put(q.getId(), answers);

            JPanel qPanel = new JPanel();
            qPanel.putClientProperty("uiTheme.surface", Boolean.TRUE);
            qPanel.setLayout(new BoxLayout(qPanel, BoxLayout.Y_AXIS));
            qPanel.setBackground(app.ui.UiTheme.SURFACE);
            qPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(app.ui.UiTheme.BORDER),
                    BorderFactory.createEmptyBorder(14, 14, 14, 14)
            ));
            qPanel.add(new JLabel("<html><b>" + q.getStatement() + "</b></html>"));
            qPanel.add(Box.createVerticalStrut(8));

            ButtonGroup group = new ButtonGroup();
            for (Answer a : answers) {
                JRadioButton rb = new JRadioButton(a.getContent());
                rb.setActionCommand(String.valueOf(a.getId()));
                group.add(rb);
                qPanel.add(rb);
            }
            answerGroups.put(q.getId(), group);
            content.add(qPanel);
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        JButton submit = new JButton("Finalizar Examen");
        app.ui.UiTheme.styleButton(submit);
        submit.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "¿Estas seguro de enviar el examen?", "Confirmar", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                finishExam();
            }
        });
        
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(submit);
        root.add(bottom, BorderLayout.SOUTH);

        app.ui.UiTheme.styleTree(root);
        setContentPane(root);
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (timer != null && timer.isRunning()) {
                    timer.stop();
                }
            }
        });
    }

    private void finishExam() {
        if (timer != null) timer.stop();

        double rawScore = 0.0;
        double maxRawScore = 0.0;
        
        StringBuilder json = new StringBuilder();
        json.append("{\\"examId\\":").append(examId)
                .append(",\\"attemptId\\":").append(attemptId)
                .append(",\\"items\\":[");

        boolean first = true;
        for (Question q : questions) {
            maxRawScore += q.getScore();
            
            ButtonGroup group = answerGroups.get(q.getId());
            String selectedId = group.getSelection() != null ? group.getSelection().getActionCommand() : null;
            boolean correct = false;
            long selectedAnswerId = -1;
            for (Answer a : answersByQuestion.get(q.getId())) {
                if (selectedId != null && String.valueOf(a.getId()).equals(selectedId)) {
                    selectedAnswerId = a.getId();
                    correct = a.isCorrect();
                    break;
                }
            }
            double score = correct ? q.getScore() : 0.0;
            rawScore += score;

            if (!first) json.append(",");
            json.append("{\\"questionId\\":").append(q.getId())
                    .append(",\\"selectedAnswerId\\":").append(selectedAnswerId)
                    .append(",\\"correct\\":").append(correct)
                    .append(",\\"score\\":").append(score)
                    .append("}");
            first = false;
        }
        
        // Calcular nota sobre 5.0
        double finalScore = maxRawScore > 0 ? (rawScore / maxRawScore) * 5.0 : 0.0;
        // Redondear a 1 decimal
        finalScore = Math.round(finalScore * 10.0) / 10.0;
        
        json.append("],\\"totalRaw\\":").append(rawScore)
            .append(",\\"maxRaw\\":").append(maxRawScore)
            .append(",\\"calificacionFinal\\":").append(finalScore)
            .append("}");

        attemptService.finishAttempt(attemptId, finalScore);
        resultService.createResult(attemptId, json.toString());

        JOptionPane.showMessageDialog(this, "Examen finalizado.\\nTu calificacion es: " + finalScore + " / 5.0");
        dispose();
    }
}
"""
    content = content[:start_idx] + new_methods

    with open('src/app/ui/ExamDialog.java', 'w', encoding='utf-8') as f:
        f.write(content)
    print("ExamDialog refactored successfully.")
else:
    print("Could not find start index in ExamDialog.")

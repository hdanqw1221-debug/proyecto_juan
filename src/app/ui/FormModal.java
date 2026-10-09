package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class FormModal extends JDialog {
    private final JPanel content;
    private boolean confirmed = false;

    public FormModal(Frame owner, String title, int width, int height) {
        super(owner, title, true);
        setSize(width, height);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        content = new JPanel(new GridBagLayout());
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UiTheme.SURFACE);
        wrapper.add(content, BorderLayout.CENTER);

        setContentPane(wrapper);
    }

    public JPanel getContent() {
        return content;
    }

    public void addActions(JButton confirmBtn, JButton cancelBtn) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        if (cancelBtn != null) {
            cancelBtn.addActionListener(e -> dispose());
            actions.add(cancelBtn);
        }

        if (confirmBtn != null) {
            confirmBtn.addActionListener(e -> {
                confirmed = true;
                dispose();
            });
            actions.add(confirmBtn);
        }

        ((JPanel) getContentPane()).add(actions, BorderLayout.SOUTH);
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}

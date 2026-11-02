package app.ui;

import javax.swing.*;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
        e.printStackTrace();
    }
}

package app.ui;

import app.model.RoleType;
import app.model.User;
import app.service.AuditService;
import app.service.UserService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ProfileDialog extends JDialog {
    private final User user;
    private final UserService userService;
    private final AuditService auditService;

    private final JPasswordField currentPassField = new JPasswordField(20);
    private final JPasswordField newPassField = new JPasswordField(20);
    private final JPasswordField confirmPassField = new JPasswordField(20);
    private final JCheckBox showPasswordsCheck = new JCheckBox("Mostrar contraseñas");

    public ProfileDialog(Frame owner, User user, UserService userService, AuditService auditService) {
        super(owner, "Mi Perfil - " + user.getUsername(), true);
        this.user = user;
        this.userService = userService;
        this.auditService = auditService;

        setSize(520, 640);
        setMinimumSize(new Dimension(480, 600));
        setLocationRelativeTo(owner);
        initUi();
    }

    private void initUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        content.setBackground(UiTheme.BACKGROUND);

        // --- TARJETA 1: INFORMACIÓN DEL PERFIL ---
        JPanel profileCard = UiTheme.card(new BorderLayout(16, 12));
        profileCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, UiTheme.PRIMARY),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));

        // Avatar
        String initial = (user.getUsername() != null && !user.getUsername().isEmpty())
                ? user.getUsername().substring(0, 1).toUpperCase() : "U";
        JLabel avatar = new JLabel(initial, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.PRIMARY);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setOpaque(false);
        avatar.setForeground(Color.WHITE);
        avatar.setFont(new Font("Bahnschrift", Font.BOLD, 24));
        avatar.setPreferredSize(new Dimension(56, 56));

        JPanel nameAndEmail = new JPanel();
        nameAndEmail.setOpaque(false);
        nameAndEmail.setLayout(new BoxLayout(nameAndEmail, BoxLayout.Y_AXIS));
        JLabel nameLbl = UiTheme.title(user.getUsername(), 20f);
        JLabel emailLbl = new JLabel(user.getEmail() != null ? user.getEmail() : "Sin correo registrado");
        emailLbl.setForeground(UiTheme.MUTED);
        nameAndEmail.add(nameLbl);
        nameAndEmail.add(Box.createVerticalStrut(3));
        nameAndEmail.add(emailLbl);

        JPanel headerTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        headerTop.setOpaque(false);
        headerTop.add(avatar);
        headerTop.add(nameAndEmail);
        profileCard.add(headerTop, BorderLayout.NORTH);

        // Detalles del usuario (Roles, Fecha registro, Estado)
        JPanel detailsGrid = new JPanel(new GridLayout(3, 2, 8, 6));
        detailsGrid.setOpaque(false);
        detailsGrid.setBorder(BorderFactory.createEmptyBorder(10, 4, 2, 4));

        JLabel rolesTitle = new JLabel("Rol(es) asignado(s):");
        rolesTitle.setForeground(UiTheme.MUTED);

        StringBuilder rolesText = new StringBuilder();
        if (user.getRoles().isEmpty()) {
            rolesText.append("Sin rol asignado");
        } else {
            for (int i = 0; i < user.getRoles().size(); i++) {
                if (i > 0) rolesText.append(", ");
                RoleType r = user.getRoles().get(i);
                rolesText.append(r == RoleType.ADMINISTRADOR ? "Administrador" :
                        (r == RoleType.PROFESOR ? "Docente" : "Estudiante"));
            }
        }
        JLabel rolesValue = new JLabel(rolesText.toString());
        rolesValue.setFont(rolesValue.getFont().deriveFont(Font.BOLD));

        JLabel registeredTitle = new JLabel("Fecha de registro:");
        registeredTitle.setForeground(UiTheme.MUTED);
        String regDate = "No disponible";
        if (user.getCreatedAt() != null) {
            regDate = user.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")));
        }
        JLabel registeredValue = new JLabel(regDate);

        JLabel statusTitle = new JLabel("Estado de cuenta:");
        statusTitle.setForeground(UiTheme.MUTED);
        JLabel statusValue = new JLabel(user.isActive() ? "ACTIVO" : "INACTIVO");
        statusValue.setForeground(user.isActive() ? UiTheme.SUCCESS : UiTheme.PRIMARY);
        statusValue.setFont(statusValue.getFont().deriveFont(Font.BOLD));

        detailsGrid.add(rolesTitle);
        detailsGrid.add(rolesValue);
        detailsGrid.add(registeredTitle);
        detailsGrid.add(registeredValue);
        detailsGrid.add(statusTitle);
        detailsGrid.add(statusValue);

        profileCard.add(detailsGrid, BorderLayout.CENTER);
        profileCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));
        profileCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(profileCard);
        content.add(Box.createVerticalStrut(16));

        // --- TARJETA 2: SEGURIDAD Y CAMBIO DE CONTRASEÑA ---
        JPanel securityCard = UiTheme.card(new BorderLayout(12, 10));
        securityCard.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel secTop = new JPanel(new BorderLayout(4, 2));
        secTop.setOpaque(false);
        JLabel secTitle = UiTheme.title("Seguridad de la Cuenta", 17f);
        JLabel secSubtitle = new JLabel("Cambia tu clave periódicamente. Mínimo 6 caracteres, 1 mayúscula y 1 número.");
        secSubtitle.setForeground(UiTheme.MUTED);
        secSubtitle.setFont(secSubtitle.getFont().deriveFont(12f));
        secTop.add(secTitle, BorderLayout.NORTH);
        secTop.add(secSubtitle, BorderLayout.SOUTH);
        securityCard.add(secTop, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 2, 10, 10));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));

        form.add(new JLabel("Contraseña actual:"));
        form.add(currentPassField);
        form.add(new JLabel("Nueva contraseña:"));
        form.add(newPassField);
        form.add(new JLabel("Confirmar nueva contraseña:"));
        form.add(confirmPassField);

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.setOpaque(false);
        formWrapper.add(form, BorderLayout.CENTER);

        showPasswordsCheck.setOpaque(false);
        showPasswordsCheck.addActionListener(e -> {
            char echo = showPasswordsCheck.isSelected() ? (char) 0 : '\u2022';
            currentPassField.setEchoChar(echo);
            newPassField.setEchoChar(echo);
            confirmPassField.setEchoChar(echo);
        });
        formWrapper.add(showPasswordsCheck, BorderLayout.SOUTH);

        securityCard.add(formWrapper, BorderLayout.CENTER);

        JButton updateBtn = new JButton("Actualizar Contraseña");
        UiTheme.styleButton(updateBtn);
        updateBtn.addActionListener(e -> performPasswordChange());

        JPanel secActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        secActions.setOpaque(false);
        secActions.add(updateBtn);
        securityCard.add(secActions, BorderLayout.SOUTH);

        securityCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        securityCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(securityCard);
        content.add(Box.createVerticalStrut(14));

        // --- BOTÓN CERRAR ---
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomBar.setOpaque(false);
        JButton closeBtn = new JButton("Cerrar");
        UiTheme.styleSecondaryButton(closeBtn);
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn);
        bottomBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bottomBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(bottomBar);

        // Tecla ESC para cerrar y ENTER en campos para submit
        content.registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        confirmPassField.addActionListener(e -> performPasswordChange());
        newPassField.addActionListener(e -> performPasswordChange());

        setContentPane(content);
        UiTheme.styleTree(this);
    }

    private void performPasswordChange() {
        String currentPass = new String(currentPassField.getPassword()).trim();
        String newPass = new String(newPassField.getPassword()).trim();
        String confirmPass = new String(confirmPassField.getPassword()).trim();

        if (currentPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debes ingresar tu contraseña actual.", "Validación", JOptionPane.WARNING_MESSAGE);
            currentPassField.requestFocus();
            return;
        }

        if (newPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debes ingresar la nueva contraseña.", "Validación", JOptionPane.WARNING_MESSAGE);
            newPassField.requestFocus();
            return;
        }

        if (!newPass.equals(confirmPass)) {
            JOptionPane.showMessageDialog(this, "La confirmación de la contraseña no coincide.", "Validación", JOptionPane.WARNING_MESSAGE);
            confirmPassField.requestFocus();
            return;
        }

        try {
            userService.changeOwnPassword(user.getId(), currentPass, newPass);
            // Actualizar hash en memoria
            User fresh = userService.findById(user.getId());
            if (fresh != null) {
                user.setPasswordHash(fresh.getPasswordHash());
            }

            auditService.log(user.getId(), "PASSWORD_CHANGE_SELF", "Cambio de contraseña exitoso por autoservicio");

            JOptionPane.showMessageDialog(this,
                    "Tu contraseña ha sido actualizada exitosamente.",
                    "Contraseña Actualizada",
                    JOptionPane.INFORMATION_MESSAGE);

            currentPassField.setText("");
            newPassField.setText("");
            confirmPassField.setText("");
            showPasswordsCheck.setSelected(false);
            currentPassField.setEchoChar('\u2022');
            newPassField.setEchoChar('\u2022');
            confirmPassField.setEchoChar('\u2022');
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validación de Seguridad", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al actualizar la contraseña: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

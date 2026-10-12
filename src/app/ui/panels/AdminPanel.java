package app.ui.panels;

import app.config.AppConfig;
import app.model.Course;
import app.model.LogEntry;
import app.model.Role;
import app.model.RoleType;
import app.model.StudentCourseAssignment;
import app.model.User;
import app.security.AccessControl;
import app.security.InputValidator;
import app.service.AuditService;
import app.service.CourseService;
import app.service.DatabaseBackupService;
import app.service.RoleService;
import app.service.UserImportService;
import app.service.UserService;
import app.service.Visor3DService;
import app.session.SessionManager;
import app.ui.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AdminPanel extends JPanel {

    private final SessionManager sessionManager;
    private final AuditService auditService;
    private final UserService userService;
    private final RoleService roleService;
    private final CourseService courseService;
    private final UserImportService userImportService;
    private final DatabaseBackupService databaseBackupService;

    private final DefaultTableModel usersModel = new DefaultTableModel(new Object[]{"ID", "Usuario", "Email", "Activo", "Roles"}, 0);
    private final DefaultTableModel coursesModel = new DefaultTableModel(new Object[]{"ID", "Nombre", "Descripcion", "Docente Titular", "Creado"}, 0);
    private final DefaultTableModel studentCoursesModel = new DefaultTableModel(new Object[]{"Curso ID", "Materia", "Estudiante ID", "Usuario", "Asignado"}, 0);
    private final DefaultTableModel systemActivityModel =
            new DefaultTableModel(new Object[]{"Accion", "Detalle", "Fecha"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JLabel pythonScriptsSummary = new JLabel("Scripts Python no cargados.");
    private final JPanel pythonScriptsButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
    private final JTabbedPane adminTabs = new JTabbedPane();
    private final JLabel systemUsersValue = UiTheme.title("0", 30f);
    private final JLabel systemActiveUsersValue = UiTheme.title("0", 30f);
    private final JLabel systemCoursesValue = UiTheme.title("0", 30f);
    private final JLabel systemAssignmentsValue = UiTheme.title("0", 30f);
    private final JLabel systemUsersDetail = new JLabel("Sin datos");
    private final JLabel databaseStatus = new JLabel("Comprobando...");
    private final JLabel modelsStatus = new JLabel("Comprobando...");
    private final JLabel runtimeStatus = new JLabel("Comprobando...");
    private final JLabel systemLastUpdate = new JLabel("Pendiente de actualizacion");
    private final JPanel systemAlertsPanel = new JPanel();

    public AdminPanel(SessionManager sessionManager, AuditService auditService, UserService userService, RoleService roleService,
                      CourseService courseService) {
        this(sessionManager, auditService, userService, roleService, courseService,
                new UserImportService(userService), new DatabaseBackupService(AppConfig.load("config/app.properties")));
    }

    public AdminPanel(SessionManager sessionManager, AuditService auditService, UserService userService, RoleService roleService,
                      CourseService courseService, UserImportService userImportService, DatabaseBackupService databaseBackupService) {
        this.sessionManager = sessionManager;
        this.auditService = auditService;
        this.userService = userService;
        this.roleService = roleService;
        this.courseService = courseService;
        this.userImportService = userImportService;
        this.databaseBackupService = databaseBackupService;

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setBackground(UiTheme.BACKGROUND);

        JPanel header = buildHeader("Administracion", "Usuarios, materias, roles y trazabilidad del sistema.");
        adminTabs.add("Usuarios", buildUsersPanel());
        adminTabs.add("Cursos", buildCoursesPanel());
        adminTabs.add("Estudiante-Materia", buildStudentCoursesPanel());
        adminTabs.add("Roles", buildRolesPanel());
        adminTabs.add("Passwords", buildPasswordPanel());
        adminTabs.add("Logs", buildLogsPanel());
        adminTabs.add("Simuladores 3D", buildPythonSimulatorsPanel());
        adminTabs.add("Sistema", buildSystemPanel());
        adminTabs.addChangeListener(e -> {
            int selected = adminTabs.getSelectedIndex();
            if (selected >= 0 && "Sistema".equals(adminTabs.getTitleAt(selected))) {
                refreshSystemDashboard();
            }
        });
        add(header, BorderLayout.NORTH);
        add(adminTabs, BorderLayout.CENTER);

        refreshUsers();
        refreshCourses();
        refreshStudentCourses();
        refreshPythonScripts();
        refreshSystemDashboard();
        UiTheme.styleTree(this);
    }

    private JPanel buildHeader(String title, String subtitle) {
        JPanel header = UiTheme.card(new BorderLayout(6, 6));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 6, 0, 0, UiTheme.PRIMARY),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)
        ));
        header.add(UiTheme.title(title, 24f), BorderLayout.NORTH);
        JLabel detail = new JLabel(subtitle);
        detail.setForeground(UiTheme.MUTED);
        header.add(detail, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JTable table = new JTable(usersModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottomContainer = new JPanel(new BorderLayout(8, 8));
        bottomContainer.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));

        JPanel form = new JPanel(new GridLayout(2, 5, 8, 6));
        JTextField id = new JTextField();
        JTextField username = new JTextField();
        JTextField email = new JTextField();
        JPasswordField password = new JPasswordField();
        JCheckBox active = new JCheckBox("Activo", true);

        JButton create = new JButton("Crear");
        JButton update = new JButton("Actualizar");
        JButton delete = new JButton("Eliminar");
        JButton refresh = new JButton("Refrescar");
        JButton importCsv = new JButton("Importar CSV");
        JButton templateCsv = new JButton("Plantilla CSV");

        form.add(new JLabel("ID"));
        form.add(new JLabel("Usuario"));
        form.add(new JLabel("Email"));
        form.add(new JLabel("Password"));
        form.add(new JLabel("Estado"));

        form.add(id);
        form.add(username);
        form.add(email);
        form.add(password);
        form.add(active);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.add(create);
        actions.add(update);
        actions.add(delete);
        actions.add(refresh);
        actions.add(new JSeparator(SwingConstants.VERTICAL));
        actions.add(importCsv);
        actions.add(templateCsv);

        bottomContainer.add(form, BorderLayout.CENTER);
        bottomContainer.add(actions, BorderLayout.SOUTH);

        panel.add(bottomContainer, BorderLayout.SOUTH);

        create.addActionListener(e -> {
            runAdminAction(() -> {
                AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
                
                String pass = new String(password.getPassword());
                if (!isPasswordStrong(pass)) {
                    throw new IllegalArgumentException("La contrasena es muy debil. Debe tener al menos 6 caracteres, incluir una mayuscula y un numero.");
                }
                
                User u = userService.createUser(username.getText().trim(), email.getText().trim(), pass, active.isSelected());
                auditService.log(sessionManager.getCurrent().getUser().getId(), "USER_CREATE", "Usuario " + u.getUsername());
                refreshUsers();
                id.setText(String.valueOf(u.getId()));
                password.setText("");
                JOptionPane.showMessageDialog(this, "Usuario creado correctamente. ID: " + u.getId());
            });
        });

        update.addActionListener(e -> {
            runAdminAction(() -> {
                AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
                long userId = parseId(id.getText(), "ID de usuario");
                userService.updateUser(userId, username.getText().trim(), email.getText().trim(), active.isSelected());
                auditService.log(sessionManager.getCurrent().getUser().getId(), "USER_UPDATE", "Usuario ID " + userId);
                refreshUsers();
                JOptionPane.showMessageDialog(this, "Usuario actualizado correctamente.");
            });
        });

        delete.addActionListener(e -> {
            runAdminAction(() -> {
                AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
                long userId = parseId(id.getText(), "ID de usuario");
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Vas a eliminar el usuario ID " + userId + ". ¿Deseas continuar?",
                        "Confirmar eliminacion",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
                userService.deleteUser(userId);
                auditService.log(sessionManager.getCurrent().getUser().getId(), "USER_DELETE", "Usuario ID " + userId);
                refreshUsers();
                id.setText("");
                username.setText("");
                email.setText("");
                password.setText("");
                JOptionPane.showMessageDialog(this, "Usuario eliminado correctamente.");
            });
        });

        refresh.addActionListener(e -> runAdminAction(this::refreshUsers));

        importCsv.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Seleccionar archivo CSV de usuarios");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos CSV (*.csv)", "csv"));
            int res = chooser.showOpenDialog(this);
            if (res != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();
            if (!selectedFile.exists() || !selectedFile.isFile()) {
                JOptionPane.showMessageDialog(this, "El archivo seleccionado no es valido.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                UserImportService.ImportResult importResult = userImportService.importFromCsv(selectedFile);
                refreshUsers();
                auditService.log(sessionManager.getCurrent().getUser().getId(), "USER_IMPORT_CSV",
                        "Importacion masiva: " + importResult.getSuccessCount() + " creados, " + importResult.getFailedCount() + " errores.");

                if (importResult.getFailedCount() == 0) {
                    JOptionPane.showMessageDialog(this,
                            "Importacion completada con exito.\n\nUsuarios creados: " + importResult.getSuccessCount(),
                            "Importacion Exitosa",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Resumen de importacion:\n");
                    sb.append(" - Usuarios creados exitosamente: ").append(importResult.getSuccessCount()).append("\n");
                    sb.append(" - Registros con observaciones: ").append(importResult.getFailedCount()).append("\n\n");
                    sb.append("Detalle de inconsistencias:\n");
                    for (String err : importResult.getErrors()) {
                        sb.append(" • ").append(err).append("\n");
                    }

                    JTextArea textArea = new JTextArea(sb.toString());
                    textArea.setEditable(false);
                    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                    JScrollPane scrollPane = new JScrollPane(textArea);
                    scrollPane.setPreferredSize(new Dimension(560, 260));

                    JOptionPane.showMessageDialog(this, scrollPane, "Resultado de Importacion Masiva", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Fallo al procesar el archivo CSV:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                setCursor(Cursor.getDefaultCursor());
            }
        }));

        templateCsv.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Descargar Plantilla CSV");
            chooser.setSelectedFile(new File("plantilla_usuarios.csv"));
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos CSV (*.csv)", "csv"));
            int res = chooser.showSaveDialog(this);
            if (res != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File dest = chooser.getSelectedFile();
            if (!dest.getName().toLowerCase().endsWith(".csv")) {
                dest = new File(dest.getAbsolutePath() + ".csv");
            }

            try {
                UserImportService.generateTemplateFile(dest);
                JOptionPane.showMessageDialog(this,
                        "Plantilla CSV guardada correctamente en:\n" + dest.getAbsolutePath(),
                        "Plantilla Generada",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al guardar la plantilla:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    id.setText(String.valueOf(table.getValueAt(row, 0)));
                    username.setText(String.valueOf(table.getValueAt(row, 1)));
                    email.setText(String.valueOf(table.getValueAt(row, 2)));
                    active.setSelected(Boolean.parseBoolean(String.valueOf(table.getValueAt(row, 3))));
                }
            }
        });

        return panel;
    }

    private JPanel buildRolesPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultListModel<String> rolesListModel = new DefaultListModel<>();
        JList<String> rolesList = new JList<>(rolesListModel);
        panel.add(new JScrollPane(rolesList), BorderLayout.CENTER);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField userIdField = new JTextField(8);
        JCheckBox admin = new JCheckBox("ADMINISTRADOR");
        JCheckBox profesor = new JCheckBox("PROFESOR");
        JCheckBox estudiante = new JCheckBox("ESTUDIANTE");
        JButton assign = new JButton("Asignar");

        form.add(new JLabel("Usuario ID"));
        form.add(userIdField);
        form.add(admin);
        form.add(profesor);
        form.add(estudiante);
        form.add(assign);

        panel.add(form, BorderLayout.SOUTH);

        assign.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            long userId = parseId(userIdField.getText(), "ID de usuario");
            List<RoleType> roles = new ArrayList<>();
            if (admin.isSelected()) roles.add(RoleType.ADMINISTRADOR);
            if (profesor.isSelected()) roles.add(RoleType.PROFESOR);
            if (estudiante.isSelected()) roles.add(RoleType.ESTUDIANTE);
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("Selecciona al menos un rol para asignar.");
            }
            userService.assignRoles(userId, roles);
            auditService.log(sessionManager.getCurrent().getUser().getId(), "ROLE_ASSIGN", "Usuario ID " + userId);
            refreshUsers();
            JOptionPane.showMessageDialog(this, "Roles asignados correctamente.");
        }));

        rolesListModel.clear();
        for (Role r : roleService.listRoles()) {
            rolesListModel.addElement(r.getId() + " - " + r.getName());
        }

        return panel;
    }

    private JPanel buildCoursesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JTable table = new JTable(coursesModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JTextField name = new JTextField(14);
        JTextField desc = new JTextField(16);
        JComboBox<UserComboItem> teacherSelector = new JComboBox<>();
        teacherSelector.setPreferredSize(new Dimension(170, 36));

        Runnable reloadTeachers = () -> {
            teacherSelector.removeAllItems();
            for (User user : userService.listUsers()) {
                if (user.getRoles().contains(RoleType.PROFESOR) || user.getRoles().contains(RoleType.ADMINISTRADOR)) {
                    teacherSelector.addItem(new UserComboItem(user.getId(), user.getUsername() + " (" + user.getRoles().get(0) + ")"));
                }
            }
        };
        reloadTeachers.run();

        JButton create = new JButton("+ Crear");
        UiTheme.styleButton(create);
        JButton edit = new JButton("Editar");
        UiTheme.styleSecondaryButton(edit);
        JButton delete = new JButton("Eliminar");
        UiTheme.styleSecondaryButton(delete);
        JButton refresh = new JButton("Refrescar");

        top.add(new JLabel("Nombre:"));
        top.add(name);
        top.add(new JLabel("Descripcion:"));
        top.add(desc);
        top.add(new JLabel("Docente:"));
        top.add(teacherSelector);
        top.add(create);
        top.add(edit);
        top.add(delete);
        top.add(refresh);
        panel.add(top, BorderLayout.NORTH);

        create.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            if (name.getText() == null || name.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del curso es obligatorio.");
            }
            UserComboItem selectedTeacher = (UserComboItem) teacherSelector.getSelectedItem();
            long teacherId = selectedTeacher != null ? selectedTeacher.id : sessionManager.getCurrent().getUser().getId();
            long id = courseService.createCourse(name.getText().trim(), desc.getText().trim(), teacherId);
            auditService.log(sessionManager.getCurrent().getUser().getId(), "COURSE_CREATE", "Curso ID " + id + " -> Docente " + teacherId);
            name.setText("");
            desc.setText("");
            refreshCourses();
            JOptionPane.showMessageDialog(this, "Materia creada con exito. ID: " + id);
        }));

        edit.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            int row = table.getSelectedRow();
            if (row < 0) {
                throw new IllegalArgumentException("Selecciona una materia en la tabla para editar.");
            }
            long courseId = Long.parseLong(String.valueOf(coursesModel.getValueAt(row, 0)));
            Course course = courseService.findById(courseId);
            if (course == null) {
                throw new IllegalArgumentException("No se encontro la materia.");
            }

            JTextField editName = new JTextField(course.getName(), 20);
            JTextField editDesc = new JTextField(course.getDescription() != null ? course.getDescription() : "", 20);
            JComboBox<UserComboItem> editTeacher = new JComboBox<>();
            for (User u : userService.listUsers()) {
                if (u.getRoles().contains(RoleType.PROFESOR) || u.getRoles().contains(RoleType.ADMINISTRADOR)) {
                    UserComboItem item = new UserComboItem(u.getId(), u.getUsername() + " (" + u.getRoles().get(0) + ")");
                    editTeacher.addItem(item);
                    if (u.getId() == course.getTeacherId()) {
                        editTeacher.setSelectedItem(item);
                    }
                }
            }

            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Nombre:")); form.add(editName);
            form.add(new JLabel("Descripcion:")); form.add(editDesc);
            form.add(new JLabel("Docente Titular:")); form.add(editTeacher);

            int opt = JOptionPane.showConfirmDialog(this, form, "Editar Materia (ID " + courseId + ")",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opt == JOptionPane.OK_OPTION) {
                UserComboItem item = (UserComboItem) editTeacher.getSelectedItem();
                long teacherId = item != null ? item.id : course.getTeacherId();
                courseService.updateCourse(courseId, editName.getText().trim(), editDesc.getText().trim(), teacherId);
                auditService.log(sessionManager.getCurrent().getUser().getId(), "COURSE_UPDATE", "Curso ID " + courseId);
                refreshCourses();
                JOptionPane.showMessageDialog(this, "Materia actualizada correctamente.");
            }
        }));

        delete.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            int row = table.getSelectedRow();
            if (row < 0) {
                throw new IllegalArgumentException("Selecciona una materia en la tabla para eliminar.");
            }
            long courseId = Long.parseLong(String.valueOf(coursesModel.getValueAt(row, 0)));
            String courseName = String.valueOf(coursesModel.getValueAt(row, 1));
            int opt = JOptionPane.showConfirmDialog(this,
                    "¿Estas seguro de eliminar la materia '" + courseName + "'?\n"
                    + "Esta accion eliminara tambien las evaluaciones, simulaciones y asignaciones asociadas.",
                    "Confirmar eliminacion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                courseService.deleteCourse(courseId);
                auditService.log(sessionManager.getCurrent().getUser().getId(), "COURSE_DELETE", "Curso ID " + courseId);
                refreshCourses();
                JOptionPane.showMessageDialog(this, "Materia eliminada.");
            }
        }));

        refresh.addActionListener(e -> runAdminAction(() -> {
            reloadTeachers.run();
            refreshCourses();
        }));
        return panel;
    }

    private JPanel buildPasswordPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField userId = new JTextField(8);
        JPasswordField newPass = new JPasswordField(12);
        JButton change = new JButton("Cambiar Password");

        panel.add(new JLabel("Usuario ID"));
        panel.add(userId);
        panel.add(new JLabel("Nuevo Password"));
        panel.add(newPass);
        panel.add(change);

        change.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            long id = parseId(userId.getText(), "ID de usuario");
            String pass = new String(newPass.getPassword());
            if (!isPasswordStrong(pass)) {
                throw new IllegalArgumentException("La nueva contrasena es muy debil. Debe tener al menos 6 caracteres, incluir una mayuscula y un numero.");
            }
            userService.changePassword(id, pass);
            auditService.log(sessionManager.getCurrent().getUser().getId(), "PASSWORD_CHANGE", "Usuario ID " + id);
            refreshUsers();
            newPass.setText("");
            JOptionPane.showMessageDialog(this, "Password actualizado correctamente.");
        }));

        return panel;
    }

    private JPanel buildStudentCoursesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JTable table = new JTable(studentCoursesModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<UserComboItem> studentSelector = new JComboBox<>();
        JComboBox<CourseComboItem> courseSelector = new JComboBox<>();
        JButton assign = new JButton("Asignar Materia");
        JButton remove = new JButton("Quitar Materia");
        JButton refresh = new JButton("Refrescar");

        top.add(new JLabel("Estudiante"));
        top.add(studentSelector);
        top.add(new JLabel("Materia"));
        top.add(courseSelector);
        top.add(assign);
        top.add(remove);
        top.add(refresh);
        panel.add(top, BorderLayout.NORTH);

        JLabel summary = new JLabel("Asignaciones activas: 0");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(summary);
        panel.add(bottom, BorderLayout.SOUTH);

        Runnable reloadSelectors = () -> {
            studentSelector.removeAllItems();
            for (User user : userService.listUsers()) {
                if (user.getRoles().contains(RoleType.ESTUDIANTE)) {
                    studentSelector.addItem(new UserComboItem(user.getId(), user.getUsername()));
                }
            }

            courseSelector.removeAllItems();
            for (var course : courseService.listAll()) {
                courseSelector.addItem(new CourseComboItem(course.getId(), course.getName()));
            }
        };

        reloadSelectors.run();

        assign.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            UserComboItem student = (UserComboItem) studentSelector.getSelectedItem();
            CourseComboItem course = (CourseComboItem) courseSelector.getSelectedItem();
            if (student == null || course == null) {
                throw new IllegalArgumentException("Selecciona estudiante y materia.");
            }

            courseService.assignStudentToCourse(student.id, course.id);
            auditService.log(sessionManager.getCurrent().getUser().getId(), "STUDENT_COURSE_ASSIGN",
                    "Estudiante " + student.id + " -> Curso " + course.id);
            refreshStudentCourses();
            summary.setText("Asignaciones activas: " + studentCoursesModel.getRowCount());
            JOptionPane.showMessageDialog(this, "Materia asignada al estudiante.");
        }));

        remove.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            UserComboItem student = (UserComboItem) studentSelector.getSelectedItem();
            CourseComboItem course = (CourseComboItem) courseSelector.getSelectedItem();
            if (student == null || course == null) {
                throw new IllegalArgumentException("Selecciona estudiante y materia.");
            }

            courseService.removeStudentFromCourse(student.id, course.id);
            auditService.log(sessionManager.getCurrent().getUser().getId(), "STUDENT_COURSE_REMOVE",
                    "Estudiante " + student.id + " -> Curso " + course.id);
            refreshStudentCourses();
            summary.setText("Asignaciones activas: " + studentCoursesModel.getRowCount());
            JOptionPane.showMessageDialog(this, "Materia retirada del estudiante.");
        }));

        refresh.addActionListener(e -> runAdminAction(() -> {
            reloadSelectors.run();
            refreshStudentCourses();
            summary.setText("Asignaciones activas: " + studentCoursesModel.getRowCount());
        }));

        summary.setText("Asignaciones activas: " + studentCoursesModel.getRowCount());

        return panel;
    }

    private long parseId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo " + fieldName + " es obligatorio.");
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El campo " + fieldName + " debe ser numerico.");
        }
    }
    
    private boolean isPasswordStrong(String password) {
        return InputValidator.isStrongPassword(password);
    }

    private void runAdminAction(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | SecurityException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private JPanel buildLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultTableModel logModel = new DefaultTableModel(new Object[]{"ID", "Usuario", "Accion", "Detalle", "Fecha"}, 0);
        JTable table = new JTable(logModel);
        JButton refresh = new JButton("Refrescar");
        refresh.addActionListener(e -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            logModel.setRowCount(0);
            for (var entry : auditService.listRecent(100)) {
                logModel.addRow(new Object[]{entry.getId(), entry.getUserId(), entry.getAction(), entry.getDetail(), entry.getCreatedAt()});
            }
        });
        panel.add(refresh, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPythonSimulatorsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel title = new JLabel("Ejecucion de scripts de simulacion 3D para administracion.");
        title.setForeground(UiTheme.MUTED);
        header.add(title);
        panel.add(header, BorderLayout.NORTH);

        pythonScriptsButtonsPanel.setOpaque(false);
        JScrollPane scriptsScroll = new JScrollPane(pythonScriptsButtonsPanel);
        scriptsScroll.setBorder(BorderFactory.createTitledBorder("Scripts Python detectados"));
        scriptsScroll.setPreferredSize(new Dimension(600, 260));
        scriptsScroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scriptsScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshPython = new JButton("Recargar Scripts Python");
        JButton installPythonDeps = new JButton("Instalar Dependencias Python");
        JButton openPythonFolder = new JButton("Abrir Carpeta 3D");
        actions.add(refreshPython);
        actions.add(installPythonDeps);
        actions.add(openPythonFolder);
        actions.add(pythonScriptsSummary);
        panel.add(actions, BorderLayout.SOUTH);

        refreshPython.addActionListener(e -> refreshPythonScripts());
        openPythonFolder.addActionListener(e -> openPythonScriptsFolder());
        installPythonDeps.addActionListener(e -> installPythonDependencies());

        return panel;
    }

    private JPanel buildSystemPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));

        JPanel overview = UiTheme.card(new BorderLayout(16, 6));
        JPanel overviewCopy = new JPanel();
        overviewCopy.setOpaque(false);
        overviewCopy.setLayout(new BoxLayout(overviewCopy, BoxLayout.Y_AXIS));
        JLabel title = UiTheme.title("Centro de control", 22f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        systemLastUpdate.setForeground(UiTheme.MUTED);
        systemLastUpdate.setAlignmentX(Component.LEFT_ALIGNMENT);
        overviewCopy.add(title);
        overviewCopy.add(Box.createVerticalStrut(4));
        overviewCopy.add(systemLastUpdate);
        JButton refresh = new JButton("Actualizar estado");
        refresh.addActionListener(e -> refreshSystemDashboard());
        overview.add(overviewCopy, BorderLayout.CENTER);
        overview.add(refresh, BorderLayout.EAST);
        overview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(overview);
        content.add(Box.createVerticalStrut(14));

        JPanel metrics = new JPanel(new GridLayout(1, 4, 12, 12));
        metrics.add(systemMetricCard("Usuarios", systemUsersValue, systemUsersDetail, UiTheme.INFO));
        metrics.add(systemMetricCard("Usuarios activos", systemActiveUsersValue,
                new JLabel("Con acceso al sistema"), UiTheme.SUCCESS));
        metrics.add(systemMetricCard("Materias", systemCoursesValue,
                new JLabel("Registradas"), UiTheme.PRIMARY));
        metrics.add(systemMetricCard("Asignaciones", systemAssignmentsValue,
                new JLabel("Estudiante - materia"), UiTheme.WARNING));
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
        metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(metrics);
        content.add(Box.createVerticalStrut(14));

        JPanel statusGrid = new JPanel(new GridLayout(1, 2, 12, 12));
        statusGrid.add(systemStatusCard());
        statusGrid.add(systemAlertsCard());
        statusGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        statusGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(statusGrid);
        content.add(Box.createVerticalStrut(14));

        JPanel activityCard = UiTheme.card(new BorderLayout(10, 10));
        activityCard.add(UiTheme.title("Actividad reciente", 18f), BorderLayout.NORTH);
        JTable activityTable = new JTable(systemActivityModel);
        activityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane activityScroll = new JScrollPane(activityTable);
        activityScroll.setPreferredSize(new Dimension(760, 190));
        activityCard.add(activityScroll, BorderLayout.CENTER);
        activityCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        activityCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(activityCard);
        content.add(Box.createVerticalStrut(14));

        content.add(buildBackupCard());
        content.add(Box.createVerticalStrut(14));

        JPanel quickActions = UiTheme.card(new BorderLayout(12, 8));
        quickActions.add(UiTheme.title("Accesos rapidos", 17f), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        buttons.setOpaque(false);
        JButton users = new JButton("Gestionar usuarios");
        JButton courses = new JButton("Gestionar materias");
        JButton logs = new JButton("Revisar logs");
        JButton models = new JButton("Comprobar modelos 3D");
        users.addActionListener(e -> selectAdminTab("Usuarios"));
        courses.addActionListener(e -> selectAdminTab("Cursos"));
        logs.addActionListener(e -> selectAdminTab("Logs"));
        models.addActionListener(e -> selectAdminTab("Simuladores 3D"));
        buttons.add(users);
        buttons.add(courses);
        buttons.add(logs);
        buttons.add(models);
        quickActions.add(buttons, BorderLayout.CENTER);
        quickActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        quickActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(quickActions);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBackupCard() {
        JPanel card = UiTheme.card(new BorderLayout(14, 10));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, UiTheme.PRIMARY),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));

        JPanel top = new JPanel(new BorderLayout(6, 4));
        top.setOpaque(false);
        JLabel title = UiTheme.title("Copia de Seguridad y Restauracion (PostgreSQL)", 17f);
        JLabel subtitle = new JLabel("Respaldo automatizado de datos relacionales mediante utilitarios pg_dump / pg_restore.");
        subtitle.setForeground(UiTheme.MUTED);
        top.add(title, BorderLayout.NORTH);
        top.add(subtitle, BorderLayout.SOUTH);
        card.add(top, BorderLayout.NORTH);

        JPanel infoGrid = new JPanel(new GridLayout(2, 3, 10, 6));
        infoGrid.setOpaque(false);
        infoGrid.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        JLabel hostLbl = new JLabel("Host / Puerto: " + databaseBackupService.getDbHost() + ":" + databaseBackupService.getDbPort());
        JLabel dbLbl = new JLabel("Base de Datos: " + databaseBackupService.getDbName());
        JLabel userLbl = new JLabel("Usuario DB: " + databaseBackupService.getDbUser());

        boolean pgDumpOk = databaseBackupService.isPgDumpAvailable();
        JLabel dumpStatusLbl = new JLabel(pgDumpOk ? "Utilitario pg_dump: DISPONIBLE" : "Utilitario pg_dump: NO DETECTADO");
        dumpStatusLbl.setForeground(pgDumpOk ? UiTheme.SUCCESS : UiTheme.PRIMARY);
        dumpStatusLbl.setFont(dumpStatusLbl.getFont().deriveFont(Font.BOLD));

        JLabel lastBackupLbl = new JLabel("Ultimo respaldo: Ninguno en esta sesion");
        lastBackupLbl.setForeground(UiTheme.MUTED);

        infoGrid.add(hostLbl);
        infoGrid.add(dbLbl);
        infoGrid.add(userLbl);
        infoGrid.add(dumpStatusLbl);
        infoGrid.add(lastBackupLbl);
        infoGrid.add(new JLabel(""));

        card.add(infoGrid, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        actions.setOpaque(false);

        JButton btnBackupBinary = new JButton("Crear Respaldo (.backup)");
        JButton btnBackupSql = new JButton("Exportar SQL (.sql)");
        JButton btnRestore = new JButton("Restaurar Base de Datos");

        btnBackupBinary.setToolTipText("Genera un respaldo binario comprimido con pg_dump apto para restauracion integra.");
        btnBackupSql.setToolTipText("Genera un volcado SQL legible en texto plano.");
        btnRestore.setToolTipText("Restaura la base de datos a partir de un archivo .backup o .sql existente.");

        actions.add(btnBackupBinary);
        actions.add(btnBackupSql);
        actions.add(btnRestore);

        card.add(actions, BorderLayout.SOUTH);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        java.awt.event.ActionListener backupAction = e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            boolean isSql = (e.getSource() == btnBackupSql);
            String ext = isSql ? ".sql" : ".backup";
            String desc = isSql ? "Script SQL (*.sql)" : "Copia de Seguridad PostgreSQL (*.backup)";

            String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String defaultName = "respaldo_gestion_" + timeStamp + ext;

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Guardar Copia de Seguridad");
            chooser.setSelectedFile(new File(defaultName));
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(desc, ext.replace(".", "")));

            int choice = chooser.showSaveDialog(this);
            if (choice != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File targetFile = chooser.getSelectedFile();
            if (!targetFile.getName().toLowerCase().endsWith(ext)) {
                targetFile = new File(targetFile.getAbsolutePath() + ext);
            }

            File finalFile = targetFile;
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            btnBackupBinary.setEnabled(false);
            btnBackupSql.setEnabled(false);
            btnRestore.setEnabled(false);

            SwingWorker<DatabaseBackupService.BackupResult, Void> worker = new SwingWorker<>() {
                @Override
                protected DatabaseBackupService.BackupResult doInBackground() throws Exception {
                    return databaseBackupService.createBackup(finalFile);
                }

                @Override
                protected void done() {
                    setCursor(Cursor.getDefaultCursor());
                    btnBackupBinary.setEnabled(true);
                    btnBackupSql.setEnabled(true);
                    btnRestore.setEnabled(true);
                    try {
                        DatabaseBackupService.BackupResult res = get();
                        lastBackupLbl.setText("Ultimo: " + res.getFile().getName() + " (" + res.getFormattedSize() + ")");
                        lastBackupLbl.setForeground(UiTheme.SUCCESS);

                        auditService.log(sessionManager.getCurrent().getUser().getId(), "DB_BACKUP",
                                "Respaldo generado: " + res.getFile().getName() + " (" + res.getFormattedSize() + ")");

                        refreshSystemDashboard();

                        JOptionPane.showMessageDialog(AdminPanel.this,
                                "Copia de seguridad completada con exito.\n\n" +
                                        "Archivo: " + res.getFile().getAbsolutePath() + "\n" +
                                        "Tamano: " + res.getFormattedSize() + "\n" +
                                        "Duracion: " + res.getDurationMs() + " ms",
                                "Respaldo Exitoso",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        auditService.log(sessionManager.getCurrent().getUser().getId(), "DB_BACKUP_FAIL", cause.getMessage());
                        JOptionPane.showMessageDialog(AdminPanel.this,
                                "Fallo al generar el respaldo:\n" + cause.getMessage(),
                                "Error de Respaldo",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        });

        btnBackupBinary.addActionListener(backupAction);
        btnBackupSql.addActionListener(backupAction);

        btnRestore.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Seleccionar Archivo de Respaldo para Restaurar");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Respaldos (*.backup, *.sql, *.dump)", "backup", "sql", "dump"));

            int choice = chooser.showOpenDialog(this);
            if (choice != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File sourceFile = chooser.getSelectedFile();
            if (!sourceFile.exists()) {
                JOptionPane.showMessageDialog(this, "El archivo no existe.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "ATENCION: La restauracion sobrescribira los datos actuales de la base de datos 'gestion' con los del archivo:\n" +
                            sourceFile.getName() + "\n\n¿Deseas proceder con la restauracion?",
                    "Confirmar Restauracion de Base de Datos",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            btnBackupBinary.setEnabled(false);
            btnBackupSql.setEnabled(false);
            btnRestore.setEnabled(false);

            SwingWorker<DatabaseBackupService.RestoreResult, Void> worker = new SwingWorker<>() {
                @Override
                protected DatabaseBackupService.RestoreResult doInBackground() throws Exception {
                    return databaseBackupService.restoreBackup(sourceFile);
                }

                @Override
                protected void done() {
                    setCursor(Cursor.getDefaultCursor());
                    btnBackupBinary.setEnabled(true);
                    btnBackupSql.setEnabled(true);
                    btnRestore.setEnabled(true);
                    try {
                        DatabaseBackupService.RestoreResult res = get();
                        auditService.log(sessionManager.getCurrent().getUser().getId(), "DB_RESTORE",
                                "Restauracion desde: " + sourceFile.getName());

                        refreshUsers();
                        refreshCourses();
                        refreshStudentCourses();
                        refreshSystemDashboard();

                        JOptionPane.showMessageDialog(AdminPanel.this,
                                "Base de datos restaurada correctamente en " + res.getDurationMs() + " ms.",
                                "Restauracion Completada",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        auditService.log(sessionManager.getCurrent().getUser().getId(), "DB_RESTORE_FAIL", cause.getMessage());
                        JOptionPane.showMessageDialog(AdminPanel.this,
                                "Error al restaurar la base de datos:\n" + cause.getMessage(),
                                "Fallo de Restauracion",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }));

        return card;
    }

    private JPanel systemMetricCard(String label, JLabel value, JLabel detail, Color accent) {
        JPanel card = UiTheme.card(new BorderLayout(6, 5));
        JLabel heading = new JLabel(label);
        heading.setForeground(UiTheme.MUTED);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));
        detail.setForeground(UiTheme.MUTED);
        detail.setFont(detail.getFont().deriveFont(12f));
        JPanel accentLine = new JPanel();
        accentLine.setBackground(accent);
        accentLine.setPreferredSize(new Dimension(5, 5));
        card.add(accentLine, BorderLayout.NORTH);
        card.add(heading, BorderLayout.WEST);
        card.add(value, BorderLayout.CENTER);
        card.add(detail, BorderLayout.SOUTH);
        return card;
    }

    private JPanel systemStatusCard() {
        JPanel card = UiTheme.card(new BorderLayout(8, 8));
        card.add(UiTheme.title("Estado de servicios", 18f), BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(0, 1, 6, 6));
        rows.setOpaque(false);
        rows.add(systemStatusRow("Base de datos", databaseStatus));
        rows.add(systemStatusRow("Modelos interactivos", modelsStatus));
        rows.add(systemStatusRow("Entorno de ejecucion", runtimeStatus));
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JPanel systemStatusRow(String name, JLabel status) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(name);
        label.setForeground(UiTheme.MUTED);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 12f));
        row.add(label, BorderLayout.WEST);
        row.add(status, BorderLayout.EAST);
        return row;
    }

    private JPanel systemAlertsCard() {
        JPanel card = UiTheme.card(new BorderLayout(8, 8));
        card.add(UiTheme.title("Alertas y recomendaciones", 18f), BorderLayout.NORTH);
        systemAlertsPanel.setOpaque(false);
        systemAlertsPanel.setLayout(new BoxLayout(systemAlertsPanel, BoxLayout.Y_AXIS));
        card.add(systemAlertsPanel, BorderLayout.CENTER);
        return card;
    }

    private void refreshSystemDashboard() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
        systemLastUpdate.setText("Actualizando indicadores...");

        SwingWorker<SystemSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected SystemSnapshot doInBackground() {
                try {
                    List<User> users = userService.listUsers();
                    List<Course> courses = courseService.listAll();
                    List<StudentCourseAssignment> assignments = courseService.listStudentAssignments();
                    List<LogEntry> activity = auditService.listRecent(8);
                    boolean modelsReady = !Visor3DService.disponibles(Visor3DService.carpetaModelos()).isEmpty()
                            && Visor3DService.python() != null;
                    return new SystemSnapshot(users, courses, assignments, activity, modelsReady, null);
                } catch (Exception ex) {
                    return new SystemSnapshot(List.of(), List.of(), List.of(), List.of(), false, ex);
                }
            }

            @Override
            protected void done() {
                try {
                    applySystemSnapshot(get());
                } catch (Exception ex) {
                    applySystemSnapshot(new SystemSnapshot(
                            List.of(), List.of(), List.of(), List.of(), false, ex
                    ));
                }
            }
        };
        worker.execute();
    }

    private void applySystemSnapshot(SystemSnapshot snapshot) {
        boolean databaseReady = snapshot.error == null;
        long activeUsers = snapshot.users.stream().filter(User::isActive).count();
        long students = snapshot.users.stream()
                .filter(user -> user.getRoles().contains(RoleType.ESTUDIANTE))
                .count();

        systemUsersValue.setText(String.valueOf(snapshot.users.size()));
        systemActiveUsersValue.setText(String.valueOf(activeUsers));
        systemCoursesValue.setText(String.valueOf(snapshot.courses.size()));
        systemAssignmentsValue.setText(String.valueOf(snapshot.assignments.size()));
        systemUsersDetail.setText(students + " estudiante(s)");

        updateStatus(databaseStatus,
                databaseReady ? "Conectada" : "Sin conexion",
                databaseReady);
        updateStatus(modelsStatus,
                snapshot.modelsReady ? "Listos" : "Revisar entorno",
                snapshot.modelsReady);
        updateStatus(runtimeStatus,
                "Java " + System.getProperty("java.version"),
                true);

        systemActivityModel.setRowCount(0);
        for (LogEntry entry : snapshot.activity) {
            systemActivityModel.addRow(new Object[]{
                    friendlyAction(entry.getAction()),
                    entry.getDetail(),
                    formatSystemDate(entry.getCreatedAt())
            });
        }
        if (snapshot.activity.isEmpty()) {
            systemActivityModel.addRow(new Object[]{
                    "Sin actividad", "No hay eventos recientes para mostrar.", ""
            });
        }

        updateSystemAlerts(snapshot, activeUsers);
        systemLastUpdate.setText("Actualizado " + formatSystemDate(java.time.LocalDateTime.now()));
    }

    private void updateStatus(JLabel label, String text, boolean healthy) {
        label.setText(text);
        label.setForeground(healthy ? UiTheme.SUCCESS : UiTheme.PRIMARY);
    }

    private void updateSystemAlerts(SystemSnapshot snapshot, long activeUsers) {
        systemAlertsPanel.removeAll();
        if (snapshot.error != null) {
            addSystemAlert("No fue posible consultar PostgreSQL.", UiTheme.PRIMARY, UiTheme.PRIMARY_SOFT);
        } else {
            long inactiveUsers = snapshot.users.size() - activeUsers;
            if (inactiveUsers > 0) {
                addSystemAlert(inactiveUsers + " usuario(s) inactivos.",
                        UiTheme.WARNING, UiTheme.WARNING_SOFT);
            }

            Set<Long> assignedCourses = snapshot.assignments.stream()
                    .map(StudentCourseAssignment::getCourseId)
                    .collect(java.util.stream.Collectors.toSet());
            long emptyCourses = snapshot.courses.stream()
                    .filter(course -> !assignedCourses.contains(course.getId()))
                    .count();
            if (emptyCourses > 0) {
                addSystemAlert(emptyCourses + " materia(s) sin estudiantes asignados.",
                        UiTheme.INFO, UiTheme.INFO_SOFT);
            }
        }

        if (!snapshot.modelsReady) {
            addSystemAlert("El entorno de modelos 3D necesita revision.",
                    UiTheme.WARNING, UiTheme.WARNING_SOFT);
        }
        if (systemAlertsPanel.getComponentCount() == 0) {
            addSystemAlert("No hay alertas pendientes.", UiTheme.SUCCESS, UiTheme.SUCCESS_SOFT);
        }
        systemAlertsPanel.revalidate();
        systemAlertsPanel.repaint();
    }

    private void addSystemAlert(String text, Color foreground, Color background) {
        JLabel alert = new JLabel(text);
        alert.setOpaque(true);
        alert.setForeground(foreground);
        alert.setBackground(background);
        alert.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        alert.setAlignmentX(Component.LEFT_ALIGNMENT);
        alert.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        systemAlertsPanel.add(alert);
        systemAlertsPanel.add(Box.createVerticalStrut(5));
    }

    private String friendlyAction(String action) {
        if (action == null || action.isBlank()) {
            return "Actividad";
        }
        return switch (action) {
            case "LOGIN_OK" -> "Inicio de sesion";
            case "LOGIN_FAIL" -> "Acceso rechazado";
            case "LOGOUT" -> "Cierre de sesion";
            case "USER_CREATE" -> "Usuario creado";
            case "USER_UPDATE" -> "Usuario actualizado";
            case "STUDENT_COURSE_ASSIGN" -> "Materia asignada";
            case "STUDENT_COURSE_REMOVE" -> "Materia retirada";
            case "SIM_RUN" -> "Practica abierta";
            case "SIM_RUN_PY", "SIM_RUN_3D_PRACTICE", "SIM_RUN_PY_ADMIN" -> "Modelo 3D abierto";
            case "TEACHER_SIM_3D" -> "Vista previa 3D (docente)";
            case "SIM_3D_SESION" -> "Sesion en modelo 3D";
            case "SIM_CREATE" -> "Practica creada";
            default -> action.replace('_', ' ').toLowerCase(Locale.ROOT);
        };
    }

    private String formatSystemDate(java.time.LocalDateTime date) {
        if (date == null) {
            return "Sin fecha";
        }
        return date.format(java.time.format.DateTimeFormatter.ofPattern(
                "d MMM yyyy, h:mm a", Locale.forLanguageTag("es-CO")
        ));
    }

    private void selectAdminTab(String title) {
        int index = adminTabs.indexOfTab(title);
        if (index >= 0) {
            adminTabs.setSelectedIndex(index);
        }
    }

    private void refreshUsers() {
        usersModel.setRowCount(0);
        for (User u : userService.listUsers()) {
            String roles = String.join(", ", u.getRoles().stream().map(Enum::name).toList());
            usersModel.addRow(new Object[]{u.getId(), u.getUsername(), u.getEmail(), u.isActive(), roles});
        }
    }

    private void refreshCourses() {
        coursesModel.setRowCount(0);
        for (var course : courseService.listAll()) {
            User teacher = userService.findById(course.getTeacherId());
            String teacherName = teacher != null ? teacher.getUsername() : ("ID " + course.getTeacherId());
            coursesModel.addRow(new Object[]{
                    course.getId(),
                    course.getName(),
                    course.getDescription(),
                    teacherName,
                    course.getCreatedAt()
            });
        }
    }

    private void refreshStudentCourses() {
        studentCoursesModel.setRowCount(0);
        for (StudentCourseAssignment assignment : courseService.listStudentAssignments()) {
            studentCoursesModel.addRow(new Object[]{
                    assignment.getCourseId(),
                    assignment.getCourseName(),
                    assignment.getStudentId(),
                    assignment.getStudentUsername(),
                    assignment.getAssignedAt()
            });
        }
    }

    private void refreshPythonScripts() {
        pythonScriptsButtonsPanel.removeAll();

        Path scriptsFolder = resolvePythonScriptsFolder();
        List<Visor3DService.Modelo> models = Visor3DService.disponibles(scriptsFolder);
        if (scriptsFolder == null) {
            pythonScriptsSummary.setText("No se encontro carpeta 3D de Python.");
        } else if (models.isEmpty()) {
            pythonScriptsSummary.setText("No se encontraron los modelos de las cavidades en " + scriptsFolder + ".");
        } else {
            for (Visor3DService.Modelo model : models) {
                JButton button = new JButton(model.titulo());
                button.setToolTipText(model.script());
                Path script = scriptsFolder.resolve(model.script());
                button.addActionListener(e -> runPythonScript(script));
                pythonScriptsButtonsPanel.add(button);
            }
            pythonScriptsSummary.setText("Modelos 3D: " + models.size() + " | Carpeta: " + scriptsFolder);
        }
        pythonScriptsButtonsPanel.revalidate();
        pythonScriptsButtonsPanel.repaint();
    }

    private void runPythonScript(Path scriptPath) {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
        long adminId = sessionManager.getCurrent().getUser().getId();
        auditService.log(adminId, "SIM_RUN_PY_ADMIN", "Script " + scriptPath.getFileName());

        JDialog progress = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Abriendo " + Visor3DService.titulo(scriptPath.getFileName().toString()), false);
        progress.setLayout(new BorderLayout(8, 8));
        progress.add(new JLabel("  Abriendo el modelo 3D, por favor espera..."), BorderLayout.CENTER);
        progress.setSize(360, 110);
        progress.setLocationRelativeTo(this);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        SwingWorker<Visor3DService.Resultado, Void> worker = new SwingWorker<>() {
            private boolean withoutPython;

            @Override
            protected Visor3DService.Resultado doInBackground() {
                List<String> python = Visor3DService.python();
                if (python == null) {
                    withoutPython = true;
                    return null;
                }
                return Visor3DService.ejecutar(scriptPath, python, () -> SwingUtilities.invokeLater(progress::dispose));
            }

            @Override
            protected void done() {
                progress.dispose();
                Visor3DService.Resultado result;
                try {
                    result = get();
                } catch (Exception ex) {
                    result = null;
                }
                if (withoutPython) {
                    JOptionPane.showMessageDialog(AdminPanel.this,
                            "No se encontro un entorno Python con PyQt5, VTK y numpy.\n"
                                    + "Usa la opcion Instalar dependencias y vuelve a intentar.",
                            "Entorno 3D no disponible",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (result == null) {
                    return;
                }
                auditService.log(adminId, "SIM_3D_SESION", Visor3DService.resumen(scriptPath, result));
                if (!result.correcto()) {
                    String detail = "Codigo de salida: " + result.codigoSalida()
                            + (result.error() != null ? "\nError: " + result.error().getMessage() : "")
                            + "\n\n" + (result.salida().isBlank() ? "(Sin salida de consola)" : result.salida());
                    JTextArea area = new JTextArea(detail, 18, 78);
                    area.setEditable(false);
                    area.setCaretPosition(0);
                    JOptionPane.showMessageDialog(AdminPanel.this, new JScrollPane(area),
                            "El modelo 3D no se cerro correctamente", JOptionPane.WARNING_MESSAGE);
                }
            }
        };

        worker.execute();
        progress.setVisible(true);
    }

    private void installPythonDependencies() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
        Path scriptsFolder = resolvePythonScriptsFolder();
        if (scriptsFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "No se encontro la carpeta de scripts Python.",
                    "Carpeta no encontrada",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path requirements = scriptsFolder.resolve("requeriments.txt");
        if (!Files.exists(requirements)) {
            JOptionPane.showMessageDialog(this,
                    "No se encontro requeriments.txt en " + scriptsFolder,
                    "Dependencias no encontradas",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        PythonCommand pythonCommand = detectPythonInstallerCommand();
        if (pythonCommand == null) {
            JOptionPane.showMessageDialog(this,
                    "No se encontro una instalacion completa de Python.\nInstala Python y vuelve a intentar.",
                    "Python no disponible",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Visor3DService.olvidarPython();
        executeExternalCommand(
                pythonCommand.append("-m", "pip", "install", "-r", requirements.getFileName().toString()),
                scriptsFolder,
                "Instalando dependencias Python",
                "Instalacion de dependencias"
        );
    }

    private void openPythonScriptsFolder() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
        Path scriptsFolder = resolvePythonScriptsFolder();
        if (scriptsFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "No se encontro carpeta 3D de Python.",
                    "Carpeta no encontrada",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop no soportado en este entorno.");
            }
            Desktop.getDesktop().open(scriptsFolder.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo abrir la carpeta:\n" + scriptsFolder + "\n\n" + ex.getMessage(),
                    "Error al abrir carpeta",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path resolvePythonScriptsFolder() {
        return Visor3DService.carpetaModelos();
    }

    private PythonCommand detectPythonInstallerCommand() {
        for (PythonCommand candidate : pythonCommandCandidates()) {
            if (isCommandAvailable(candidate.command)) {
                return candidate;
            }
        }
        return null;
    }

    private List<PythonCommand> pythonCommandCandidates() {
        return Visor3DService.candidatosPython().stream().map(PythonCommand::new).toList();
    }

    private boolean isCommandAvailable(List<String> command) {
        return canRunPythonCode(command,
                "import pathlib, sys; raise SystemExit(0 if pathlib.Path(sys.base_prefix, 'Lib').is_dir() else 1)");
    }

    private boolean canRunPythonCode(List<String> command, String code) {
        List<String> probe = new ArrayList<>(command);
        probe.addAll(List.of("-c", code));
        try {
            Process process = new ProcessBuilder(probe)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void executeExternalCommand(List<String> command, Path workDir, String startTitle, String resultTitle) {
        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), startTitle, false);
        progressDialog.setLayout(new BorderLayout(8, 8));
        progressDialog.add(new JLabel("Procesando, por favor espera..."), BorderLayout.CENTER);
        progressDialog.setSize(320, 110);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        SwingWorker<CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CommandResult doInBackground() {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder(command)
                            .directory(workDir.toFile())
                            .redirectErrorStream(true);
                    Process process = processBuilder.start();
                    String output;
                    try (var input = process.getInputStream()) {
                        output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    int exit = process.waitFor();
                    return new CommandResult(exit, output, null);
                } catch (Exception ex) {
                    return new CommandResult(-1, "", ex);
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                CommandResult result;
                try {
                    result = get();
                } catch (Exception ex) {
                    result = new CommandResult(-1, "", ex);
                }

                if (result.error != null) {
                    JOptionPane.showMessageDialog(
                            AdminPanel.this,
                            "No se pudo ejecutar el comando:\n" + String.join(" ", command) + "\n\n" + result.error.getMessage(),
                            resultTitle,
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                StringBuilder text = new StringBuilder();
                text.append("Comando: ").append(String.join(" ", command)).append("\n");
                text.append("Directorio: ").append(workDir).append("\n");
                text.append("Codigo de salida: ").append(result.exitCode).append("\n\n");
                if (result.output == null || result.output.isBlank()) {
                    text.append("(Sin salida de consola)");
                } else {
                    text.append(result.output);
                }

                JTextArea area = new JTextArea(text.toString(), 22, 78);
                area.setEditable(false);
                area.setCaretPosition(0);
                JOptionPane.showMessageDialog(
                        AdminPanel.this,
                        new JScrollPane(area),
                        resultTitle,
                        result.exitCode == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
                );
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private static final class SystemSnapshot {
        private final List<User> users;
        private final List<Course> courses;
        private final List<StudentCourseAssignment> assignments;
        private final List<LogEntry> activity;
        private final boolean modelsReady;
        private final Exception error;

        private SystemSnapshot(List<User> users, List<Course> courses,
                               List<StudentCourseAssignment> assignments,
                               List<LogEntry> activity, boolean modelsReady, Exception error) {
            this.users = users;
            this.courses = courses;
            this.assignments = assignments;
            this.activity = activity;
            this.modelsReady = modelsReady;
            this.error = error;
        }
    }

    private static final class PythonCommand {
        private final List<String> command;

        private PythonCommand(List<String> command) {
            this.command = command;
        }

        private List<String> append(String... args) {
            List<String> fullCommand = new ArrayList<>(command);
            for (String arg : args) {
                fullCommand.add(arg);
            }
            return fullCommand;
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;
        private final Exception error;

        private CommandResult(int exitCode, String output, Exception error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }

    private static final class UserComboItem {
        private final long id;
        private final String username;

        private UserComboItem(long id, String username) {
            this.id = id;
            this.username = username;
        }

        @Override
        public String toString() {
            return username + " (" + id + ")";
        }
    }

    private static final class CourseComboItem {
        private final long id;
        private final String name;

        private CourseComboItem(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }
}

# -*- coding: utf-8 -*-
with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\AdminPanel.java', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update coursesModel declaration
old_table_decl = '    private final DefaultTableModel coursesModel = new DefaultTableModel(new Object[]{"ID", "Nombre", "Descripcion", "Administrador", "Creado"}, 0);'
new_table_decl = '    private final DefaultTableModel coursesModel = new DefaultTableModel(new Object[]{"ID", "Nombre", "Descripcion", "Docente Titular", "Creado"}, 0);'

assert old_table_decl in text, "old_table_decl not found"
text = text.replace(old_table_decl, new_table_decl, 1)

# 2. Update buildCoursesPanel
old_build_courses = """    private JPanel buildCoursesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JTable table = new JTable(coursesModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField name = new JTextField(18);
        JTextField desc = new JTextField(24);
        JButton create = new JButton("Crear Curso");
        JButton refresh = new JButton("Refrescar");

        top.add(new JLabel("Nombre"));
        top.add(name);
        top.add(new JLabel("Descripcion"));
        top.add(desc);
        top.add(create);
        top.add(refresh);
        panel.add(top, BorderLayout.NORTH);

        create.addActionListener(e -> runAdminAction(() -> {
            AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
            if (name.getText() == null || name.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del curso es obligatorio.");
            }
            long adminId = sessionManager.getCurrent().getUser().getId();
            long id = courseService.createCourse(name.getText().trim(), desc.getText().trim(), adminId);
            auditService.log(adminId, "COURSE_CREATE", "Curso ID " + id);
            name.setText("");
            desc.setText("");
            refreshCourses();
            JOptionPane.showMessageDialog(this, "Curso creado. ID: " + id);
        }));

        refresh.addActionListener(e -> runAdminAction(this::refreshCourses));
        return panel;
    }"""

new_build_courses = """    private JPanel buildCoursesPanel() {
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
                    "¿Estas seguro de eliminar la materia '" + courseName + "'?\\n"
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
    }"""

assert old_build_courses in text, "old_build_courses not found"
text = text.replace(old_build_courses, new_build_courses, 1)

# 3. Update refreshCourses to show teacher name
old_refresh_courses = """    private void refreshCourses() {
        coursesModel.setRowCount(0);
        for (var course : courseService.listAll()) {
            coursesModel.addRow(new Object[]{
                    course.getId(),
                    course.getName(),
                    course.getDescription(),
                    course.getTeacherId(),
                    course.getCreatedAt()
            });
        }
    }"""

new_refresh_courses = """    private void refreshCourses() {
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
    }"""

assert old_refresh_courses in text, "old_refresh_courses not found"
text = text.replace(old_refresh_courses, new_refresh_courses, 1)

with open(r'c:\Users\user\Desktop\proyecto_juan\src\app\ui\panels\AdminPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)

print("AdminPanel.java patched successfully")

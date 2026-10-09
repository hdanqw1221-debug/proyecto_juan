with open('src/app/ui/MainFrame.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

# 1. Update navigation items
old_nav = """        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            nav.add(makeSectionLabel("ADMIN"));
            nav.add(makeNavButton("Administracion", "ADMIN", "AD"));
            nav.add(makeNavButton("Gestion Academica", "TEACHER", "GA"));
        }"""

new_nav = """        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            nav.add(makeSectionLabel("ADMIN"));
            nav.add(makeNavButton("Administracion", "ADMIN", "AD"));
        }
        if (user.getRoles().contains(RoleType.ADMINISTRADOR) || user.getRoles().contains(RoleType.PROFESOR)) {
            nav.add(makeSectionLabel("DOCENCIA"));
            nav.add(makeNavButton("Gestion Academica", "TEACHER", "GA"));
        }"""

assert old_nav in text, 'old_nav not found'
text = text.replace(old_nav, new_nav, 1)

# 2. Update panel contents
old_panels = """        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            AdminPanel adminPanel = new AdminPanel(sessionManager, auditService, userService, roleService, courseService);
            content.add(adminPanel, "ADMIN");
            TeacherPanel teacherPanel = new TeacherPanel(sessionManager, auditService, courseService, examService,
                    simulationService, answerService, examAttemptService);
            content.add(teacherPanel, "TEACHER");
        }"""

new_panels = """        if (user.getRoles().contains(RoleType.ADMINISTRADOR)) {
            AdminPanel adminPanel = new AdminPanel(sessionManager, auditService, userService, roleService, courseService);
            content.add(adminPanel, "ADMIN");
        }
        if (user.getRoles().contains(RoleType.ADMINISTRADOR) || user.getRoles().contains(RoleType.PROFESOR)) {
            TeacherPanel teacherPanel = new TeacherPanel(sessionManager, auditService, courseService, examService,
                    simulationService, answerService, examAttemptService);
            content.add(teacherPanel, "TEACHER");
        }"""

assert old_panels in text, 'old_panels not found'
text = text.replace(old_panels, new_panels, 1)

# 3. Update dashboard refresh on click
old_click = """                if (cmd != null && (cmd.equals("DASH") || cmd.equals("ADMIN") || cmd.equals("TEACHER") || cmd.equals("STUDENT"))) {
                    btn.addActionListener(e -> cardLayout.show(content, cmd));
                    btn.addActionListener(e -> updateNavActive(cmd));
                }"""

new_click = """                if (cmd != null && (cmd.equals("DASH") || cmd.equals("ADMIN") || cmd.equals("TEACHER") || cmd.equals("STUDENT"))) {
                    btn.addActionListener(e -> {
                        if ("DASH".equals(cmd)) {
                            dashboard.refresh();
                        }
                        cardLayout.show(content, cmd);
                        updateNavActive(cmd);
                    });
                }"""

assert old_click in text, 'old_click not found'
text = text.replace(old_click, new_click, 1)

# 4. Update roleName in top bar
old_role_name = 'String roleName = user.getRoles().contains(RoleType.ADMINISTRADOR) ? "Administrador" : "Estudiante";'
new_role_name = """String roleName = user.getRoles().contains(RoleType.ADMINISTRADOR) ? "Administrador"
                : (user.getRoles().contains(RoleType.PROFESOR) ? "Docente" : "Estudiante");"""

assert old_role_name in text, 'old_role_name not found'
text = text.replace(old_role_name, new_role_name, 1)

with open('src/app/ui/MainFrame.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('MainFrame updated successfully!')

with open('src/app/ui/panels/AdminPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

old_chk = """        JCheckBox admin = new JCheckBox("ADMINISTRADOR");
        JCheckBox estudiante = new JCheckBox("ESTUDIANTE");"""

new_chk = """        JCheckBox admin = new JCheckBox("ADMINISTRADOR");
        JCheckBox profesor = new JCheckBox("PROFESOR");
        JCheckBox estudiante = new JCheckBox("ESTUDIANTE");"""

assert old_chk in text, 'old_chk not found'
text = text.replace(old_chk, new_chk, 1)

old_form_add = """        form.add(new JLabel("Usuario ID"));
        form.add(userIdField);
        form.add(admin);
        form.add(estudiante);
        form.add(assign);"""

new_form_add = """        form.add(new JLabel("Usuario ID"));
        form.add(userIdField);
        form.add(admin);
        form.add(profesor);
        form.add(estudiante);
        form.add(assign);"""

assert old_form_add in text, 'old_form_add not found'
text = text.replace(old_form_add, new_form_add, 1)

old_roles_assign = """            if (admin.isSelected()) roles.add(RoleType.ADMINISTRADOR);
            if (estudiante.isSelected()) roles.add(RoleType.ESTUDIANTE);"""

new_roles_assign = """            if (admin.isSelected()) roles.add(RoleType.ADMINISTRADOR);
            if (profesor.isSelected()) roles.add(RoleType.PROFESOR);
            if (estudiante.isSelected()) roles.add(RoleType.ESTUDIANTE);"""

assert old_roles_assign in text, 'old_roles_assign not found'
text = text.replace(old_roles_assign, new_roles_assign, 1)

with open('src/app/ui/panels/AdminPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('AdminPanel updated successfully!')

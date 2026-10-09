with open('src/app/ui/panels/TeacherPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

old_req = """    private void requireTeacher() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR);
    }"""

new_req = """    private void requireTeacher() {
        AccessControl.requireRole(sessionManager.getCurrent(), RoleType.ADMINISTRADOR, RoleType.PROFESOR);
    }"""

assert old_req in text, 'old_req not found'
text = text.replace(old_req, new_req, 1)

with open('src/app/ui/panels/TeacherPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('TeacherPanel requireTeacher updated successfully!')

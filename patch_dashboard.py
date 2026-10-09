import re

with open('src/app/ui/panels/DashboardPanel.java', 'r', encoding='utf-8') as f:
    content = f.read()

start_idx = content.find('JPanel detailGrid = new JPanel(new GridLayout(1, 2, 14, 14));')
end_idx = content.find('add(center, BorderLayout.CENTER);')

if start_idx != -1 and end_idx != -1:
    new_code = """
        JPanel detailGrid = new JPanel(new GridLayout(1, 2, 14, 14));
        detailGrid.setBackground(app.ui.UiTheme.BACKGROUND);
        detailGrid.add(rolesCard(user));
        
        // Grafico circular en lugar de solo texto plano de actividad
        app.ui.PieChartPanel pieChart = new app.ui.PieChartPanel("Distribucion de Actividad");
        int courses = countCourses(user, courseService);
        int exams = countExams(user, courseService, examService);
        int sims = countSimulations(user, courseService, simulationService);
        
        pieChart.addSlice("Cursos", courses, new Color(41, 128, 185)); // Azul
        pieChart.addSlice("Examenes", exams, new Color(39, 174, 96)); // Verde
        pieChart.addSlice("Simulaciones", sims, new Color(243, 156, 18)); // Naranja
        
        JPanel pieCard = app.ui.UiTheme.card(new BorderLayout(12, 12));
        pieCard.add(pieChart, BorderLayout.CENTER);
        detailGrid.add(pieCard);
        
        center.add(detailGrid, BorderLayout.CENTER);

        // Alertas de vencimiento
        JPanel alertsPanel = new JPanel();
        alertsPanel.setLayout(new BoxLayout(alertsPanel, BoxLayout.Y_AXIS));
        alertsPanel.setBackground(app.ui.UiTheme.BACKGROUND);
        
        if (user.getRoles().contains(app.model.RoleType.ESTUDIANTE)) {
            java.util.List<app.model.Exam> upcomingExams = new java.util.ArrayList<>();
            try {
                for (Long courseId : visibleCourseIds(user, courseService)) {
                    for (app.model.Exam ex : examService.listExamsByCourse(courseId)) {
                        if (ex.isActive() && ex.getDueAt() != null) {
                            long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDateTime.now(), ex.getDueAt());
                            if (days >= 0 && days <= 7) {
                                upcomingExams.add(ex);
                            }
                        }
                    }
                }
            } catch (Exception ex) {}
            
            if (!upcomingExams.isEmpty()) {
                JPanel alertsCard = app.ui.UiTheme.card(new BorderLayout(12, 12));
                alertsCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 6, 0, 0, new Color(231, 76, 60)), // Borde rojo
                        BorderFactory.createEmptyBorder(12, 16, 12, 16)
                ));
                alertsCard.add(new JLabel("<html><b style='color:red;'>⚠️ Alerta: Tienes " + upcomingExams.size() + " examen(es) que vencen en los proximos 7 dias.</b></html>"), BorderLayout.CENTER);
                alertsPanel.add(alertsCard);
                alertsPanel.add(Box.createVerticalStrut(10));
            }
        }
        center.add(alertsPanel, BorderLayout.SOUTH);
"""
    content = content[:start_idx] + new_code + content[end_idx:]

    with open('src/app/ui/panels/DashboardPanel.java', 'w', encoding='utf-8') as f:
        f.write(content)
    print("DashboardPanel refactored successfully.")
else:
    print("Could not find start index in DashboardPanel.")

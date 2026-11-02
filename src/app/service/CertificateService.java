package app.service;

import app.model.Course;
import app.model.Exam;
import app.model.ExamAttempt;
import app.model.User;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CertificateService {

    public static final double PASSING_SCORE = 3.0;

    public boolean isPassing(ExamAttempt attempt) {
        return attempt != null && attempt.getTotalScore() != null && attempt.getTotalScore() >= PASSING_SCORE;
    }

    public String generateVerificationCode(User student, Exam exam, ExamAttempt attempt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = (student != null ? student.getId() : 0) + ":" +
                    (exam != null ? exam.getId() : 0) + ":" +
                    (attempt != null ? attempt.getId() : 0) + ":" +
                    (attempt != null && attempt.getTotalScore() != null ? attempt.getTotalScore() : 0);
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02X", hash[i]));
            }
            int year = (attempt != null && attempt.getEndAt() != null)
                    ? attempt.getEndAt().getYear()
                    : LocalDateTime.now().getYear();
            return "UP-" + year + "-" + hex;
        } catch (Exception e) {
            return "UP-2026-CERT-" + (attempt != null ? attempt.getId() : "0000");
        }
    }

    public String getDistinctionLabel(double score) {
        if (score >= 4.8) {
            return "Aprobado con Mención de Honor y Excelencia Summa Cum Laude";
        } else if (score >= 4.5) {
            return "Aprobado con Distinción y Mención Sobresaliente";
        } else if (score >= 4.0) {
            return "Aprobado con Calificación Sobresaliente";
        } else {
            return "Aprobado Satisfactoriamente";
        }
    }

    public String buildCertificateHtml(User student, Course course, Exam exam, ExamAttempt attempt) {
        double score = (attempt != null && attempt.getTotalScore() != null) ? attempt.getTotalScore() : 0.0;
        String distinction = getDistinctionLabel(score);
        String verificationCode = generateVerificationCode(student, exam, attempt);

        LocalDateTime issueDate = (attempt != null && attempt.getEndAt() != null)
                ? attempt.getEndAt()
                : LocalDateTime.now();

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d", Locale.forLanguageTag("es-CO"));
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("es-CO"));
        DateTimeFormatter yearFmt = DateTimeFormatter.ofPattern("yyyy", Locale.forLanguageTag("es-CO"));
        DateTimeFormatter fullDateFmt = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-CO"));

        String day = issueDate.format(dayFmt);
        String month = issueDate.format(monthFmt);
        String year = issueDate.format(yearFmt);
        String formattedFullDate = issueDate.format(fullDateFmt);

        String studentName = (student != null && student.getUsername() != null && !student.getUsername().isBlank())
                ? student.getUsername().toUpperCase()
                : "ESTUDIANTE REGISTRADO";
        String studentEmail = (student != null && student.getEmail() != null) ? student.getEmail() : "usuario@unipamplona.edu.co";
        String examTitle = (exam != null && exam.getTitle() != null) ? exam.getTitle() : "Evaluación de Competencias";
        String courseName = (course != null && course.getName() != null) ? course.getName() : "Curso Académico";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"es\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Certificado de Aprobación - " + escapeHtml(studentName) + "</title>\n" +
                "  <style>\n" +
                "    @import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@600;700;800;900&family=Montserrat:wght@400;500;600;700&family=Playfair+Display:ital,wght@0,600;0,700;1,400&family=Alex+Brush&display=swap');\n" +
                "\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    body {\n" +
                "      background-color: #1a1a1f;\n" +
                "      font-family: 'Montserrat', system-ui, -apple-system, sans-serif;\n" +
                "      color: #1e1e24;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: center;\n" +
                "      min-height: 100vh;\n" +
                "      padding: 24px 12px;\n" +
                "    }\n" +
                "\n" +
                "    /* Barra de utilidades flotante */\n" +
                "    .toolbar {\n" +
                "      background: #25252d;\n" +
                "      padding: 12px 24px;\n" +
                "      border-radius: 50px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 16px;\n" +
                "      margin-bottom: 24px;\n" +
                "      box-shadow: 0 8px 24px rgba(0,0,0,0.35);\n" +
                "      border: 1px solid rgba(255,255,255,0.08);\n" +
                "    }\n" +
                "    .toolbar-info {\n" +
                "      color: #e0e0e6;\n" +
                "      font-size: 13px;\n" +
                "      font-weight: 500;\n" +
                "    }\n" +
                "    .btn-print {\n" +
                "      background: linear-gradient(135deg, #b12d37 0%, #851c24 100%);\n" +
                "      color: white;\n" +
                "      border: none;\n" +
                "      padding: 10px 22px;\n" +
                "      border-radius: 30px;\n" +
                "      font-weight: 600;\n" +
                "      font-size: 14px;\n" +
                "      cursor: pointer;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "      transition: transform 0.15s ease, box-shadow 0.15s ease;\n" +
                "    }\n" +
                "    .btn-print:hover {\n" +
                "      transform: translateY(-1px);\n" +
                "      box-shadow: 0 4px 14px rgba(177,45,55,0.4);\n" +
                "    }\n" +
                "\n" +
                "    /* Hoja del Diploma (A4 / Carta Apaisado) */\n" +
                "    .cert-sheet {\n" +
                "      width: 1040px;\n" +
                "      min-height: 735px;\n" +
                "      background: #ffffff;\n" +
                "      padding: 26px;\n" +
                "      box-shadow: 0 20px 50px rgba(0,0,0,0.5);\n" +
                "      position: relative;\n" +
                "      background-image: radial-gradient(#faf8f5 25%, #ffffff 85%);\n" +
                "    }\n" +
                "\n" +
                "    /* Marco ornamental */\n" +
                "    .cert-border-outer {\n" +
                "      border: 6px solid #8b1e2b;\n" +
                "      padding: 8px;\n" +
                "      height: 100%;\n" +
                "      position: relative;\n" +
                "    }\n" +
                "    .cert-border-inner {\n" +
                "      border: 2px solid #c59b27;\n" +
                "      padding: 30px 42px;\n" +
                "      height: 100%;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: center;\n" +
                "      text-align: center;\n" +
                "      position: relative;\n" +
                "    }\n" +
                "\n" +
                "    /* Esquinas ornamentales SVG */\n" +
                "    .corner-decor {\n" +
                "      position: absolute;\n" +
                "      width: 48px;\n" +
                "      height: 48px;\n" +
                "      pointer-events: none;\n" +
                "    }\n" +
                "    .corner-tl { top: -2px; left: -2px; }\n" +
                "    .corner-tr { top: -2px; right: -2px; transform: scaleX(-1); }\n" +
                "    .corner-bl { bottom: -2px; left: -2px; transform: scaleY(-1); }\n" +
                "    .corner-br { bottom: -2px; right: -2px; transform: scale(-1); }\n" +
                "\n" +
                "    /* Cabecera institucional */\n" +
                "    .inst-header {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 16px;\n" +
                "      margin-bottom: 12px;\n" +
                "    }\n" +
                "    .crest-icon {\n" +
                "      width: 58px;\n" +
                "      height: 58px;\n" +
                "    }\n" +
                "    .inst-text {\n" +
                "      text-align: left;\n" +
                "    }\n" +
                "    .inst-republic {\n" +
                "      font-size: 10px;\n" +
                "      letter-spacing: 2.5px;\n" +
                "      font-weight: 700;\n" +
                "      color: #696971;\n" +
                "      text-transform: uppercase;\n" +
                "    }\n" +
                "    .inst-name {\n" +
                "      font-family: 'Cinzel', serif;\n" +
                "      font-size: 20px;\n" +
                "      font-weight: 800;\n" +
                "      color: #8b1e2b;\n" +
                "      letter-spacing: 1.5px;\n" +
                "      line-height: 1.1;\n" +
                "    }\n" +
                "    .inst-sub {\n" +
                "      font-size: 9.5px;\n" +
                "      color: #888;\n" +
                "      letter-spacing: 0.8px;\n" +
                "      font-weight: 500;\n" +
                "    }\n" +
                "\n" +
                "    /* Titulo del certificado */\n" +
                "    .cert-heading {\n" +
                "      margin: 10px 0 6px 0;\n" +
                "    }\n" +
                "    .cert-title {\n" +
                "      font-family: 'Cinzel', serif;\n" +
                "      font-size: 26px;\n" +
                "      font-weight: 800;\n" +
                "      color: #1a1a1f;\n" +
                "      letter-spacing: 3px;\n" +
                "      text-transform: uppercase;\n" +
                "      position: relative;\n" +
                "      display: inline-block;\n" +
                "      padding-bottom: 6px;\n" +
                "    }\n" +
                "    .cert-title::after {\n" +
                "      content: '';\n" +
                "      position: absolute;\n" +
                "      bottom: 0;\n" +
                "      left: 20%;\n" +
                "      right: 20%;\n" +
                "      height: 2px;\n" +
                "      background: linear-gradient(90deg, transparent, #c59b27, transparent);\n" +
                "    }\n" +
                "    .cert-subtitle {\n" +
                "      font-family: 'Playfair Display', serif;\n" +
                "      font-style: italic;\n" +
                "      color: #7a6e5d;\n" +
                "      font-size: 13.5px;\n" +
                "      margin-top: 4px;\n" +
                "    }\n" +
                "\n" +
                "    /* Nombre del estudiante */\n" +
                "    .statement-intro {\n" +
                "      font-size: 12px;\n" +
                "      color: #555;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 2px;\n" +
                "      margin-top: 18px;\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "    .student-name {\n" +
                "      font-family: 'Playfair Display', Georgia, serif;\n" +
                "      font-size: 32px;\n" +
                "      font-weight: 700;\n" +
                "      color: #8b1e2b;\n" +
                "      margin: 8px 0 3px 0;\n" +
                "      letter-spacing: 0.5px;\n" +
                "      text-shadow: 0 1px 1px rgba(0,0,0,0.06);\n" +
                "    }\n" +
                "    .student-id {\n" +
                "      font-size: 12px;\n" +
                "      color: #777;\n" +
                "      font-weight: 500;\n" +
                "      margin-bottom: 14px;\n" +
                "    }\n" +
                "\n" +
                "    /* Descripcion de la evaluacion y curso */\n" +
                "    .cert-body {\n" +
                "      font-size: 13.5px;\n" +
                "      color: #3d3d42;\n" +
                "      max-width: 740px;\n" +
                "      line-height: 1.6;\n" +
                "      margin-bottom: 16px;\n" +
                "    }\n" +
                "    .highlight-exam {\n" +
                "      font-weight: 700;\n" +
                "      color: #1a1a1f;\n" +
                "      font-size: 15px;\n" +
                "    }\n" +
                "    .highlight-course {\n" +
                "      font-weight: 700;\n" +
                "      color: #8b1e2b;\n" +
                "    }\n" +
                "\n" +
                "    /* Pastilla de calificacion */\n" +
                "    .badge-container {\n" +
                "      margin: 4px 0 16px 0;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: center;\n" +
                "      gap: 4px;\n" +
                "    }\n" +
                "    .score-badge {\n" +
                "      background: linear-gradient(135deg, #fdf9ee 0%, #f6ebc9 100%);\n" +
                "      border: 1.5px solid #d4af37;\n" +
                "      padding: 6px 20px;\n" +
                "      border-radius: 30px;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "      box-shadow: 0 2px 6px rgba(197,155,39,0.18);\n" +
                "    }\n" +
                "    .score-title {\n" +
                "      font-size: 11px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 1.5px;\n" +
                "      color: #7a5f15;\n" +
                "      text-transform: uppercase;\n" +
                "    }\n" +
                "    .score-num {\n" +
                "      font-size: 17px;\n" +
                "      font-weight: 800;\n" +
                "      color: #8b1e2b;\n" +
                "    }\n" +
                "    .distinction-text {\n" +
                "      font-family: 'Playfair Display', serif;\n" +
                "      font-style: italic;\n" +
                "      color: #8b1e2b;\n" +
                "      font-size: 13px;\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "\n" +
                "    /* Fecha y lugar */\n" +
                "    .date-location {\n" +
                "      font-size: 12.5px;\n" +
                "      color: #555;\n" +
                "      margin-bottom: 24px;\n" +
                "    }\n" +
                "\n" +
                "    /* Firmas y sello central */\n" +
                "    .signatures-row {\n" +
                "      width: 100%;\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: flex-end;\n" +
                "      padding: 0 40px;\n" +
                "      margin-bottom: 20px;\n" +
                "    }\n" +
                "    .signature-block {\n" +
                "      width: 240px;\n" +
                "      text-align: center;\n" +
                "    }\n" +
                "    .signature-script {\n" +
                "      font-family: 'Alex Brush', cursive;\n" +
                "      font-size: 34px;\n" +
                "      color: #1a2d54;\n" +
                "      line-height: 1;\n" +
                "      height: 38px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "    }\n" +
                "    .signature-line {\n" +
                "      height: 1px;\n" +
                "      background: #999;\n" +
                "      margin: 4px auto 6px auto;\n" +
                "      width: 90%;\n" +
                "    }\n" +
                "    .signature-role {\n" +
                "      font-size: 11px;\n" +
                "      font-weight: 700;\n" +
                "      color: #222;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 0.5px;\n" +
                "    }\n" +
                "    .signature-dept {\n" +
                "      font-size: 10px;\n" +
                "      color: #777;\n" +
                "    }\n" +
                "\n" +
                "    /* Medalla / Sello central */\n" +
                "    .seal-block {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    .seal-graphic {\n" +
                "      width: 86px;\n" +
                "      height: 86px;\n" +
                "    }\n" +
                "\n" +
                "    /* Pie de seguridad y QR */\n" +
                "    .security-footer {\n" +
                "      width: 100%;\n" +
                "      border-top: 1px solid #e4decb;\n" +
                "      padding-top: 10px;\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      font-size: 10.5px;\n" +
                "      color: #777;\n" +
                "    }\n" +
                "    .qr-and-code {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 12px;\n" +
                "      text-align: left;\n" +
                "    }\n" +
                "    .qr-svg {\n" +
                "      width: 44px;\n" +
                "      height: 44px;\n" +
                "      background: white;\n" +
                "      border: 1px solid #ddd;\n" +
                "      padding: 2px;\n" +
                "    }\n" +
                "    .code-label {\n" +
                "      font-weight: 700;\n" +
                "      color: #8b1e2b;\n" +
                "      letter-spacing: 0.5px;\n" +
                "    }\n" +
                "    .legal-notice {\n" +
                "      text-align: right;\n" +
                "      max-width: 380px;\n" +
                "      line-height: 1.3;\n" +
                "    }\n" +
                "\n" +
                "    /* Estilos para impresion y PDF */\n" +
                "    @media print {\n" +
                "      body {\n" +
                "        background: none;\n" +
                "        padding: 0;\n" +
                "      }\n" +
                "      .toolbar { display: none !important; }\n" +
                "      .cert-sheet {\n" +
                "        box-shadow: none;\n" +
                "        width: 100%;\n" +
                "        min-height: 98vh;\n" +
                "        padding: 0;\n" +
                "      }\n" +
                "      @page {\n" +
                "        size: landscape;\n" +
                "        margin: 1cm;\n" +
                "      }\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <div class=\"toolbar\">\n" +
                "    <div class=\"toolbar-info\">\n" +
                "      Certificado Académico &bull; <strong>" + escapeHtml(examTitle) + "</strong>\n" +
                "    </div>\n" +
                "    <button class=\"btn-print\" onclick=\"window.print()\">\n" +
                "      <svg width=\"18\" height=\"18\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" viewBox=\"0 0 24 24\">\n" +
                "        <path d=\"M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2\"/>\n" +
                "        <path d=\"M6 14h12v8H6z\"/>\n" +
                "      </svg>\n" +
                "      Imprimir o Guardar en PDF\n" +
                "    </button>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"cert-sheet\">\n" +
                "    <div class=\"cert-border-outer\">\n" +
                "      <div class=\"cert-border-inner\">\n" +
                "\n" +
                "        <!-- Esquinas ornamentales -->\n" +
                "        <svg class=\"corner-decor corner-tl\" viewBox=\"0 0 100 100\" fill=\"none\" stroke=\"#c59b27\" stroke-width=\"4\">\n" +
                "          <path d=\"M 0,40 L 0,0 L 40,0 M 0,0 L 30,30 M 10,0 L 0,10\" />\n" +
                "        </svg>\n" +
                "        <svg class=\"corner-decor corner-tr\" viewBox=\"0 0 100 100\" fill=\"none\" stroke=\"#c59b27\" stroke-width=\"4\">\n" +
                "          <path d=\"M 0,40 L 0,0 L 40,0 M 0,0 L 30,30 M 10,0 L 0,10\" />\n" +
                "        </svg>\n" +
                "        <svg class=\"corner-decor corner-bl\" viewBox=\"0 0 100 100\" fill=\"none\" stroke=\"#c59b27\" stroke-width=\"4\">\n" +
                "          <path d=\"M 0,40 L 0,0 L 40,0 M 0,0 L 30,30 M 10,0 L 0,10\" />\n" +
                "        </svg>\n" +
                "        <svg class=\"corner-decor corner-br\" viewBox=\"0 0 100 100\" fill=\"none\" stroke=\"#c59b27\" stroke-width=\"4\">\n" +
                "          <path d=\"M 0,40 L 0,0 L 40,0 M 0,0 L 30,30 M 10,0 L 0,10\" />\n" +
                "        </svg>\n" +
                "\n" +
                "        <!-- Encabezado institucional -->\n" +
                "        <div class=\"inst-header\">\n" +
                "          <svg class=\"crest-icon\" viewBox=\"0 0 100 100\" fill=\"none\">\n" +
                "            <circle cx=\"50\" cy=\"50\" r=\"45\" fill=\"#8b1e2b\" stroke=\"#c59b27\" stroke-width=\"3\"/>\n" +
                "            <path d=\"M50 15 L78 30 V65 L50 85 L22 65 V30 Z\" fill=\"#7a1622\" stroke=\"#f1d779\" stroke-width=\"2\"/>\n" +
                "            <text x=\"50\" y=\"58\" font-family=\"Cinzel, serif\" font-size=\"22\" font-weight=\"bold\" fill=\"#ffffff\" text-anchor=\"middle\">UP</text>\n" +
                "          </svg>\n" +
                "          <div class=\"inst-text\">\n" +
                "            <div class=\"inst-republic\">República de Colombia &bull; Ministerio de Educación Nacional</div>\n" +
                "            <div class=\"inst-name\">UNIVERSIDAD DE PAMPLONA</div>\n" +
                "            <div class=\"inst-sub\">Institución de Educación Superior sujeta a inspección y vigilancia por el MEN</div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Titulo del Certificado -->\n" +
                "        <div class=\"cert-heading\">\n" +
                "          <h1 class=\"cert-title\">Certificado de Aprobación</h1>\n" +
                "          <div class=\"cert-subtitle\">Acreditación de suficiencia y excelencia académica institucional</div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"statement-intro\">El claustro evaluador hace constar que el(la) estudiante:</div>\n" +
                "        <div class=\"student-name\">" + escapeHtml(studentName) + "</div>\n" +
                "        <div class=\"student-id\">Usuario: <strong>" + escapeHtml(student != null ? student.getUsername() : "") + "</strong> &bull; Correo: " + escapeHtml(studentEmail) + "</div>\n" +
                "\n" +
                "        <div class=\"cert-body\">\n" +
                "          Ha sustentado, acreditado y aprobado satisfactoriamente los criterios evaluativos de la prueba de conocimientos\n" +
                "          <span class=\"highlight-exam\">\"" + escapeHtml(examTitle) + "\"</span>, inscrita y validada en el marco de la asignatura\n" +
                "          <span class=\"highlight-course\">\"" + escapeHtml(courseName) + "\"</span>.\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Calificacion -->\n" +
                "        <div class=\"badge-container\">\n" +
                "          <div class=\"score-badge\">\n" +
                "            <span class=\"score-title\">Calificación Final:</span>\n" +
                "            <span class=\"score-num\">" + String.format(Locale.ROOT, "%.1f", score) + " / 5.0</span>\n" +
                "          </div>\n" +
                "          <div class=\"distinction-text\">" + escapeHtml(distinction) + "</div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Fecha y lugar -->\n" +
                "        <div class=\"date-location\">\n" +
                "          Dado y acreditado en Pamplona, Norte de Santander, a los " + day + " días del mes de " + month + " de " + year + ".\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Firmas y Sello -->\n" +
                "        <div class=\"signatures-row\">\n" +
                "          <div class=\"signature-block\">\n" +
                "            <div class=\"signature-script\">Prof. Evaluador</div>\n" +
                "            <div class=\"signature-line\"></div>\n" +
                "            <div class=\"signature-role\">Docente Evaluador Titular</div>\n" +
                "            <div class=\"signature-dept\">Facultad Académica</div>\n" +
                "          </div>\n" +
                "\n" +
                "          <div class=\"seal-block\">\n" +
                "            <svg class=\"seal-graphic\" viewBox=\"0 0 120 120\">\n" +
                "              <defs>\n" +
                "                <linearGradient id=\"sealGold\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\n" +
                "                  <stop offset=\"0%\" stop-color=\"#f3e5ab\"/>\n" +
                "                  <stop offset=\"50%\" stop-color=\"#c59b27\"/>\n" +
                "                  <stop offset=\"100%\" stop-color=\"#8b6814\"/>\n" +
                "                </linearGradient>\n" +
                "              </defs>\n" +
                "              <circle cx=\"60\" cy=\"60\" r=\"54\" fill=\"url(#sealGold)\" stroke=\"#8b1e2b\" stroke-width=\"2\"/>\n" +
                "              <circle cx=\"60\" cy=\"60\" r=\"46\" fill=\"none\" stroke=\"#ffffff\" stroke-width=\"1.5\" stroke-dasharray=\"3,3\"/>\n" +
                "              <circle cx=\"60\" cy=\"60\" r=\"38\" fill=\"#8b1e2b\"/>\n" +
                "              <path d=\"M60 32 L67 46 L82 48 L71 58 L74 73 L60 65 L46 73 L49 58 L38 48 L53 46 Z\" fill=\"url(#sealGold)\"/>\n" +
                "            </svg>\n" +
                "          </div>\n" +
                "\n" +
                "          <div class=\"signature-block\">\n" +
                "            <div class=\"signature-script\">Dir. Académica</div>\n" +
                "            <div class=\"signature-line\"></div>\n" +
                "            <div class=\"signature-role\">Dirección Académica UP</div>\n" +
                "            <div class=\"signature-dept\">Campus Digital Certificado</div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Pie de seguridad y QR -->\n" +
                "        <div class=\"security-footer\">\n" +
                "          <div class=\"qr-and-code\">\n" +
                "            <svg class=\"qr-svg\" viewBox=\"0 0 29 29\">\n" +
                "              <!-- Representacion vectorial QR estandar -->\n" +
                "              <rect width=\"29\" height=\"29\" fill=\"white\"/>\n" +
                "              <path d=\"M2 2h7v7H2zM4 4h3v3H4zM20 2h7v7h-7zM22 4h3v3h-3zM2 20h7v7H2zM4 22h3v3H4zM12 2h5v2h-5zM12 6h2v3h-2zM16 6h3v3h-3zM2 11h2v3H2zM6 11h3v2H6zM11 11h3v3h-3zM16 11h2v3h-2zM20 11h4v2h-4zM26 11h1v4h-1zM11 16h3v2h-3zM16 16h2v3h-2zM20 16h2v2h-2zM24 16h3v3h-3zM12 20h2v4h-2zM16 20h3v2h-3zM21 20h3v4h-3zM26 22h1v5h-1zM12 26h5v1h-5zM19 25h3v2h-3z\" fill=\"#1a1a1f\"/>\n" +
                "            </svg>\n" +
                "            <div>\n" +
                "              <div>CÓDIGO ÚNICO DIGITAL (CUD): <span class=\"code-label\">" + verificationCode + "</span></div>\n" +
                "              <div style=\"font-size:9.5px; color:#888;\">Emisión: " + formattedFullDate + " &bull; Intento #" + (attempt != null ? attempt.getId() : 0) + "</div>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <div class=\"legal-notice\">\n" +
                "            Documento académico digital verificable. La autenticidad e integridad de este certificado puede ser validada en el sistema de gestión académica institucional.\n" +
                "          </div>\n" +
                "        </div>\n" +
                "\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "</body>\n" +
                "</html>";
    }

    public File generateCertificateHtmlFile(User student, Course course, Exam exam, ExamAttempt attempt, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        String html = buildCertificateHtml(student, course, exam, attempt);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
            writer.write(html);
        }
        return targetFile;
    }

    public File exportAndOpenCertificate(User student, Course course, Exam exam, ExamAttempt attempt) throws IOException {
        File reportsDir = new File("reports/certificados");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        String studentClean = (student != null && student.getUsername() != null)
                ? student.getUsername().replaceAll("[^a-zA-Z0-9_.-]", "_")
                : "estudiante";
        long attemptId = (attempt != null) ? attempt.getId() : System.currentTimeMillis();
        File targetFile = new File(reportsDir, "certificado_" + studentClean + "_intento_" + attemptId + ".html");

        generateCertificateHtmlFile(student, course, exam, attempt, targetFile);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(targetFile.toURI());
            } catch (Exception ignored) {
            }
        }

        return targetFile;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

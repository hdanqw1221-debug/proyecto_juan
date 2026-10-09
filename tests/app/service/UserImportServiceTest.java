package app.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class UserImportServiceTest {

    @Test
    public void testTemplateContent() {
        String template = UserImportService.getTemplateContent();
        assertNotNull(template);
        assertTrue(template.contains("usuario"));
        assertTrue(template.contains("email"));
        assertTrue(template.contains("password"));
        assertTrue(template.contains("rol"));
    }

    @Test
    public void testInvalidRowsHandling() throws IOException {
        // Simular CSV con contrasena debil y correo invalido sin golpear la BD necesariamente
        String csv = "usuario,email,password,rol,activo\n" +
                "user_debil,user@mail.com,123,ESTUDIANTE,true\n" +
                "user_sin_mail,correo_invalido,Clave123,ESTUDIANTE,true\n";

        // Mock o dummy UserService pasando null en UserDao ya que debe fallar antes de tocar la BD
        UserService dummyService = new UserService(null, null) {
            // No implementado
        };
        UserImportService importer = new UserImportService(dummyService);

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        UserImportService.ImportResult result = importer.importFromStream(in);

        assertEquals(2, result.getTotalProcessed());
        assertEquals(0, result.getSuccessCount());
        assertEquals(2, result.getFailedCount());
        assertTrue(result.getErrors().get(0).contains("debil"));
        assertTrue(result.getErrors().get(1).contains("invalido"));
    }
}

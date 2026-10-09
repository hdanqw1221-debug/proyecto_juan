package app.service;

import app.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseBackupServiceTest {

    @Test
    public void testUrlParsingAndConfiguration() {
        AppConfig config = AppConfig.load("config/app.properties");
        DatabaseBackupService backupService = new DatabaseBackupService(config);

        assertEquals("localhost", backupService.getDbHost());
        assertEquals(5432, backupService.getDbPort());
        assertEquals("gestion", backupService.getDbName());
        assertEquals("postgres", backupService.getDbUser());
    }

    @Test
    public void testPgDumpDetection() {
        AppConfig config = AppConfig.load("config/app.properties");
        DatabaseBackupService backupService = new DatabaseBackupService(config);

        assertTrue(backupService.isPgDumpAvailable(), "pg_dump debe ser detectado en el sistema");
        assertNotNull(backupService.getPgDumpPath());
        assertTrue(new File(backupService.getPgDumpPath()).exists());
    }

    @Test
    public void testBackupResultFormatting() {
        DatabaseBackupService.BackupResult result = new DatabaseBackupService.BackupResult(
                true, "Ok", new File("dummy.backup"), 1024 * 1024 * 2 + 512 * 1024, 150
        );
        assertTrue(result.getFormattedSize().contains("MB"));
    }
}

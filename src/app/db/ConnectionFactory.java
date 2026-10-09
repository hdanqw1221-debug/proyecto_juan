package app.db;

import app.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {
    private final AppConfig config;

    public ConnectionFactory(AppConfig config) {
        this.config = config;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver PostgreSQL no encontrado. Agrega el jar en lib/", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                config.getDbUrl(),
                config.getDbUser(),
                config.getDbPassword()
        );
    }
}

package app.testutil;

import app.config.AppConfig;
import app.db.ConnectionFactory;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

/**
 * Fabrica de conexiones sin base de datos: acepta el DDL que algunos DAO ejecutan al construirse y rechaza
 * cualquier otra operacion. Sirve para probar que una regla (p. ej. de autorizacion) actua antes del acceso a datos.
 */
public final class NoDatabase extends ConnectionFactory {
    public NoDatabase() {
        super(AppConfig.fromProperties(new Properties(), n -> null));
    }

    @Override
    public Connection getConnection() {
        Statement statement = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                (p, m, a) -> switch (m.getName()) {
                    case "executeUpdate" -> 0;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException("sin base de datos: " + m.getName());
                });
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> switch (m.getName()) {
                    case "createStatement" -> statement;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException("sin base de datos: " + m.getName());
                });
    }
}

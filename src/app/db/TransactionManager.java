package app.db;

import java.sql.Connection;
import java.sql.SQLException;

public class TransactionManager {
    public interface Tx<T> {
        T run(Connection conn) throws SQLException;
    }

    public static <T> T execute(ConnectionFactory factory, Tx<T> tx) {
        try (Connection conn = factory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = tx.run(conn);
                conn.commit();
                return result;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en transaccion", e);
        }
    }
}

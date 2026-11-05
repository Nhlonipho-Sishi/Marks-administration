package db;

import java.sql.*;

/**
 * Central JDBC connection factory + small helpers.
 * Credentials live here (sandbox demo) - in production use environment variables / a pool.
 */
public final class Database {
    private static final String URL  = "jdbc:mysql://localhost:3306/marks_admin"
                                     + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "marksuser";
    private static final String PASS = "markspass";

    private Database() {}

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /** Append one row to the audit trail. */
    public static void audit(Connection c, Integer userId, String action, String detail) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_log(user_id, action, detail) VALUES (?,?,?)")) {
            if (userId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setString(3, detail);
            ps.executeUpdate();
        }
    }
}

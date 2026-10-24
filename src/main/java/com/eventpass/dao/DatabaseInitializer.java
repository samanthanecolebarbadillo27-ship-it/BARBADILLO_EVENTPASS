package com.eventpass.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates (and, if needed, upgrades) the database schema before the menu starts.
 * Foreign keys are enabled for every connection by {@link DatabaseConnection}.
 */
public final class DatabaseInitializer {
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private DatabaseInitializer() {
    }

    public static void initialize() throws SQLException {
        String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    roles TEXT NOT NULL CHECK (roles IN ('ADMIN', 'TEACHER', 'STUDENT')),
                    status TEXT NOT NULL DEFAULT 'PENDING'
                )
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createUsersTable);
            upgradeRoleConstraintIfNeeded(connection);
            seedDefaultAdmin(connection);
        }
    }

    /** Older databases only allowed TEACHER/STUDENT; rebuild the table so ADMIN is allowed. */
    private static void upgradeRoleConstraintIfNeeded(Connection connection) throws SQLException {
        String tableSql = null;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'users'")) {
            if (rs.next()) {
                tableSql = rs.getString(1);
            }
        }
        if (tableSql == null || tableSql.contains("'ADMIN'")) {
            return;
        }

        // Foreign keys must be switched off outside a transaction while the table is rebuilt.
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = OFF;");
        }
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE users_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        password TEXT NOT NULL,
                        roles TEXT NOT NULL CHECK (roles IN ('ADMIN', 'TEACHER', 'STUDENT')),
                        status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                    """);
            st.execute("INSERT INTO users_new (id, username, password, roles, status) "
                    + "SELECT id, username, password, roles, status FROM users");
            st.execute("DROP TABLE users");
            st.execute("ALTER TABLE users_new RENAME TO users");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON;");
            }
        }
    }

    /** Ensures at least one ADMIN account exists so pending users can be approved. */
    private static void seedDefaultAdmin(Connection connection) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM users WHERE roles = ?";
        try (PreparedStatement count = connection.prepareStatement(countSql)) {
            count.setString(1, "ADMIN");
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        String insertSql = "INSERT OR IGNORE INTO users (username, password, roles, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, DEFAULT_ADMIN_USERNAME);
            insert.setString(2, PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD));
            insert.setString(3, "ADMIN");
            insert.setString(4, "APPROVED");
            insert.executeUpdate();
        }
    }
}

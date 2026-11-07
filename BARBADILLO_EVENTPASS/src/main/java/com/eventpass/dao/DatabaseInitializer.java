package com.eventpass.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the five EventPass tables from the system proposal / ERD:
 * users, events, registrations, attendance_logs and activity_logs.
 */
public final class DatabaseInitializer {
    public static final String DEFAULT_ADMIN_NAME = "System Admin";
    public static final String DEFAULT_ADMIN_EMAIL = "admin@eventpass.com";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private static final String CREATE_USERS = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL,
                roles TEXT NOT NULL CHECK (roles IN ('STUDENT', 'ORGANIZER', 'MONITOR', 'ADMIN')),
                status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('APPROVED', 'PENDING'))
            )
            """;

    private static final String CREATE_EVENTS = """
            CREATE TABLE IF NOT EXISTS events (
                event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                organizer_id INTEGER NOT NULL,
                event_title TEXT NOT NULL,
                venue TEXT NOT NULL,
                event_date TIMESTAMP NOT NULL,
                max_capacity INTEGER NOT NULL CHECK (max_capacity > 0),
                FOREIGN KEY (organizer_id) REFERENCES users(id)
            )
            """;

    private static final String CREATE_REGISTRATIONS = """
            CREATE TABLE IF NOT EXISTS registrations (
                registration_id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                student_id INTEGER NOT NULL,
                registration_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                status TEXT NOT NULL DEFAULT 'REGISTERED',
                UNIQUE (event_id, student_id),
                FOREIGN KEY (event_id) REFERENCES events(event_id),
                FOREIGN KEY (student_id) REFERENCES users(id)
            )
            """;

    private static final String CREATE_ATTENDANCE_LOGS = """
            CREATE TABLE IF NOT EXISTS attendance_logs (
                attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,
                registration_id INTEGER NOT NULL UNIQUE,
                monitor_id INTEGER NOT NULL,
                check_in_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                arrival_status TEXT NOT NULL DEFAULT 'ON_TIME',
                FOREIGN KEY (registration_id) REFERENCES registrations(registration_id),
                FOREIGN KEY (monitor_id) REFERENCES users(id)
            )
            """;

    private static final String CREATE_ACTIVITY_LOGS = """
            CREATE TABLE IF NOT EXISTS activity_logs (
                activity_id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                activity_type TEXT NOT NULL,
                activity_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                details TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id)
            )
            """;

    private DatabaseInitializer() {
    }

    public static void initialize() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            dropOldSchemaIfNeeded(connection);
            statement.execute(CREATE_USERS);
            statement.execute(CREATE_EVENTS);
            statement.execute(CREATE_REGISTRATIONS);
            statement.execute(CREATE_ATTENDANCE_LOGS);
            statement.execute(CREATE_ACTIVITY_LOGS);
            seedDefaultAdmin(connection);
        }
    }

    /**
     * An older eventpass.db (username-based users table) cannot be used with the proposal's schema,
     * so it is dropped and recreated. Only runs when the users table exists without a full_name column.
     */
    private static void dropOldSchemaIfNeeded(Connection connection) throws SQLException {
        boolean usersExists = false;
        boolean hasFullName = false;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(users)")) {
            while (rs.next()) {
                usersExists = true;
                if ("full_name".equalsIgnoreCase(rs.getString("name"))) {
                    hasFullName = true;
                }
            }
        }
        if (usersExists && !hasFullName) {
            try (Statement st = connection.createStatement()) {
                st.execute("DROP TABLE IF EXISTS attendance_logs");
                st.execute("DROP TABLE IF EXISTS registrations");
                st.execute("DROP TABLE IF EXISTS events");
                st.execute("DROP TABLE IF EXISTS activity_logs");
                st.execute("DROP TABLE IF EXISTS users");
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
        String insertSql = "INSERT OR IGNORE INTO users (full_name, email, password, roles, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, DEFAULT_ADMIN_NAME);
            insert.setString(2, DEFAULT_ADMIN_EMAIL);
            insert.setString(3, PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD));
            insert.setString(4, "ADMIN");
            insert.setString(5, "APPROVED");
            insert.executeUpdate();
        }
    }
}

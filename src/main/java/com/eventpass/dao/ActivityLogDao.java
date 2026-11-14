package com.eventpass.dao;

import com.eventpass.model.ActionType;
import com.eventpass.model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the activity_logs (audit trail) table.
 * All SQL for the audit log lives here; every statement is a parameterized PreparedStatement.
 */
public class ActivityLogDao {
    private static final String SELECT_COLUMNS =
            "SELECT id, user_id, action_type, description, timestamp FROM activity_logs";

    /** Inserts one audit entry using its own connection (autocommit). */
    public void logAction(int userId, String actionType, String description) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            insert(connection, userId, actionType, description);
        }
    }

    /**
     * Inserts one audit entry on an existing connection. Other DAOs call this inside their own
     * transaction so a business change and its audit entry are saved (or rolled back) together.
     */
    static void insert(Connection connection, int userId, String actionType, String description)
            throws SQLException {
        String sql = "INSERT INTO activity_logs (user_id, action_type, description) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, actionType);
            statement.setString(3, description);
            statement.executeUpdate();
        }
    }

    /** Returns every log entry, newest first. */
    public List<ActivityLog> getAllLogs() throws SQLException {
        return query(SELECT_COLUMNS + " ORDER BY timestamp DESC, id DESC", null);
    }

    /** Returns the log entries written by one user, newest first. */
    public List<ActivityLog> getLogsByUserId(int userId) throws SQLException {
        return query(SELECT_COLUMNS + " WHERE user_id = ? ORDER BY timestamp DESC, id DESC", userId);
    }

    /**
     * Deletes all historical log entries, then records a LOGS_CLEARED entry for the admin who did it,
     * in one transaction. The clearing itself therefore always remains in the audit trail.
     *
     * @return how many entries were deleted
     */
    public int clearAllLogs(int adminId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int deleted;
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM activity_logs")) {
                    deleted = statement.executeUpdate();
                }
                insert(connection, adminId, ActionType.LOGS_CLEARED,
                        "Admin " + adminId + " cleared " + deleted + " audit log entries.");
                connection.commit();
                return deleted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private List<ActivityLog> query(String sql, Integer userId) throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId != null) {
                statement.setInt(1, userId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    logs.add(new ActivityLog(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getString("action_type"),
                            rs.getString("description"),
                            rs.getString("timestamp")));
                }
            }
        }
        return logs;
    }
}

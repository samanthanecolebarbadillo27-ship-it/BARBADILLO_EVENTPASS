package com.eventpass.dao;

import com.eventpass.model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the activity_logs (audit) table. */
public class ActivityLogDao {

    public void log(Integer userId, String activityType, String details) throws SQLException {
        String sql = "INSERT INTO activity_logs (user_id, activity_type, details) VALUES (?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, userId);
            }
            statement.setString(2, activityType);
            statement.setString(3, details);
            statement.executeUpdate();
        }
    }

    public List<ActivityLog> getAll() throws SQLException {
        String sql = "SELECT activity_id, user_id, activity_type, activity_timestamp, details "
                + "FROM activity_logs ORDER BY activity_id DESC";
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int uid = rs.getInt("user_id");
                Integer userId = rs.wasNull() ? null : uid;
                logs.add(new ActivityLog(
                        rs.getInt("activity_id"),
                        userId,
                        rs.getString("activity_type"),
                        rs.getString("activity_timestamp"),
                        rs.getString("details")));
            }
        }
        return logs;
    }
}

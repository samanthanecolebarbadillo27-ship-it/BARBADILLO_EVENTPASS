package com.eventpass.dao;

import com.eventpass.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UserDao {

    public boolean register(String username, String password, String role) throws SQLException {
        String normalizedUsername = username.trim();
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        if (!normalizedRole.equals("TEACHER") && !normalizedRole.equals("STUDENT")) {
            throw new IllegalArgumentException("Role must be TEACHER or STUDENT.");
        }

        String sql = """
                INSERT INTO users (username, password, roles, status)
                VALUES (?, ?, ?, 'PENDING')
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedUsername);
            statement.setString(2, PasswordUtil.hash(password));
            statement.setString(3, normalizedRole);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("unique")) {
                return false;
            }
            throw e;
        }
    }

    public Optional<User> login(String username, String password) throws SQLException {
        String sql = """
                SELECT id, username, password, roles, status
                FROM users
                WHERE username = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                String storedHash = resultSet.getString("password");
                if (!PasswordUtil.verify(password, storedHash)) {
                    return Optional.empty();
                }

                User user = new User(
                        resultSet.getInt("id"),
                        resultSet.getString("username"),
                        storedHash,
                        resultSet.getString("roles"),
                        resultSet.getString("status")
                );
                return Optional.of(user);
            }
        }
    }

    /** Returns every registered account regardless of role or status. */
    public List<User> getAllUsers() throws SQLException {
        String sql = "SELECT id, username, password, roles, status FROM users ORDER BY id";
        return queryUsers(sql, null);
    }

    /** Returns every account (all roles) whose status is PENDING. */
    public List<User> getPendingUsers() throws SQLException {
        String sql = "SELECT id, username, password, roles, status FROM users WHERE status = ? ORDER BY id";
        return queryUsers(sql, "PENDING");
    }

    /**
     * Changes a PENDING account to APPROVED.
     *
     * @return true if exactly one pending account was updated
     */
    public boolean approveUser(int userId) throws SQLException {
        String sql = "UPDATE users SET status = 'APPROVED' WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            return statement.executeUpdate() > 0;
        }
    }

    private List<User> queryUsers(String sql, String param) throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (param != null) {
                statement.setString(1, param);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    users.add(new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("roles"),
                            rs.getString("status")));
                }
            }
        }
        return users;
    }
}

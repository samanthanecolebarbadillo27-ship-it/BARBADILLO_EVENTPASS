package com.eventpass.dao;

import com.eventpass.model.ActionType;
import com.eventpass.model.Roles;
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
    private static final String SELECT_COLUMNS = "SELECT id, full_name, email, password, roles, status FROM users";

    public boolean register(String fullName, String email, String password, String role) throws SQLException {
        String normalizedName = fullName.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);

        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        if (!normalizedEmail.contains("@") || normalizedEmail.startsWith("@") || normalizedEmail.endsWith("@")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        if (!Roles.SELF_REGISTER.contains(normalizedRole)) {
            throw new IllegalArgumentException("Role must be STUDENT, ORGANIZER, or MONITOR.");
        }

        String sql = "INSERT INTO users (full_name, email, password, roles, status) VALUES (?, ?, ?, ?, 'PENDING')";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedName);
            statement.setString(2, normalizedEmail);
            statement.setString(3, PasswordUtil.hash(password));
            statement.setString(4, normalizedRole);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("unique")) {
                return false;
            }
            throw e;
        }
    }

    public Optional<User> login(String email, String password) throws SQLException {
        String sql = SELECT_COLUMNS + " WHERE email = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim().toLowerCase(Locale.ROOT));

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String storedHash = rs.getString("password");
                if (!PasswordUtil.verify(password, storedHash)) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(rs));
            }
        }
    }

    /** Returns every registered account regardless of role or status. */
    public List<User> getAllUsers() throws SQLException {
        return queryUsers(SELECT_COLUMNS + " ORDER BY id", null);
    }

    /** Returns every account (all roles) whose status is PENDING. */
    public List<User> getPendingUsers() throws SQLException {
        return queryUsers(SELECT_COLUMNS + " WHERE status = ? ORDER BY id", "PENDING");
    }

    /**
     * Changes an account's status to APPROVED and records USER_APPROVED in the audit trail
     * (same transaction, so either both are saved or neither is).
     *
     * @param adminId the admin performing the approval
     * @param userId  the account being approved
     */
    public boolean approveUser(int adminId, int userId) throws SQLException {
        String sql = "UPDATE users SET status = 'APPROVED' WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                ActivityLogDao.insert(connection, adminId, ActionType.USER_APPROVED,
                        "Admin " + adminId + " approved account for User ID " + userId + ".");
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Updates the role and status of one user account and records USER_UPDATED in the audit trail
     * (same transaction). Both values are converted to upper case before they are saved.
     *
     * @param adminId the admin performing the change
     * @return true if exactly one user row was updated, false if no user has that id
     */
    public boolean updateUserRoleAndStatus(int adminId, int userId, String newRole, String newStatus)
            throws SQLException {
        String role = newRole.trim().toUpperCase(Locale.ROOT);
        String status = newStatus.trim().toUpperCase(Locale.ROOT);
        String sql = "UPDATE users SET roles = ?, status = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, role);
                statement.setString(2, status);
                statement.setInt(3, userId);
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
                ActivityLogDao.insert(connection, adminId, ActionType.USER_UPDATED,
                        "Admin " + adminId + " changed role of User " + userId + " to " + role
                                + " and status to " + status + ".");
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
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
                    users.add(mapUser(rs));
                }
            }
        }
        return users;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("password"),
                rs.getString("roles"),
                rs.getString("status"));
    }
}

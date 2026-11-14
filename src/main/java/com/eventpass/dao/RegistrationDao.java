package com.eventpass.dao;

import com.eventpass.model.ActionType;
import com.eventpass.model.Registration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Student registrations for events.
 * Registering is audited as EVENT_REGISTERED; the status change REGISTERED -> CANCELLED is audited
 * as REG_STATUS_UPDATED. Business-rule problems are reported with IllegalArgumentException.
 */
public class RegistrationDao {
    private static final String SELECT_REGISTRATIONS =
            "SELECT r.registration_id, r.event_id, e.event_title, r.student_id, u.full_name, "
                    + "r.status, r.registration_date "
                    + "FROM registrations r "
                    + "JOIN events e ON e.event_id = r.event_id "
                    + "JOIN users u ON u.id = r.student_id";

    /** Registers a student for an event (core business process). Returns the registration_id. */
    public int registerForEvent(int studentId, int eventId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String title;
                int capacity;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT event_title, max_capacity FROM events WHERE event_id = ?")) {
                    ps.setInt(1, eventId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Event ID " + eventId + " does not exist.");
                        }
                        title = rs.getString("event_title");
                        capacity = rs.getInt("max_capacity");
                    }
                }

                Integer existingId = null;
                String existingStatus = null;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT registration_id, status FROM registrations WHERE event_id = ? AND student_id = ?")) {
                    ps.setInt(1, eventId);
                    ps.setInt(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getInt("registration_id");
                            existingStatus = rs.getString("status");
                        }
                    }
                }
                if (existingId != null && !"CANCELLED".equals(existingStatus)) {
                    throw new IllegalArgumentException(
                            "You already have a registration for this event (status: " + existingStatus + ").");
                }

                int taken;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM registrations WHERE event_id = ? AND status <> 'CANCELLED'")) {
                    ps.setInt(1, eventId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        taken = rs.getInt(1);
                    }
                }
                if (taken >= capacity) {
                    throw new IllegalArgumentException("This event is full (" + capacity + " seats).");
                }

                int registrationId;
                if (existingId != null) {
                    // A previously cancelled registration is re-activated (the table allows one row per student/event).
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE registrations SET status = 'REGISTERED', "
                                    + "registration_date = datetime('now', 'localtime') WHERE registration_id = ?")) {
                        ps.setInt(1, existingId);
                        ps.executeUpdate();
                    }
                    registrationId = existingId;
                } else {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO registrations (event_id, student_id, registration_date, status) "
                                    + "VALUES (?, ?, datetime('now', 'localtime'), 'REGISTERED')",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, eventId);
                        ps.setInt(2, studentId);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            registrationId = keys.getInt(1);
                        }
                    }
                }

                ActivityLogDao.insert(connection, studentId, ActionType.EVENT_REGISTERED,
                        "Student " + studentId + " registered for Event #" + eventId + " '" + title
                                + "' (Registration #" + registrationId + ").");
                connection.commit();
                return registrationId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Lifecycle change: a student cancels their own REGISTERED registration. */
    public void cancelRegistration(int studentId, int registrationId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String oldStatus;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT status FROM registrations WHERE registration_id = ? AND student_id = ?")) {
                    ps.setInt(1, registrationId);
                    ps.setInt(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Registration #" + registrationId + " was not found on your account.");
                        }
                        oldStatus = rs.getString("status");
                    }
                }
                if (!"REGISTERED".equals(oldStatus)) {
                    throw new IllegalArgumentException(
                            "Only REGISTERED registrations can be cancelled (this one is " + oldStatus + ").");
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE registrations SET status = 'CANCELLED' WHERE registration_id = ? AND student_id = ?")) {
                    ps.setInt(1, registrationId);
                    ps.setInt(2, studentId);
                    ps.executeUpdate();
                }
                ActivityLogDao.insert(connection, studentId, ActionType.REG_STATUS_UPDATED,
                        "Updated Registration #" + registrationId + " status from " + oldStatus + " to CANCELLED.");
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<Registration> getRegistrationsByStudent(int studentId) throws SQLException {
        return query(SELECT_REGISTRATIONS + " WHERE r.student_id = ? ORDER BY r.registration_id", studentId);
    }

    public List<Registration> getRegistrationsByEvent(int eventId) throws SQLException {
        return query(SELECT_REGISTRATIONS + " WHERE r.event_id = ? ORDER BY r.registration_id", eventId);
    }

    private List<Registration> query(String sql, int param) throws SQLException {
        List<Registration> list = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, param);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new Registration(
                            rs.getInt("registration_id"),
                            rs.getInt("event_id"),
                            rs.getString("event_title"),
                            rs.getInt("student_id"),
                            rs.getString("full_name"),
                            rs.getString("status"),
                            rs.getString("registration_date")));
                }
            }
        }
        return list;
    }
}

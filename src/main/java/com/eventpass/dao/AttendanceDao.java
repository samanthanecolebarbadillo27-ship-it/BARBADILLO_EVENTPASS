package com.eventpass.dao;

import com.eventpass.model.ActionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Gate check-in. A successful check-in is audited as ATTENDANCE_LOGGED. */
public class AttendanceDao {
    public static final Set<String> ARRIVAL_STATUSES = Set.of("ON_TIME", "LATE");

    /**
     * Checks a registered student in at the gate: writes the attendance_logs row and marks the
     * registration ATTENDED, in one transaction together with the audit entry.
     *
     * @throws IllegalArgumentException if the registration does not exist or is not in REGISTERED status
     */
    public void checkIn(int monitorId, int registrationId, String arrivalStatus) throws SQLException {
        if (!ARRIVAL_STATUSES.contains(arrivalStatus)) {
            throw new IllegalArgumentException("Arrival status must be ON_TIME or LATE.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int eventId;
                int studentId;
                String status;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT event_id, student_id, status FROM registrations WHERE registration_id = ?")) {
                    ps.setInt(1, registrationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Registration #" + registrationId + " does not exist.");
                        }
                        eventId = rs.getInt("event_id");
                        studentId = rs.getInt("student_id");
                        status = rs.getString("status");
                    }
                }
                if (!"REGISTERED".equals(status)) {
                    throw new IllegalArgumentException("Registration #" + registrationId
                            + " cannot be checked in (status: " + status + ").");
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO attendance_logs (registration_id, monitor_id, check_in_time, arrival_status) "
                                + "VALUES (?, ?, datetime('now', 'localtime'), ?)")) {
                    ps.setInt(1, registrationId);
                    ps.setInt(2, monitorId);
                    ps.setString(3, arrivalStatus);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE registrations SET status = 'ATTENDED' WHERE registration_id = ?")) {
                    ps.setInt(1, registrationId);
                    ps.executeUpdate();
                }
                ActivityLogDao.insert(connection, monitorId, ActionType.ATTENDANCE_LOGGED,
                        "Monitor " + monitorId + " checked in Student " + studentId + " (Registration #"
                                + registrationId + ", Event #" + eventId + ") - " + arrivalStatus + ".");
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}

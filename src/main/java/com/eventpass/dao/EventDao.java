package com.eventpass.dao;

import com.eventpass.model.ActionType;
import com.eventpass.model.Event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Event data access. Creating an event is audited as EVENT_CREATED. */
public class EventDao {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String SELECT_EVENTS =
            "SELECT e.event_id, e.organizer_id, e.event_title, e.venue, e.event_date, e.max_capacity, "
                    + "(SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.event_id "
                    + "AND r.status <> 'CANCELLED') AS registered "
                    + "FROM events e";

    /** Creates an event for an organizer and returns its new event_id. */
    public int createEvent(int organizerId, String title, String venue, LocalDateTime eventDate, int maxCapacity)
            throws SQLException {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanVenue = venue == null ? "" : venue.trim();
        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("Event title cannot be empty.");
        }
        if (cleanVenue.isEmpty()) {
            throw new IllegalArgumentException("Venue cannot be empty.");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0.");
        }

        String sql = "INSERT INTO events (organizer_id, event_title, venue, event_date, max_capacity) "
                + "VALUES (?, ?, ?, ?, ?)";
        String formattedDate = eventDate.format(DATE_FORMAT);
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, organizerId);
                statement.setString(2, cleanTitle);
                statement.setString(3, cleanVenue);
                statement.setString(4, formattedDate);
                statement.setInt(5, maxCapacity);
                statement.executeUpdate();

                int eventId;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    eventId = keys.getInt(1);
                }
                ActivityLogDao.insert(connection, organizerId, ActionType.EVENT_CREATED,
                        "Organizer " + organizerId + " created Event #" + eventId + " '" + cleanTitle
                                + "' at " + cleanVenue + " on " + formattedDate
                                + " (capacity " + maxCapacity + ").");
                connection.commit();
                return eventId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<Event> getAllEvents() throws SQLException {
        return query(SELECT_EVENTS + " ORDER BY e.event_date, e.event_id", null);
    }

    public List<Event> getEventsByOrganizer(int organizerId) throws SQLException {
        return query(SELECT_EVENTS + " WHERE e.organizer_id = ? ORDER BY e.event_date, e.event_id", organizerId);
    }

    private List<Event> query(String sql, Integer organizerId) throws SQLException {
        List<Event> events = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (organizerId != null) {
                statement.setInt(1, organizerId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    events.add(new Event(
                            rs.getInt("event_id"),
                            rs.getInt("organizer_id"),
                            rs.getString("event_title"),
                            rs.getString("venue"),
                            rs.getString("event_date"),
                            rs.getInt("max_capacity"),
                            rs.getInt("registered")));
                }
            }
        }
        return events;
    }
}

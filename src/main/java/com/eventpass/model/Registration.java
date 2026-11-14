package com.eventpass.model;

/** A row of the registrations table joined with its event title and student name. */
public record Registration(
        int registrationId,
        int eventId,
        String eventTitle,
        int studentId,
        String studentName,
        String status,
        String registrationDate
) {
}

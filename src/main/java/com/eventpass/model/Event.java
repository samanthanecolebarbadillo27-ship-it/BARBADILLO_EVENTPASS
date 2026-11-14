package com.eventpass.model;

/** A row of the events table plus the number of active (non-cancelled) registrations. */
public record Event(
        int eventId,
        int organizerId,
        String title,
        String venue,
        String eventDate,
        int maxCapacity,
        int registeredCount
) {
}

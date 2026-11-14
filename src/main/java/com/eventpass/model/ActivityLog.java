package com.eventpass.model;

/** A row of the activity_logs table (audit trail). */
public record ActivityLog(
        int id,
        int userId,
        String actionType,
        String description,
        String timestamp
) {
}

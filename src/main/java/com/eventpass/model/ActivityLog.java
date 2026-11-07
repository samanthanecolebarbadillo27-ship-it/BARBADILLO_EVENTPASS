package com.eventpass.model;

/** A row of the activity_logs table (audit trail). */
public record ActivityLog(
        int activityId,
        Integer userId,
        String activityType,
        String activityTimestamp,
        String details
) {
}

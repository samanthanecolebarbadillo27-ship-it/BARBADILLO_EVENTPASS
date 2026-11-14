package com.eventpass.model;

/**
 * Action types written to activity_logs.action_type.
 * Every value is 18 characters or fewer so it fits the audit table's ACTION TYPE column.
 */
public final class ActionType {
    // Authentication
    public static final String USER_LOGIN = "USER_LOGIN";
    public static final String USER_LOGOUT = "USER_LOGOUT";

    // Account management (Deliverables 5 and 7)
    public static final String USER_APPROVED = "USER_APPROVED";
    public static final String USER_UPDATED = "USER_UPDATED";

    // EventPass business events
    public static final String EVENT_CREATED = "EVENT_CREATED";
    public static final String EVENT_REGISTERED = "EVENT_REGISTERED";
    public static final String ATTENDANCE_LOGGED = "ATTENDANCE_LOGGED";
    public static final String REG_STATUS_UPDATED = "REG_STATUS_UPDATED";

    // Audit maintenance
    public static final String LOGS_CLEARED = "LOGS_CLEARED";

    private ActionType() {
    }
}

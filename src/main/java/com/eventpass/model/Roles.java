package com.eventpass.model;

import java.util.List;

/** The four EventPass roles from the system proposal, stored in upper case. */
public final class Roles {
    public static final String STUDENT = "STUDENT";       // Student Attendee
    public static final String ORGANIZER = "ORGANIZER";   // Event Organizer
    public static final String MONITOR = "MONITOR";       // Gate Monitor / Usher
    public static final String ADMIN = "ADMIN";           // Student Affairs Admin

    /** Every valid role (used by the admin update validation). */
    public static final List<String> ALL = List.of(STUDENT, ORGANIZER, MONITOR, ADMIN);

    /** Roles a person may pick for themselves when registering (never ADMIN). */
    public static final List<String> SELF_REGISTER = List.of(STUDENT, ORGANIZER, MONITOR);

    private Roles() {
    }

    public static String displayName(String role) {
        return switch (role == null ? "" : role.toUpperCase()) {
            case STUDENT -> "Student Attendee";
            case ORGANIZER -> "Event Organizer";
            case MONITOR -> "Gate Monitor / Usher";
            case ADMIN -> "Student Affairs Admin";
            default -> role;
        };
    }
}

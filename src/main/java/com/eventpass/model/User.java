package com.eventpass.model;

/** A row of the users table. {@code role} holds the value of the "roles" column. */
public record User(
        int id,
        String fullName,
        String email,
        String passwordHash,
        String role,
        String status
) {
}

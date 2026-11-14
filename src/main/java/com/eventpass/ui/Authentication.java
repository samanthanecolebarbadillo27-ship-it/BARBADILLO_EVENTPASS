package com.eventpass.ui;

import com.eventpass.dao.ActivityLogDao;
import com.eventpass.dao.AttendanceDao;
import com.eventpass.dao.EventDao;
import com.eventpass.dao.RegistrationDao;
import com.eventpass.dao.UserDao;
import com.eventpass.model.ActionType;
import com.eventpass.model.Roles;
import com.eventpass.model.User;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Scanner;

/**
 * Handles registration, login, and routing users to their role-specific menus.
 * SQL statements and database logic remain in the DAO layer.
 */
public class Authentication {
    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;
    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final AttendanceDao attendanceDao;
    private final Scanner scanner;
    private boolean running = true;

    public Authentication(UserDao userDao, ActivityLogDao activityLogDao, EventDao eventDao,
                          RegistrationDao registrationDao, AttendanceDao attendanceDao, Scanner scanner) {
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.attendanceDao = attendanceDao;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("==================================");
        System.out.println("  EventPass - Student Event Attendance Tracker");
        System.out.println("==================================");

        while (running) {
            System.out.println("\nMAIN MENU");
            System.out.println("1. Register Account");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> register();
                case "2" -> login();
                case "3" -> {
                    running = false;
                    System.out.println("Exiting EventPass. Goodbye!");
                }
                default -> System.out.println("Invalid option. Please choose 1, 2, or 3.");
            }
        }
    }

    private void register() {
        System.out.println("\n--- Register Account ---");
        System.out.print("Enter full name: ");
        String fullName = scanner.nextLine().trim();
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        System.out.println("Choose role:");
        System.out.println("1. STUDENT   (Student Attendee)");
        System.out.println("2. ORGANIZER (Event Organizer)");
        System.out.println("3. MONITOR   (Gate Monitor / Usher)");
        System.out.print("Enter role number: ");

        String role = switch (scanner.nextLine().trim()) {
            case "1" -> Roles.STUDENT;
            case "2" -> Roles.ORGANIZER;
            case "3" -> Roles.MONITOR;
            default -> null;
        };

        if (role == null) {
            System.out.println("Invalid role. Registration cancelled.");
            return;
        }

        try {
            if (userDao.register(fullName, email, password, role)) {
                System.out.println("Account registered successfully.");
                System.out.println("Role: " + role);
                System.out.println("Status: PENDING");
            } else {
                System.out.println("That email is already registered. Please use another.");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Registration failed: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error while registering: " + e.getMessage());
        }
    }

    private void login() {
        System.out.println("\n--- Login ---");
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        try {
            Optional<User> result = userDao.login(email, password);
            if (result.isEmpty()) {
                System.out.println("Login failed. Check your email and password.");
                return;
            }

            User user = result.get();
            logQuietly(user.id(), ActionType.USER_LOGIN,
                    "User " + user.email() + " (ID: " + user.id() + ") logged in.");
            System.out.println("Login successful. Welcome, " + user.fullName() + "!");
            System.out.println("Role: " + user.role() + " (" + Roles.displayName(user.role()) + ")");
            System.out.println("Account status: " + user.status());
            if ("PENDING".equalsIgnoreCase(user.status())) {
                System.out.println("Notice: your account is pending approval.");
            }
            routeToRoleMenu(user);
        } catch (SQLException e) {
            System.out.println("Database error while logging in: " + e.getMessage());
        }
    }

    /**
     * Opens the menu for the user's role. Admins and APPROVED users get their full menu; accounts
     * that are still PENDING only get the limited menu. When the menu closes, the logout is audited.
     */
    private void routeToRoleMenu(User user) {
        boolean approved = "APPROVED".equalsIgnoreCase(user.status());
        boolean exitApp;
        switch (user.role().toUpperCase()) {
            case Roles.ADMIN -> exitApp = new AdminMenu(userDao, activityLogDao, scanner).show(user);
            case Roles.ORGANIZER -> exitApp = approved
                    ? new OrganizerMenu(eventDao, registrationDao, scanner).show(user)
                    : limitedMenu(user, "EVENT ORGANIZER MENU");
            case Roles.MONITOR -> exitApp = approved
                    ? new MonitorMenu(eventDao, registrationDao, attendanceDao, scanner).show(user)
                    : limitedMenu(user, "GATE MONITOR MENU");
            case Roles.STUDENT -> exitApp = approved
                    ? new StudentMenu(eventDao, registrationDao, scanner).show(user)
                    : limitedMenu(user, "STUDENT ATTENDEE MENU");
            default -> {
                System.out.println("No menu is configured for role: " + user.role());
                return;
            }
        }

        logQuietly(user.id(), ActionType.USER_LOGOUT, "User " + user.email() + " (ID: " + user.id()
                + ") logged out" + (exitApp ? " (application exit)." : "."));
        if (exitApp) {
            running = false;
        }
    }

    /** Menu for accounts that are not approved yet. @return true if the user chose to exit the application */
    private boolean limitedMenu(User user, String title) {
        while (true) {
            System.out.println("\n--- " + title + " (PENDING APPROVAL) ---");
            System.out.println("Signed in as: " + user.fullName());
            System.out.println("1. View Account Details");
            System.out.println("2. Logout");
            System.out.println("3. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> {
                    System.out.println("User ID: " + user.id());
                    System.out.println("Full name: " + user.fullName());
                    System.out.println("Email: " + user.email());
                    System.out.println("Role: " + user.role() + " (" + Roles.displayName(user.role()) + ")");
                    System.out.println("Status: " + user.status());
                }
                case "2" -> {
                    System.out.println("You have been logged out.");
                    return false;
                }
                case "3" -> {
                    System.out.println("Exiting EventPass. Goodbye!");
                    return true;
                }
                default -> System.out.println("Invalid option. Please choose 1, 2, or 3.");
            }
        }
    }

    /** Writes an audit entry without letting a logging problem break the user's session. */
    private void logQuietly(int userId, String actionType, String description) {
        try {
            activityLogDao.logAction(userId, actionType, description);
        } catch (SQLException e) {
            System.out.println("Warning: could not write the activity log: " + e.getMessage());
        }
    }
}

package com.eventpass.ui;

import com.eventpass.dao.ActivityLogDao;
import com.eventpass.dao.UserDao;
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
    private final Scanner scanner;
    private boolean running = true;

    public Authentication(UserDao userDao, ActivityLogDao activityLogDao, Scanner scanner) {
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
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
            try {
                activityLogDao.log(user.id(), "USER_LOGIN", "User " + user.email() + " logged in.");
            } catch (SQLException e) {
                System.out.println("Warning: could not write the activity log: " + e.getMessage());
            }
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

    private void routeToRoleMenu(User user) {
        switch (user.role().toUpperCase()) {
            case Roles.ADMIN -> {
                if (new AdminMenu(userDao, activityLogDao, scanner).show(user)) {
                    running = false;
                }
            }
            case Roles.ORGANIZER -> roleMenu(user, "EVENT ORGANIZER MENU");
            case Roles.MONITOR -> roleMenu(user, "GATE MONITOR MENU");
            case Roles.STUDENT -> roleMenu(user, "STUDENT ATTENDEE MENU");
            default -> System.out.println("No menu is configured for role: " + user.role());
        }
    }

    private void roleMenu(User user, String title) {
        boolean inMenu = true;
        while (inMenu && running) {
            System.out.println("\n--- " + title + " ---");
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
                    inMenu = false;
                    System.out.println("You have been logged out.");
                }
                case "3" -> {
                    inMenu = false;
                    running = false;
                    System.out.println("Exiting EventPass. Goodbye!");
                }
                default -> System.out.println("Invalid option. Please choose 1, 2, or 3.");
            }
        }
    }
}

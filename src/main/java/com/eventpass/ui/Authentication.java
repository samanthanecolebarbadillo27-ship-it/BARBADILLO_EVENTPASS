package com.eventpass.ui;

import com.eventpass.dao.UserDao;
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
    private final Scanner scanner;
    private boolean running = true;

    public Authentication(UserDao userDao, Scanner scanner) {
        this.userDao = userDao;
        this.scanner = scanner;
    }

    public void start() {
        System.out.println("==================================");
        System.out.println("  EventPass - Student Event Tracker");
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
        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        System.out.println("Choose role:");
        System.out.println("1. TEACHER");
        System.out.println("2. STUDENT");
        System.out.print("Enter role number: ");

        String role = switch (scanner.nextLine().trim()) {
            case "1" -> "TEACHER";
            case "2" -> "STUDENT";
            default -> null;
        };

        if (role == null) {
            System.out.println("Invalid role. Registration cancelled.");
            return;
        }

        try {
            if (userDao.register(username, password, role)) {
                System.out.println("Account registered successfully.");
                System.out.println("Role: " + role);
                System.out.println("Status: PENDING");
            } else {
                System.out.println("That username already exists. Please choose another.");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Registration failed: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error while registering: " + e.getMessage());
        }
    }

    private void login() {
        System.out.println("\n--- Login ---");
        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        try {
            Optional<User> result = userDao.login(username, password);
            if (result.isEmpty()) {
                System.out.println("Login failed. Check your username and password.");
                return;
            }

            User user = result.get();
            System.out.println("Login successful. Welcome, " + user.username() + "!");
            System.out.println("Role: " + user.role());
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
            case "ADMIN" -> {
                if (new AdminMenu(userDao, scanner).show(user)) {
                    running = false;
                }
            }
            case "TEACHER" -> teacherMenu(user);
            case "STUDENT" -> studentMenu(user);
            default -> System.out.println("No menu is configured for role: " + user.role());
        }
    }

    private void teacherMenu(User user) {
        roleMenu(user, "TEACHER MENU");
    }

    private void studentMenu(User user) {
        roleMenu(user, "STUDENT MENU");
    }

    private void roleMenu(User user, String title) {
        boolean inMenu = true;
        while (inMenu && running) {
            System.out.println("\n--- " + title + " ---");
            System.out.println("Signed in as: " + user.username());
            System.out.println("1. View Account Details");
            System.out.println("2. Logout");
            System.out.println("3. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> {
                    System.out.println("User ID: " + user.id());
                    System.out.println("Username: " + user.username());
                    System.out.println("Role: " + user.role());
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

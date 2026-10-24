package com.eventpass.ui;

import com.eventpass.dao.UserDao;
import com.eventpass.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Admin-only console menu: list users, list pending registrations, approve accounts.
 */
public class AdminMenu {
    private final UserDao userDao;
    private final Scanner scanner;

    public AdminMenu(UserDao userDao, Scanner scanner) {
        this.userDao = userDao;
        this.scanner = scanner;
    }

    /**
     * Runs the admin menu loop.
     *
     * @return true if the admin chose to exit the whole application, false on logout
     */
    public boolean show(User admin) {
        while (true) {
            System.out.println("\n--- ADMIN MENU ---");
            System.out.println("Signed in as: " + admin.username());
            System.out.println("1. View All System Users");
            System.out.println("2. View Pending Registrations");
            System.out.println("3. Approve User Account");
            System.out.println("4. Logout");
            System.out.println("5. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> viewAllUsers();
                case "2" -> viewPendingUsers();
                case "3" -> approveUser();
                case "4" -> {
                    System.out.println("You have been logged out.");
                    return false;
                }
                case "5" -> {
                    System.out.println("Exiting EventPass. Goodbye!");
                    return true;
                }
                default -> System.out.println("Invalid option. Please choose 1-5.");
            }
        }
    }

    private void viewAllUsers() {
        try {
            System.out.println("\n=== ALL SYSTEM USERS ===");
            printUserTable(userDao.getAllUsers());
        } catch (SQLException e) {
            System.out.println("Database error while loading users: " + e.getMessage());
        }
    }

    private void viewPendingUsers() {
        try {
            System.out.println("\n=== PENDING REGISTRATIONS ===");
            printUserTable(userDao.getPendingUsers());
        } catch (SQLException e) {
            System.out.println("Database error while loading pending users: " + e.getMessage());
        }
    }

    private void approveUser() {
        try {
            List<User> pending = userDao.getPendingUsers();
            System.out.println("\n=== APPROVE USER ACCOUNT ===");
            printUserTable(pending);
            if (pending.isEmpty()) {
                return;
            }

            while (true) {
                System.out.print("Enter Target User ID to approve (0 to cancel): ");
                String input = scanner.nextLine().trim();

                int userId;
                try {
                    userId = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a numeric User ID.");
                    continue;
                }

                if (userId == 0) {
                    System.out.println("Approval cancelled.");
                    return;
                }

                User target = pending.stream().filter(u -> u.id() == userId).findFirst().orElse(null);
                if (target == null) {
                    System.out.println("User ID " + userId + " is not in the pending list. Try again.");
                    continue;
                }

                if (userDao.approveUser(userId)) {
                    System.out.println("SUCCESS: Account '" + target.username() + "' (ID " + userId
                            + ", role " + target.role() + ") has been APPROVED.");
                } else {
                    System.out.println("Approval failed. The account may have changed; please try again.");
                }
                return;
            }
        } catch (SQLException e) {
            System.out.println("Database error while approving user: " + e.getMessage());
        }
    }

    /** Prints any list of users as an aligned, bordered table. */
    static void printUserTable(List<User> users) {
        String row = "| %-5s | %-15s | %-12s | %-10s |%n";
        String border = "+" + "-".repeat(7) + "+" + "-".repeat(17) + "+" + "-".repeat(14) + "+" + "-".repeat(12) + "+";

        System.out.println(border);
        System.out.printf(row, "ID", "USERNAME", "ROLE", "STATUS");
        System.out.println(border);
        if (users.isEmpty()) {
            System.out.printf("| %-46s |%n", "No users found.");
        } else {
            for (User u : users) {
                System.out.printf(row, u.id(), u.username(), u.role(), u.status());
            }
        }
        System.out.println(border);
        System.out.println("Total: " + users.size());
    }
}

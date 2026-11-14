package com.eventpass.ui;

import com.eventpass.dao.ActivityLogDao;
import com.eventpass.dao.UserDao;
import com.eventpass.model.ActivityLog;
import com.eventpass.model.Roles;
import com.eventpass.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

/**
 * Student Affairs Admin console menu: list users, list pending registrations, approve accounts,
 * update a user's role and status, and review / filter / clear the system audit log.
 */
public class AdminMenu {
    private static final Set<String> VALID_STATUSES = Set.of("APPROVED", "PENDING");

    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;
    private final Scanner scanner;

    public AdminMenu(UserDao userDao, ActivityLogDao activityLogDao, Scanner scanner) {
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
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
            System.out.println("Signed in as: " + admin.fullName());
            System.out.println("1. View All System Users");
            System.out.println("2. View Pending Registrations");
            System.out.println("3. Approve User Account");
            System.out.println("4. Update User Role & Status");
            System.out.println("5. View System Audit Logs");
            System.out.println("6. Logout");
            System.out.println("7. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> viewAllUsers();
                case "2" -> viewPendingUsers();
                case "3" -> approveUser(admin);
                case "4" -> updateUser(admin);
                case "5" -> auditLogMenu(admin);
                case "6" -> {
                    System.out.println("You have been logged out.");
                    return false;
                }
                case "7" -> {
                    System.out.println("Exiting EventPass. Goodbye!");
                    return true;
                }
                default -> System.out.println("Invalid option. Please choose 1-7.");
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

    private void approveUser(User admin) {
        try {
            List<User> pending = userDao.getPendingUsers();
            System.out.println("\n=== APPROVE USER ACCOUNT ===");
            printUserTable(pending);
            if (pending.isEmpty()) {
                return;
            }

            while (true) {
                int userId = readIntInput("Enter Target User ID to approve (0 to cancel): ");
                if (userId == 0) {
                    System.out.println("Approval cancelled.");
                    return;
                }

                User target = pending.stream().filter(u -> u.id() == userId).findFirst().orElse(null);
                if (target == null) {
                    System.out.println("User ID " + userId + " is not in the pending list. Try again.");
                    continue;
                }

                // UserDao.approveUser also writes the USER_APPROVED audit entry.
                if (userDao.approveUser(admin.id(), userId)) {
                    System.out.println("SUCCESS: Account '" + target.fullName() + "' (ID " + userId
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

    /**
     * Lets the admin change another user's role and status.
     * Entering 0 at any prompt cancels the update.
     */
    private void updateUser(User currentAdmin) {
        try {
            List<User> users = userDao.getAllUsers();
            System.out.println("\n=== UPDATE USER ROLE & STATUS ===");
            printUserTable(users);
            if (users.isEmpty()) {
                return;
            }

            // Step 1: choose the target user.
            User target;
            while (true) {
                int targetUserId = readIntInput("Enter Target User ID to update (0 to cancel): ");
                if (targetUserId == 0) {
                    System.out.println("Update cancelled.");
                    return;
                }
                if (targetUserId == currentAdmin.id()) {
                    System.out.println("WARNING: Modifying the currently active admin account is restricted.");
                    continue;
                }
                target = users.stream().filter(u -> u.id() == targetUserId).findFirst().orElse(null);
                if (target == null) {
                    System.out.println("User ID " + targetUserId + " does not exist. Try again.");
                    continue;
                }
                break;
            }

            System.out.println("Selected: " + target.fullName()
                    + " (current role " + target.role() + ", status " + target.status() + ")");

            // Step 2: choose the new role.
            String newRole = readValidRole();
            if (newRole == null) {
                System.out.println("Update cancelled.");
                return;
            }

            // Step 3: choose the new status.
            String newStatus = readValidStatus();
            if (newStatus == null) {
                System.out.println("Update cancelled.");
                return;
            }

            // UserDao.updateUserRoleAndStatus also writes the USER_UPDATED audit entry.
            if (userDao.updateUserRoleAndStatus(currentAdmin.id(), target.id(), newRole, newStatus)) {
                System.out.println("SUCCESS: User '" + target.fullName() + "' (ID " + target.id()
                        + ") updated to role " + newRole + " and status " + newStatus + ".");
            } else {
                System.out.println("Update failed. The account may no longer exist.");
            }
        } catch (SQLException e) {
            System.out.println("Database error while updating user: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- audit log interface

    private static final String LOG_ROW = "%-5s | %-8s | %-18s | %-35s | %-20s%n";
    private static final int LOG_DESCRIPTION_WIDTH = 35;

    /** Sub-menu for the audit trail: view all, filter by user, or clear history. */
    private void auditLogMenu(User admin) {
        while (true) {
            System.out.println("\n--- SYSTEM AUDIT LOGS ---");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear Historical Logs");
            System.out.println("0. Back to Admin Menu");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> viewAllLogs();
                case "2" -> filterLogsByUser();
                case "3" -> clearLogs(admin);
                case "0" -> {
                    return;
                }
                default -> System.out.println("Invalid option. Please choose 0-3.");
            }
        }
    }

    private void viewAllLogs() {
        try {
            System.out.println("\n=== SYSTEM AUDIT LOGS (newest first) ===");
            printLogTable(activityLogDao.getAllLogs());
        } catch (SQLException e) {
            System.out.println("Database error while loading audit logs: " + e.getMessage());
        }
    }

    private void filterLogsByUser() {
        int userId = readIntInput("Enter User ID to filter by (0 to cancel): ");
        if (userId == 0) {
            return;
        }
        try {
            System.out.println("\n=== AUDIT LOGS FOR USER ID " + userId + " (newest first) ===");
            printLogTable(activityLogDao.getLogsByUserId(userId));
        } catch (SQLException e) {
            System.out.println("Database error while loading audit logs: " + e.getMessage());
        }
    }

    /** Deletes all history only after the admin types CLEAR; the clearing itself is then logged. */
    private void clearLogs(User admin) {
        try {
            int count = activityLogDao.getAllLogs().size();
            if (count == 0) {
                System.out.println("There are no audit logs to clear.");
                return;
            }
            System.out.println("WARNING: This permanently deletes all " + count + " audit log entries.");
            System.out.print("Type CLEAR to confirm (anything else cancels): ");
            if (!scanner.nextLine().trim().equals("CLEAR")) {
                System.out.println("Clear cancelled. No logs were deleted.");
                return;
            }
            int deleted = activityLogDao.clearAllLogs(admin.id());
            System.out.println("SUCCESS: " + deleted + " audit log entries deleted. "
                    + "This action was recorded as LOGS_CLEARED.");
        } catch (SQLException e) {
            System.out.println("Database error while clearing audit logs: " + e.getMessage());
        }
    }

    /** Prints logs as an aligned console table; long descriptions wrap onto extra lines. */
    private static void printLogTable(List<ActivityLog> logs) {
        String divider = "-".repeat(5 + 3 + 8 + 3 + 18 + 3 + LOG_DESCRIPTION_WIDTH + 3 + 20);
        System.out.println(divider);
        System.out.printf(LOG_ROW, "ID", "USER ID", "ACTION TYPE", "DESCRIPTION", "TIMESTAMP");
        System.out.println(divider);
        if (logs.isEmpty()) {
            System.out.println("No log entries found.");
        }
        for (ActivityLog log : logs) {
            List<String> lines = wrap(log.description(), LOG_DESCRIPTION_WIDTH);
            System.out.printf(LOG_ROW, log.id(), log.userId(), fit(log.actionType(), 18),
                    lines.get(0), log.timestamp());
            for (int i = 1; i < lines.size(); i++) {
                System.out.printf(LOG_ROW, "", "", "", lines.get(i), "");
            }
        }
        System.out.println(divider);
        System.out.println("Total: " + logs.size());
    }

    /** Splits text into lines of at most {@code width} characters, breaking on spaces where possible. */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split("\\s+")) {
            while (word.length() > width) {            // a single very long word: hard split
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String fit(String text, int width) {
        if (text == null) {
            return "";
        }
        return text.length() <= width ? text : text.substring(0, width - 3) + "...";
    }

    /** Keeps asking until the admin types a whole number, so letters/symbols never crash the app. */
    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    /** Keeps asking until a valid role is entered. Returns null if the admin enters 0 to cancel. */
    private String readValidRole() {
        while (true) {
            System.out.print("Enter new role (" + String.join(", ", Roles.ALL) + ") or 0 to cancel: ");
            String input = scanner.nextLine().trim().toUpperCase(Locale.ROOT);
            if (input.equals("0")) {
                return null;
            }
            if (Roles.ALL.contains(input)) {
                return input;
            }
            System.out.println("Invalid role. Valid roles are: " + String.join(", ", Roles.ALL) + ".");
        }
    }

    /** Keeps asking until a valid status is entered. Returns null if the admin enters 0 to cancel. */
    private String readValidStatus() {
        while (true) {
            System.out.print("Enter new status (APPROVED, PENDING) or 0 to cancel: ");
            String input = scanner.nextLine().trim().toUpperCase(Locale.ROOT);
            if (input.equals("0")) {
                return null;
            }
            if (VALID_STATUSES.contains(input)) {
                return input;
            }
            System.out.println("Invalid status. Valid statuses are: APPROVED, PENDING.");
        }
    }

    /** Prints any list of users as an aligned, bordered table. */
    static void printUserTable(List<User> users) {
        String row = "| %-5s | %-20s | %-26s | %-10s | %-10s |%n";
        String border = "+" + "-".repeat(7) + "+" + "-".repeat(22) + "+" + "-".repeat(28) + "+"
                + "-".repeat(12) + "+" + "-".repeat(12) + "+";

        System.out.println(border);
        System.out.printf(row, "ID", "FULL NAME", "EMAIL", "ROLE", "STATUS");
        System.out.println(border);
        if (users.isEmpty()) {
            System.out.printf("| %-83s |%n", "No users found.");
        } else {
            for (User u : users) {
                System.out.printf(row, u.id(), u.fullName(), u.email(), u.role(), u.status());
            }
        }
        System.out.println(border);
        System.out.println("Total: " + users.size());
    }
}

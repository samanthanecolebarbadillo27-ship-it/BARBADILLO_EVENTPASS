package com.eventpass.ui;

import com.eventpass.model.Event;
import com.eventpass.model.Registration;

import java.util.List;
import java.util.Scanner;

/** Small console helpers shared by the menus: safe number input and aligned tables. */
final class ConsoleUtil {
    private ConsoleUtil() {
    }

    /** Keeps asking until a whole number is typed, so letters/symbols never crash the app. */
    static int readInt(Scanner scanner, String prompt) {
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

    static void printEventTable(List<Event> events) {
        String row = "| %-5s | %-24s | %-18s | %-16s | %-9s |%n";
        String border = "+" + "-".repeat(7) + "+" + "-".repeat(26) + "+" + "-".repeat(20) + "+"
                + "-".repeat(18) + "+" + "-".repeat(11) + "+";
        System.out.println(border);
        System.out.printf(row, "ID", "TITLE", "VENUE", "DATE", "SEATS");
        System.out.println(border);
        if (events.isEmpty()) {
            System.out.printf("| %-78s |%n", "No events found.");
        }
        for (Event e : events) {
            System.out.printf(row, e.eventId(), cut(e.title(), 24), cut(e.venue(), 18), e.eventDate(),
                    e.registeredCount() + "/" + e.maxCapacity());
        }
        System.out.println(border);
    }

    static void printRegistrationTable(List<Registration> registrations) {
        String row = "| %-7s | %-8s | %-22s | %-20s | %-10s |%n";
        String border = "+" + "-".repeat(9) + "+" + "-".repeat(10) + "+" + "-".repeat(24) + "+"
                + "-".repeat(22) + "+" + "-".repeat(12) + "+";
        System.out.println(border);
        System.out.printf(row, "REG ID", "EVENT ID", "EVENT", "STUDENT", "STATUS");
        System.out.println(border);
        if (registrations.isEmpty()) {
            System.out.printf("| %-81s |%n", "No registrations found.");
        }
        for (Registration r : registrations) {
            System.out.printf(row, r.registrationId(), r.eventId(), cut(r.eventTitle(), 22),
                    cut(r.studentName(), 20), r.status());
        }
        System.out.println(border);
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}

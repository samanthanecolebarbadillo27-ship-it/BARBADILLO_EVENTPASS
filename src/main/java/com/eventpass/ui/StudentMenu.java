package com.eventpass.ui;

import com.eventpass.dao.EventDao;
import com.eventpass.dao.RegistrationDao;
import com.eventpass.model.User;

import java.sql.SQLException;
import java.util.Scanner;

/** Student Attendee menu: browse events, register, and cancel a registration. */
public class StudentMenu {
    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final Scanner scanner;

    public StudentMenu(EventDao eventDao, RegistrationDao registrationDao, Scanner scanner) {
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.scanner = scanner;
    }

    /** @return true if the user chose to exit the whole application, false on logout */
    public boolean show(User student) {
        while (true) {
            System.out.println("\n--- STUDENT ATTENDEE MENU ---");
            System.out.println("Signed in as: " + student.fullName());
            System.out.println("1. Browse Events");
            System.out.println("2. Register for an Event");
            System.out.println("3. View My Registrations");
            System.out.println("4. Cancel a Registration");
            System.out.println("5. Logout");
            System.out.println("6. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> browseEvents();
                case "2" -> register(student);
                case "3" -> viewMine(student);
                case "4" -> cancel(student);
                case "5" -> {
                    System.out.println("You have been logged out.");
                    return false;
                }
                case "6" -> {
                    System.out.println("Exiting EventPass. Goodbye!");
                    return true;
                }
                default -> System.out.println("Invalid option. Please choose 1-6.");
            }
        }
    }

    private void browseEvents() {
        try {
            System.out.println("\n=== UPCOMING EVENTS ===");
            ConsoleUtil.printEventTable(eventDao.getAllEvents());
        } catch (SQLException e) {
            System.out.println("Database error while loading events: " + e.getMessage());
        }
    }

    private void register(User student) {
        browseEvents();
        int eventId = ConsoleUtil.readInt(scanner, "Enter Event ID to register for (0 to cancel): ");
        if (eventId == 0) {
            System.out.println("Registration cancelled.");
            return;
        }
        try {
            int regId = registrationDao.registerForEvent(student.id(), eventId);
            System.out.println("SUCCESS: You are registered. Your Registration ID is #" + regId + ".");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not register: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error while registering: " + e.getMessage());
        }
    }

    private void viewMine(User student) {
        try {
            System.out.println("\n=== MY REGISTRATIONS ===");
            ConsoleUtil.printRegistrationTable(registrationDao.getRegistrationsByStudent(student.id()));
        } catch (SQLException e) {
            System.out.println("Database error while loading registrations: " + e.getMessage());
        }
    }

    private void cancel(User student) {
        viewMine(student);
        int regId = ConsoleUtil.readInt(scanner, "Enter Registration ID to cancel (0 to go back): ");
        if (regId == 0) {
            return;
        }
        try {
            registrationDao.cancelRegistration(student.id(), regId);
            System.out.println("SUCCESS: Registration #" + regId + " has been CANCELLED.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not cancel: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error while cancelling: " + e.getMessage());
        }
    }
}

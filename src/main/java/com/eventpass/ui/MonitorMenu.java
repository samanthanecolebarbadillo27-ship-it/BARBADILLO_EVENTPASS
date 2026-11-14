package com.eventpass.ui;

import com.eventpass.dao.AttendanceDao;
import com.eventpass.dao.EventDao;
import com.eventpass.dao.RegistrationDao;
import com.eventpass.model.User;

import java.sql.SQLException;
import java.util.Scanner;

/** Gate Monitor / Usher menu: look up an event's registrations and check students in. */
public class MonitorMenu {
    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final AttendanceDao attendanceDao;
    private final Scanner scanner;

    public MonitorMenu(EventDao eventDao, RegistrationDao registrationDao, AttendanceDao attendanceDao,
                       Scanner scanner) {
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.attendanceDao = attendanceDao;
        this.scanner = scanner;
    }

    /** @return true if the user chose to exit the whole application, false on logout */
    public boolean show(User monitor) {
        while (true) {
            System.out.println("\n--- GATE MONITOR MENU ---");
            System.out.println("Signed in as: " + monitor.fullName());
            System.out.println("1. View Events");
            System.out.println("2. View Registrations for an Event");
            System.out.println("3. Check In a Student");
            System.out.println("4. Logout");
            System.out.println("5. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> viewEvents();
                case "2" -> viewRegistrations();
                case "3" -> checkIn(monitor);
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

    private void viewEvents() {
        try {
            System.out.println("\n=== EVENTS ===");
            ConsoleUtil.printEventTable(eventDao.getAllEvents());
        } catch (SQLException e) {
            System.out.println("Database error while loading events: " + e.getMessage());
        }
    }

    private void viewRegistrations() {
        viewEvents();
        int eventId = ConsoleUtil.readInt(scanner, "Enter Event ID (0 to cancel): ");
        if (eventId == 0) {
            return;
        }
        try {
            System.out.println("\n=== REGISTRATIONS FOR EVENT #" + eventId + " ===");
            ConsoleUtil.printRegistrationTable(registrationDao.getRegistrationsByEvent(eventId));
        } catch (SQLException e) {
            System.out.println("Database error while loading registrations: " + e.getMessage());
        }
    }

    private void checkIn(User monitor) {
        viewRegistrations();
        int regId = ConsoleUtil.readInt(scanner, "Enter Registration ID to check in (0 to cancel): ");
        if (regId == 0) {
            System.out.println("Check-in cancelled.");
            return;
        }

        String arrival = null;
        while (arrival == null) {
            System.out.print("Arrival status - 1. ON_TIME  2. LATE  (0 to cancel): ");
            switch (scanner.nextLine().trim()) {
                case "1" -> arrival = "ON_TIME";
                case "2" -> arrival = "LATE";
                case "0" -> {
                    System.out.println("Check-in cancelled.");
                    return;
                }
                default -> System.out.println("Invalid choice. Enter 1, 2, or 0.");
            }
        }

        try {
            attendanceDao.checkIn(monitor.id(), regId, arrival);
            System.out.println("SUCCESS: Registration #" + regId + " checked in (" + arrival + ").");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not check in: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error while checking in: " + e.getMessage());
        }
    }
}

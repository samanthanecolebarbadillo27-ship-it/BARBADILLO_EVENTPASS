package com.eventpass.ui;

import com.eventpass.dao.EventDao;
import com.eventpass.dao.RegistrationDao;
import com.eventpass.model.Event;
import com.eventpass.model.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/** Event Organizer menu: create events and review who registered. */
public class OrganizerMenu {
    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final Scanner scanner;

    public OrganizerMenu(EventDao eventDao, RegistrationDao registrationDao, Scanner scanner) {
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.scanner = scanner;
    }

    /** @return true if the user chose to exit the whole application, false on logout */
    public boolean show(User organizer) {
        while (true) {
            System.out.println("\n--- EVENT ORGANIZER MENU ---");
            System.out.println("Signed in as: " + organizer.fullName());
            System.out.println("1. Create Event");
            System.out.println("2. View My Events");
            System.out.println("3. View Registrations of My Event");
            System.out.println("4. Logout");
            System.out.println("5. Exit Application");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> createEvent(organizer);
                case "2" -> viewMyEvents(organizer);
                case "3" -> viewRegistrations(organizer);
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

    private void createEvent(User organizer) {
        System.out.println("\n--- Create Event ---");
        System.out.print("Event title: ");
        String title = scanner.nextLine().trim();
        System.out.print("Venue: ");
        String venue = scanner.nextLine().trim();

        LocalDateTime date;
        while (true) {
            System.out.print("Date and time (yyyy-MM-dd HH:mm, 0 to cancel): ");
            String input = scanner.nextLine().trim();
            if (input.equals("0")) {
                System.out.println("Event creation cancelled.");
                return;
            }
            try {
                date = LocalDateTime.parse(input, EventDao.DATE_FORMAT);
                break;
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Example: 2026-12-05 14:30");
            }
        }

        int capacity = ConsoleUtil.readInt(scanner, "Maximum capacity: ");
        try {
            int id = eventDao.createEvent(organizer.id(), title, venue, date, capacity);
            System.out.println("SUCCESS: Event #" + id + " '" + title + "' created.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not create event: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error while creating event: " + e.getMessage());
        }
    }

    private void viewMyEvents(User organizer) {
        try {
            System.out.println("\n=== MY EVENTS ===");
            ConsoleUtil.printEventTable(eventDao.getEventsByOrganizer(organizer.id()));
        } catch (SQLException e) {
            System.out.println("Database error while loading events: " + e.getMessage());
        }
    }

    private void viewRegistrations(User organizer) {
        try {
            List<Event> mine = eventDao.getEventsByOrganizer(organizer.id());
            System.out.println("\n=== MY EVENTS ===");
            ConsoleUtil.printEventTable(mine);
            if (mine.isEmpty()) {
                return;
            }
            while (true) {
                int eventId = ConsoleUtil.readInt(scanner, "Enter Event ID (0 to cancel): ");
                if (eventId == 0) {
                    return;
                }
                if (mine.stream().noneMatch(e -> e.eventId() == eventId)) {
                    System.out.println("Event ID " + eventId + " is not one of your events. Try again.");
                    continue;
                }
                System.out.println("\n=== REGISTRATIONS FOR EVENT #" + eventId + " ===");
                ConsoleUtil.printRegistrationTable(registrationDao.getRegistrationsByEvent(eventId));
                return;
            }
        } catch (SQLException e) {
            System.out.println("Database error while loading registrations: " + e.getMessage());
        }
    }
}

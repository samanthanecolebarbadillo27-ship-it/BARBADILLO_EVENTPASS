package com.eventpass;

import com.eventpass.dao.ActivityLogDao;
import com.eventpass.dao.DatabaseInitializer;
import com.eventpass.dao.UserDao;
import com.eventpass.ui.Authentication;

import java.sql.SQLException;
import java.util.Scanner;

public final class Move-Item eventpass.db eventpass_backup.db
git pull origin mainMove-Item eventpass.db eventpass_backup.db
git pull origin mainMain {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            // 1. Ensure required database tables exist before showing the CLI.
            DatabaseInitializer.initialize();

            // 2. Create shared instances for the application's UI.
            UserDao userDao = new UserDao();
            ActivityLogDao activityLogDao = new ActivityLogDao();
            Scanner scanner = new Scanner(System.in);

            // 3. Delegate registration, login, and role routing.
            new Authentication(userDao, activityLogDao, scanner).start();
        } catch (SQLException e) {
            System.err.println("Unable to initialize EventPass database: " + e.getMessage());
        }
    }
}

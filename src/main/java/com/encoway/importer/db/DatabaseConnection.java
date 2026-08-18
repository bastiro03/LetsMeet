package com.encoway.importer.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static final String URL = "jdbc:postgresql://localhost:5432/lf8_lets_meet_db";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "secret";

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }
}

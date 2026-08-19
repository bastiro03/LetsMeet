package com.encoway.importer.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

//DDL
public class SchemaCreator {

    private static final String VIEW_SQL = "/sql/view.sql";

    public void createSchema() throws SQLException, IOException {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE users (
                    id SERIAL PRIMARY KEY,
                    email TEXT NOT NULL UNIQUE,
                    first_name TEXT,
                    last_name TEXT,
                    birth_date DATE,
                    postal_code TEXT,
                    city TEXT
                );
            """);

            statement.execute(loadViewSql());
        }
    }

    private String loadViewSql() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(VIEW_SQL)) {
            if (in == null) {
                throw new IOException("SQL-Ressource nicht gefunden: " + VIEW_SQL);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

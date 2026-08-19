package com.encoway.importer.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

//DDL
public class SchemaCreator {
    public void createSchema() throws SQLException {
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

            statement.execute("""
                CREATE OR REPLACE VIEW migration_users AS
                SELECT email, first_name, last_name, birth_date, postal_code, city
                FROM users;
            """);
        }
    }
}

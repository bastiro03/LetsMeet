package com.encoway.importer.data;

import com.encoway.importer.db.DatabaseConnection;
import com.encoway.importer.model.UserRecord;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class DataImporter {

    public void importUsers(List<UserRecord> users) throws SQLException {
        String sql = """
                INSERT INTO users(email, first_name, last_name, birth_date, postal_code, city)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (UserRecord userRecord : users) {
                if (userRecord.email() == null || userRecord.email().isBlank()) {
                    continue;
                }

                preparedStatement.setString(1, userRecord.email());
                preparedStatement.setString(2, userRecord.firstName());
                preparedStatement.setString(3, userRecord.lastName());
                preparedStatement.setObject(4, userRecord.birthDate());
                preparedStatement.setString(5, userRecord.postalCode());
                preparedStatement.setString(6, userRecord.city());
                preparedStatement.executeUpdate();
            }
        }
    }
}
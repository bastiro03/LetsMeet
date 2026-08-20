package com.encoway.importer.data;

import com.encoway.importer.db.DatabaseConnection;
import com.encoway.importer.dto.HobbyRecord;
import com.encoway.importer.dto.UserRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class DataImporter {

    public void importUsers(List<UserRecord> users) throws SQLException {
        String sql = """
                INSERT INTO users(email, first_name, last_name, birth_date, postal_code,
                                  city, phone, gender)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
                preparedStatement.setString(7, userRecord.phone());
                preparedStatement.setString(8, userRecord.gender());
                preparedStatement.executeUpdate();
            }
        }
    }

    public void importInterests(List<UserRecord> users) throws SQLException {
        String sql = """
                INSERT INTO user_interests(email, interest_code)
                VALUES (?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (UserRecord userRecord : users) {
                if (userRecord.email() == null || userRecord.interest() == null) {
                    continue;
                }
                preparedStatement.setString(1, userRecord.email());
                preparedStatement.setString(2, userRecord.interest());
                preparedStatement.executeUpdate();
            }
        }
    }

    public void importHobbies(List<HobbyRecord> hobbies) throws SQLException {
        String sql = """
                INSERT INTO user_hobbies(email, hobby_name, priority, source)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (email, hobby_name, source) DO NOTHING
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (HobbyRecord hobby : hobbies) {
                preparedStatement.setString(1, hobby.email());
                preparedStatement.setString(2, hobby.hobbyName());
                preparedStatement.setInt(3, hobby.priority());
                preparedStatement.setString(4, hobby.source());
                preparedStatement.executeUpdate();
            }
        }
    }
}
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
                ON CONFLICT (email, interest_code) DO NOTHING
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            for (UserRecord userRecord : users) {
                if (userRecord.email() == null || userRecord.interest() == null) {
                    continue;
                }
                String raw = userRecord.interest().trim();
                // „mw" bedeutet interessiert an m und w -> zwei Zeilen (Befund 1609 statt 1576)
                List<String> codes;
                if ("mw".equals(raw)) {
                    codes = List.of("m", "w");
                } else {
                    codes = List.of(raw);
                }
                for (String code : codes) {
                    preparedStatement.setString(1, userRecord.email());
                    preparedStatement.setString(2, code);
                    preparedStatement.executeUpdate();
                }
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
                if (hobby.priority() == null) {
                    preparedStatement.setNull(3, java.sql.Types.INTEGER);
                } else {
                    preparedStatement.setInt(3, hobby.priority());
                }
                preparedStatement.setString(4, hobby.source());
                preparedStatement.executeUpdate();
            }
        }
    }
}
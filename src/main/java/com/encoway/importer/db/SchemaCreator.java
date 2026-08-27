package com.encoway.importer.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Erzeugt das Zielmodell im Schema {@code public} und stellt die Views der
 * Datenverträge V2/V3 bereit.
 *
 * <p>Die interne Struktur ist bewusst aufgeräumt (dritte Normalform): Jede
 * Zuordnung – Interesse, Hobby, Like, Nachricht – hat ihre eigene Tabelle.
 * Die Views sind die verbindliche Schnittstelle zur Kundinnen-App; ihre Namen
 * und Spaltentypen sind exakt durch den Datenvertrag vorgegeben.</p>
 * <p>V3-Erweiterung: `user_hobbies.priority` ist nullable (XML-Hobbys haben
 * `null`), und `rejections`/`migration_rejections` dokumentiert abgelehnte
 * Transferfälle.</p>
 */
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
                    city TEXT,
                    phone TEXT,
                    gender TEXT
                );
            """);

            statement.execute("""
                CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));
            """);

            statement.execute("""
                CREATE TABLE user_interests (
                    email TEXT NOT NULL REFERENCES users(email),
                    interest_code TEXT NOT NULL,
                    PRIMARY KEY (email, interest_code)
                );
            """);

            statement.execute("""
                CREATE TABLE user_hobbies (
                    email TEXT NOT NULL REFERENCES users(email),
                    hobby_name TEXT NOT NULL,
                    priority INTEGER CHECK (priority BETWEEN -100 AND 100),
                    source TEXT NOT NULL,
                    PRIMARY KEY (email, hobby_name, source)
                );
            """);

            statement.execute("""
                CREATE TABLE likes (
                    id SERIAL PRIMARY KEY,
                    liker_email TEXT NOT NULL REFERENCES users(email),
                    liked_email TEXT NOT NULL REFERENCES users(email),
                    status TEXT,
                    liked_at TIMESTAMP
                );
            """);

            statement.execute("""
                CREATE TABLE messages (
                    id SERIAL PRIMARY KEY,
                    sender_email TEXT NOT NULL REFERENCES users(email),
                    receiver_email TEXT NOT NULL REFERENCES users(email),
                    body TEXT,
                    sent_at TIMESTAMP,
                    conversation_id INTEGER
                );
            """);

            statement.execute("""
                CREATE TABLE photos (
                    id SERIAL PRIMARY KEY,
                    email TEXT NOT NULL REFERENCES users(email),
                    photo_url TEXT,
                    is_profile_picture BOOLEAN DEFAULT FALSE,
                    uploaded_at TIMESTAMP
                );
            """);

            statement.execute("""
                CREATE TABLE rejections (
                    id SERIAL PRIMARY KEY,
                    source TEXT NOT NULL,
                    source_ref TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    UNIQUE (source, source_ref)
                );
            """);

            statement.execute("""
                CREATE OR REPLACE VIEW migration_users AS
                SELECT email, first_name, last_name, birth_date, postal_code, city,
                       phone, gender
                FROM users;
            """);

            statement.execute("""
                CREATE OR REPLACE VIEW migration_user_interests AS
                SELECT email, interest_code
                FROM user_interests;
            """);

            statement.execute("""
                CREATE OR REPLACE VIEW migration_user_hobbies AS
                SELECT email, hobby_name, priority, source
                FROM user_hobbies;
            """);

            statement.execute("""
                CREATE OR REPLACE VIEW migration_likes AS
                SELECT liker_email, liked_email, status, liked_at
                FROM likes;
            """);

            statement.execute("""
                CREATE OR REPLACE VIEW migration_messages AS
                SELECT sender_email, receiver_email, body, sent_at, conversation_id
                FROM messages;
            """);

            statement.execute("""
                CREATE OR REPLACE VIEW migration_rejections AS
                SELECT source, source_ref, reason
                FROM rejections;
            """);
        }
    }
}
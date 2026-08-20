-- DDL — PostgreSQL-Zielsystem (Akt 2, V2)
-- Entspricht src/main/java/com/encoway/importer/db/SchemaCreator.java.
-- Aufbau: leeren → Schema erzeugen → Excel-Import (Java) → MongoDB-Import (Python)
-- → prüfen (Kundinnen-Checker V2).
--
-- Hinweis: Diese Datei enthält bewusst kein DROP. Das Leeren ist ein eigener
-- Schritt des Neuaufbaus („leeren → importieren → prüfen"); ein wiederholter
-- Lauf ohne Leeren schlägt an CREATE TABLE fehl — gewollt (Befundnotiz).

-- ---------------------------------------------------------------------------
-- Tabellen (3. Normalform: jede Zuordnung hat eine eigene Tabelle)
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id          SERIAL PRIMARY KEY,
    email       TEXT NOT NULL UNIQUE,
    first_name  TEXT,
    last_name   TEXT,
    birth_date  DATE,
    postal_code TEXT,
    city        TEXT,
    phone       TEXT,
    gender      TEXT
);

CREATE TABLE user_interests (
    email         TEXT NOT NULL REFERENCES users(email),
    interest_code TEXT NOT NULL,
    PRIMARY KEY (email, interest_code)
);

CREATE TABLE user_hobbies (
    email      TEXT NOT NULL REFERENCES users(email),
    hobby_name TEXT NOT NULL,
    priority   INTEGER NOT NULL CHECK (priority BETWEEN -100 AND 100),
    source     TEXT NOT NULL,
    PRIMARY KEY (email, hobby_name, source)
);

CREATE TABLE likes (
    id          SERIAL PRIMARY KEY,
    liker_email TEXT NOT NULL REFERENCES users(email),
    liked_email TEXT NOT NULL REFERENCES users(email),
    status      TEXT,
    liked_at    TIMESTAMP
);

CREATE TABLE messages (
    id              SERIAL PRIMARY KEY,
    sender_email    TEXT NOT NULL REFERENCES users(email),
    receiver_email  TEXT NOT NULL REFERENCES users(email),
    body            TEXT,
    sent_at         TIMESTAMP,
    conversation_id INTEGER
);

CREATE TABLE photos (
    id                 SERIAL PRIMARY KEY,
    email              TEXT NOT NULL REFERENCES users(email),
    photo_url          TEXT,
    is_profile_picture BOOLEAN DEFAULT FALSE,
    uploaded_at        TIMESTAMP
);

-- E-Mail-Schlüssel sprachunabhängig eindeutig (Vertrag V2: case-insensitiv)
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));

-- ---------------------------------------------------------------------------
-- Views des Datenvertrags V2 (Namen und Spalten exakt wie vereinbart)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE VIEW migration_users AS
    SELECT email, first_name, last_name, birth_date, postal_code, city,
           phone, gender
    FROM users;

CREATE OR REPLACE VIEW migration_user_interests AS
    SELECT email, interest_code
    FROM user_interests;

CREATE OR REPLACE VIEW migration_user_hobbies AS
    SELECT email, hobby_name, priority, source
    FROM user_hobbies;

CREATE OR REPLACE VIEW migration_likes AS
    SELECT liker_email, liked_email, status, liked_at
    FROM likes;

CREATE OR REPLACE VIEW migration_messages AS
    SELECT sender_email, receiver_email, body, sent_at, conversation_id
    FROM messages;
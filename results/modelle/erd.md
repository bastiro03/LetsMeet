# Zielmodell — LetsMeet (Akt 2, V2)

Physisches Modell des PostgreSQL-Zielsystems. Der Aufbau läuft verbindlich in der
Reihenfolge **leeren → Schema erzeugen → Excel-Import → MongoDB-Import → prüfen**.
Die DDL liegt in `results/modelle/ddl-ziel.sql` und identisch im
Java-`SchemaCreator` (`src/main/java/com/encoway/importer/db/SchemaCreator.java`).

## ER-Diagramm

Das ER-Diagramm entsteht in der Begleit-Website (LetsMeet-Modellierungsstation).
Die **Share-URL** wird hier und in der Befundnotiz gesichert:

- ERD-Share-URL: *(hier eintragen)*

```mermaid
erDiagram
    USERS ||--o{ USER_INTERESTS : "hat Interesse"
    USERS ||--o{ USER_HOBBIES   : "hat Hobby"
    USERS ||--o{ LIKES          : "liked_by"
    USERS ||--o{ LIKES          : "liked"
    USERS ||--o{ MESSAGES       : "sender"
    USERS ||--o{ MESSAGES       : "receiver"
    USERS ||--o{ PHOTOS         : "besitzt"

    USERS {
        bigserial id PK
        text     email UK
        text     first_name
        text     last_name
        date     birth_date
        text     postal_code
        text     city
        text     phone
        text     gender
    }
    USER_INTERESTS {
        text email FK
        text interest_code
    }
    USER_HOBBIES {
        text    email FK
        text    hobby_name
        integer priority  "-100..100"
        text    source
    }
    LIKES {
        bigserial id PK
        text      liker_email FK
        text      liked_email FK
        text      status
        timestamp liked_at
    }
    MESSAGES {
        bigserial id PK
        text      sender_email FK
        text      receiver_email FK
        text      body
        timestamp sent_at
        integer   conversation_id
    }
    PHOTOS {
        bigserial id PK
        text      email FK
        text      photo_url
        boolean   is_profile_picture
        timestamp uploaded_at
    }
```

## Datenherkunft

| Tabelle            | Quelle             | Importweg                     |
|--------------------|--------------------|-------------------------------|
| `users`            | Excel + MongoDB    | Java (Excel) / Python (Mongo) |
| `user_interests`   | Excel              | Java                          |
| `user_hobbies`     | Excel              | Java (`source = 'excel'`)     |
| `likes`            | MongoDB            | Python                        |
| `messages`         | MongoDB            | Python                        |
| `photos`           | MongoDB (Akt 3)    | —                             |

## Modellentscheidungen

- **3. Normalform:** Jede Zuordnung (Interesse, Hobby, Like, Nachricht, Foto) ist eine eigene
  Tabelle. Keine Liste in einer Zelle, kein Mehrfachwert.
- **E-Mail als Verbindung der Quellen:** eindeutig, case-insensitiv; in den Views erscheint die
  Schreibweise der Excel-Quelle (Vertrag V2).
- **Priorität −100…100:** der fachlich mit der Kundin vereinbarte Bereich; die aktuelle
  Datenlieferung schöpft ihn nicht aus (vorgefunden: 0–100).
- **Keine Codetabellen:** `gender`/`interest_code` übernehmen die Quellwerte unverändert.
- **`source` an der Hobbyzuordnung:** dokumentiert die Herkunft; in Akt 2 durchgängig `excel`.
- **`photos`** gehört zu Akt 3, wird aber schon jetzt modelliert, damit das Schema stabil bleibt.
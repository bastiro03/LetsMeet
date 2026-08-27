# RUNBOOK — LetsMeet-Neuaufbau (Akt 2, V2)

Dieses Dokument beschreibt, wie ein anderes Team den Datenbestand für Akt 2 aus dem Nichts
neu aufbaut und prüft. Der Ablauf ist immer derselbe: **leeren → Excel-Import → MongoDB-Import
→ prüfen.**

Zwei Quellen, ein Ziel:

| Quelle | Importweg | Inhalt |
|---|---|---|
| `Lets Meet DB Dump.xlsx` | Java (`Main`, POI + JDBC) | Personen, Interessen, Hobbys |
| MongoDB `LetsMeet.users` | Python-Notebook (`pymongo` + SQLAlchemy) | Likes, Nachrichten, ergänzende Profildaten |

Ziel: PostgreSQL `lf8_lets_meet_db`. Die Kundinnen-App liest ausschließlich die Views des
Datenvertrags V2.

Voraussetzungen:

- Java 21 + Maven (`mvn -v`), oder der Import läuft in einer Umgebung mit Maven
- Python 3 mit `pymongo`, `sqlalchemy`, `pandas`, `pg8000` (Schulserver) bzw. `psycopg2` (Docker)
- Laufende Dienste (Variante A oder B)

---

## Variante A — Docker

```bash
docker compose up -d
```

## Variante B — Schulserver

```bash
letsmeet up
```

---

## Schritt 1 — Leeren

Alle Tabellen und Views im Schema `public` löschen.

Variante A:

```bash
docker compose exec postgres_for_lf8_starter psql -U user -d lf8_lets_meet_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

Variante B:

```bash
letsmeet leeren
```

---

## Schritt 2 — Excel-Import (Java)

Baut das Schema (Tabellen + V2-Views) und importiert Personen, Interessen und Hobbys.
Die Rekonstruktionsregeln für die verschobene Excel-Datei stehen in `results/befundnotiz.md`.
Tests laufen beim Bauen automatisch mit.

```bash
mvn -q package
java -jar target/letsmeet-import-1.0.0-jar-with-dependencies.jar
```

Erwartete Ausgabe (Clean-Dump 2026-08-27, Hybrid-Reader unterstützt auch Verschoben):

```
Excel-Import completed: 1576 users, 4828 hobbies.
```

Danach in der Datenbank (Interessen: `mw` -> 2 Zeilen, daher 1609):

```sql
-- 1576 users  1609 user_interests  4828 user_hobbies  500 likes  300 messages (nach Schritt 3)
SELECT count(*) FROM users;         -- 1576
SELECT count(*) FROM user_interests;-- 1609 (mw gesplittet, s. Befundnotiz 2026-08-27)
SELECT count(*) FROM user_hobbies;  -- 4828
```

---

## Schritt 3 — MongoDB-Import (Python)

Ausführen im JupyterLab (Schulserver) oder in der lokalen Python-Umgebung (Docker):

1. `notebooks/02-profilierung-mongodb.ipynb` — Quelle profilieren (Pflicht vor dem Import)
2. `notebooks/03-import-mongodb.ipynb` — Likes und Nachrichten importieren (enthält Mapping `_id`/`timestamp`/`message`, case-insensitive E-Mail-Mapping, Zeitformat-Fallback `YYYY-MM-DD`/`DD.MM.YYYY` und die Stammdaten-Korrekturen für 7 Namen/3 Telefone – s. Befundnotiz 2026-08-27)

Im Notebook-Import sind zwei Stellen bewusst als Entscheidung sichtbar: der Umgang mit
**verwaisten Referenzen** (E-Mails, die nur in der MongoDB vorkommen) und die Übernahme
**ergänzender Profildaten** (Widersprüche zur Excel-Quelle). Die getroffene Regel gehört in die
Befundnotiz.

Alternativ ohne Notebook direkt mit Python (Docker-Variante A):

```bash
py import_mongo_fixed.py   # liegt im Projektroot, nutzt psycopg2/pg8000-Fallback
```

Danach in der Datenbank:

```sql
SELECT count(*) FROM likes;    -- 500
SELECT count(*) FROM messages; -- 300
SELECT count(*) FROM user_interests; -- 1609 (nach Interessen-Splitting)
```

---

## Schritt 4 — Prüfen (Kundinnen-Checker V2)

Zuerst die App für V2 neu starten, dann den Prüfbefehl ausführen. Exit-Code `0` = bestanden.

Variante A:

```bash
LETSMEET_CONTRACT_VERSION=V2 docker compose up -d --force-recreate kundinnen_app
docker compose run --rm -e CONTRACT_VERSION=V2 kundinnen_app node server/dist/cli.js
```

Variante B:

```bash
letsmeet contract V2
letsmeet check V2
```

---

## Tests

```bash
mvn -q test
```

Die Tests lesen die Excel-Quelle und prüfen Mengen, Eindeutigkeit und zentrale
Rekonstruktionsregeln (bekannte Personen, Hobby-Parsing). Der Kundinnen-Checker ergänzt diese
Tests, ersetzt sie aber nicht.

## Zurücksetzen (komplett, inkl. Prüfverlauf)

Nur wenn der komplette Stand inklusive Datenvolumes weg soll — für einen normalen Neuaufbau
reicht Schritt 1.

Variante A:

```bash
docker compose down -v
```

Variante B:

```bash
letsmeet reset-all
```

## Auf einen Blick

| Schritt | Werkzeug | Befehl |
|---|---|---|
| Leeren | Docker / letsmeet | `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` / `letsmeet leeren` |
| Excel-Import | Java | `mvn -q package && java -jar target/letsmeet-import-1.0.0-jar-with-dependencies.jar` |
| MongoDB-Import | Python | Notebooks 02 + 03 |
| Prüfen | Kundinnen-Checker | `… kundinnen_app node server/dist/cli.js` / `letsmeet check V2` |
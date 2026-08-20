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

Erwartete Ausgabe:

```
Excel-Import completed: 1573 users, 4815 hobbies.
```

Danach in der Datenbank:

```sql
-- 1573  users          1561 user_interests   4815 user_hobbies
SELECT count(*) FROM users;         -- 1573
SELECT count(*) FROM user_interests;-- 1573 (eine Zeile je Person mit Interesse)
SELECT count(*) FROM user_hobbies;  -- 4815
```

---

## Schritt 3 — MongoDB-Import (Python)

Ausführen im JupyterLab (Schulserver) oder in der lokalen Python-Umgebung (Docker):

1. `notebooks/02-profilierung-mongodb.ipynb` — Quelle profilieren (Pflicht vor dem Import)
2. `notebooks/03-import-mongodb.ipynb` — Likes und Nachrichten importieren

Im Notebook-Import sind zwei Stellen bewusst als Entscheidung sichtbar: der Umgang mit
**verwaisten Referenzen** (E-Mails, die nur in der MongoDB vorkommen) und die Übernahme
**ergänzender Profildaten** (Widersprüche zur Excel-Quelle). Die getroffene Regel gehört in die
Befundnotiz.

Danach in der Datenbank:

```sql
SELECT count(*) FROM likes;    -- aus dem Notebook notieren
SELECT count(*) FROM messages; -- aus dem Notebook notieren
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
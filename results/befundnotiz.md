# Befundnotiz — LetsMeet-Datenmigration

Drei Fragen tragen diese Notiz, dazu einmal im Projekt eine vierte. Zu jedem Eintrag gehört das
Datum. Stichpunkte genügen, solange jemand anderes sie versteht.

---

## 1. Was ist uns an der Quelle aufgefallen?

Beobachtungen, keine Vermutungen. Quelle für Akt 1: `Lets Meet DB Dump.xlsx`.

### 2026-08-19 — Profilierung der Excel-Datei

- Umfang: **1576 Datenzeilen**, eine Kopfzeile, ein Tabellenblatt.
- Spalten: `Nachname, Vorname` · `Straße Nr, PLZ Ort` · `Telefon` · `Hobby1 %Prio1%; … Hobby5 %Prio5%;`
  · `E-Mail` · `Geschlecht (m/w/nonbinary)` · `Interessiert an` · `Geburtsdatum`.
- E-Mail: **0 leere**, **0 Duplikate** — auch groß/klein geschrieben eindeutig (z. B.
  `Martin.Forster@web.ork` vs. `martin.forster@web.ork`). Alle Werte enthalten `@`.
- Name: zerfällt immer in zwei Teile per `", "`. **73 Zeilen** haben ein Leerzeichen vorm Komma,
  z. B. `Stanislav , Petrov` — der Nachname wird laut Vertrag `Stanislav ` (mit Leerzeichen).
- Adresse: 1573× genau drei Teile, **3× vier Teile** — das sind genau die Fälle
  `…, 17109, Demmin, Hansestadt` (Ort enthält selbst ein Komma). **6 Zeilen** mit Hausnummer `0`
  (z. B. `Minslebener Str. 0, 46286, Dorsten`).
- Geburtsdatum: durchgängig `dd.MM.yyyy` (Text).
- Telefon: uneinheitliche Formate, z. B. `02372 8020` und `06221 / 98689`.
- Hobbys: Mehrfachwert in einer Zelle, semikolon-getrennt, mit `%Priorität%` je Hobby.
- Geschlecht: Werte `m` (918), `w` (620), `nb` (38). „Interessiert an": kodierte Werte `m`, `w`, `mw`.
  Die Spaltenüberschriften sind Beschriftungen, keine Wertespezifikation.
- Als Quelle in Akt 1 ist **nur** die Excel-Datei erlaubt. `Lets_Meet_Hobbies.xml` (Nachlieferung)
  und die MongoDB (Akt 2) bleiben unberührt.

---

## 2. Was haben wir daraufhin entschieden, und warum?

Die Regel, die wir angewendet haben, dazu die Alternative, die wir verworfen haben.

### 2026-08-19 — Physisches Modell und Importregeln

- **Minimales Modell:** nur die sechs Spalten der geforderten View plus Primärschlüssel
  (`users(id, email, first_name, last_name, birth_date, postal_code, city)`). Entscheidung:
  Akt-1-Modell so klein wie möglich; Straße, Telefon, Hobbys, Geschlecht und Interessen werden
  nicht importiert, weil V1 sie nicht braucht. Verworfen: alle Spalten schon jetzt aufnehmen —
  hätte für Akt 1 unnötige Transformationsregeln (Hobbys, Geschlecht) mit sich gebracht.
- **Primärschlüssel:** synthetisches `id SERIAL`. E-Mail als natürlicher Schlüssel wäre möglich
  (eindeutig, nicht leer), ist aber personenbezogen und könnte sich ändern. Die E-Mail wird
  trotzdem als `NOT NULL UNIQUE` gesichert, weil V1 sie als eindeutig verlangt.
- **Text unverändert übernehmen:** keine Bereinigung, keine Trimmung — äußere Leerzeichen bleiben
  laut Vertrag erhalten. Der Prüfstand vereinheitlicht selbst auf NFC. Verworfen: Daten vor dem
  Import zu säubern (z. B. `Stanislav ` → `Stanislav`), weil „Daten bereinigen" ein eigener,
  späterer Arbeitsschritt ist.
- **Trennung in zusammengesetzten Spalten:** `", "` (Komma + genau ein Leerzeichen) sowohl bei
  Name als auch Adresse. Für die Adresse gilt `split(", ", 3)`: Ort ist alles nach dem zweiten
  Komma — `Demmin, Hansestadt` bleibt ein Ort. Verworfen: Splitten nach allen Kommas — hätte
  `Hansestadt` als vierten Teil erzeugt.
- **Geburtsdatum:** als `DATE` mit Format `dd.MM.yyyy`. Numerische und Textzellen werden beide
  akzeptiert (die Quelle liefert durchgängig Text, numerische Zellen sind Absicherung).
- **Reproduzierbarkeit:** Der Import setzt eine **leere** Datenbank voraus (Schema `public` leer).
  Schema-Erzeugung und Import laufen als eigene Schritte; die Reihenfolge ist verbindlich
  „leeren → importieren → prüfen". Verworfen: `DROP TABLE IF EXISTS` im Import — die geforderte
  Arbeitsweise ist der Neuaufbau aus dem Nichts, nicht das „Reparieren" eines alten Standes.

---

## 3. Was haben wir nicht übernommen, und warum?

Was nicht importiert ist, fehlt sichtbar — hier steht, weshalb.

### 2026-08-19 — Für V1 nicht importiert

- **Straße mit Hausnummer** (Teil der Adressspalte): wird in V1 nicht benötigt, daher nicht ins
  Modell aufgenommen. Die Hausnummer `0` aus 6 Zeilen ist damit ebenfalls nicht Gegenstand des
  Imports — eine offene Frage an die Kundin.
- **Telefon:** wird in V1 nicht benötigt.
- **Hobbys** (Mehrfachwert mit Priorität): kommen mit der Nachlieferung und in Akt 2 ins Modell.
- **Geschlecht, „Interessiert an":** werden in V1 nicht benötigt; Werte bleiben unverändert
  kodiert, sobald sie importiert werden (keine eigene Codetabelle, kein Übersetzen).
- **`Lets_Meet_Hobbies.xml` und MongoDB:** gehören zu späteren Akten, bleiben unberührt.

### 2026-08-19 — Grenzen des Imports (Akt 1, nach Prüflauf)

- **Postleitzahlen werden nicht bereinigt.** Der Prüfstand meldet als Hinweis 15 Postleitzahlen mit
  führender Null (Quelle: 15) und 58 vierstellige Postleitzahlen (Quelle: 58). Datenbereinigung ist
  ein eigener späterer Arbeitsschritt, nicht Teil des Imports — deshalb unverändert übernommen.
- **Text bleibt ungetrimmt.** Nachnamen wie `Stanislav ` (mit Leerzeichen) sind gewollt und werden
  so gespeichert; der Prüfstand hat den Abgleich zeichengenau (NFC, ungetrimmt) bestätigt.
- **Import setzt eine leere Datenbank voraus.** Bei einem Lauf ohne vorheriges Leeren des Schemas
  schlägt `CREATE TABLE users` fehl (Relation existiert). Das ist Absicht: Arbeitsweise ist
  „leeren → importieren → prüfen", kein Reparatur-Import.
- **Nicht verifizierbare Angaben:** Sechs Adressen tragen die Hausnummer `0` (z. B.
  `Minslebener Str. 0`). Die Straße ist in V1 nicht im Modell, die Frage geht an die Kundin.
- **Verzogene Felder (Hobbys, Geschlecht, Interessen, Telefon):** werden in Akt 1 weder importiert
  noch geprüft; ihre Aufnahme und ihre Regeln sind Gegenstand von Akt 2.

---

## 4. Welche dieser Daten sind besonders schützenswert, und was folgt daraus?

Einmal im Projekt, spätestens wenn die Daten das erste Mal vollständig vor uns liegen.

### 2026-08-19

- **Personenbezogen und besonders schützenswert:** E-Mail-Adresse (Zuordnung einer Person, potenziell
  Konto-Kennung), Geburtsdatum (besonders sensibel), Name sowie Wohnort (PLZ + Ort) erlauben die
  Identifizierung realer Personen.
- **Konsequenzen für unseren Umgang:**
  - Die Datenbank ist nur lokal im Entwicklungsnetz erreichbar (Compose bindet Ports an `127.0.0.1`);
    Zugangsdaten (`user`/`secret`) nicht in Git-committen, nicht im Klartext teilen.
  - Abgaben (SQL, Importcode) enthalten keine echten E-Mail-Adressen oder Geburtsdaten als
    Beispieldaten — Beispiele werden anonymisiert.
  - Das Original-Excel bleibt lokal; es wird nicht in das Versionsverzeichnis übernommen, sofern
    nicht ausdrücklich gefordert.
  - Für Akt 2: Rechtsgrundlage und Schutzbedarf werden bei der Modellierung geprüft.

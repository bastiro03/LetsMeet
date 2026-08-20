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

### 2026-08-20 — Korrektur: Die Datei ist verschoben (Akt 2, vor dem Import)

Die Beobachtungen vom 19.08. stammen aus der *unverschobenen* Sicht (Tabellenkopf als Quelle).
Beim tatsächlichen Import zeigt sich: Die Spalten A–H sind **unabhängig voneinander versetzt**
(Blockverschiebungen, Versätze −833 … +469 Zeilen). Fachliche Bedeutung je Spalte:

- **A = Name** (`Nachname, Vorname`) · **B = Telefon** · **C = Hobby** (`Name %Prio%`) ·
  **D = E-Mail** · **E = Geburtsdatum** (`dd.MM.yyyy`) · **F = „Interessiert an"** (`m/mw`) ·
  **G = Geschlecht** (`m/w/nb`) · **H = Adresse** (`Straße, PLZ, Ort`).

Konsequenzen (per Zeile gemessen, 1576 Datenzeilen):

- Die Werte stehen nicht je Person in der erwarteten Spalte. Felder werden daher **wertbasiert**
  erkannt, nicht über feste Spaltennummern: E-Mail = erster Wert mit `@` (Priorität D>C>E>G>F),
  Geburtsdatum = Wert `dd.MM.yyyy` (Priorität E>D>F>G), Telefon = Spalte B nur bei
  ziffernhaltigem Wert, Geschlecht = G sonst F, Interesse = F sonst G, Hobby = C sonst D.
- **Name:** Namen sind im Abgleich mit der E-Mail nur per **Token-Matching** zuverlässig
  (E-Mail-Lokalteil vs. Namensbestandteile, z. B. `martin.forster@…` ↔ `Forster, Martin`).
  1455 von 1573 E-Mails finden so einen Namen. Ohne Matching wären viele Namen falsch
  zugeordnet (Beispiel: `Hüneborn, Michael` steht 360 Zeilen über seiner E-Mail).
- **Adresse:** wertbasiert nicht eindeutig (Straße enthält Ziffern wie eine PLZ). Regel mit
  Rückgriff auf die Vorzeile: `H → A → vorherige H → vorherige A`. Gegen zwei bekannte
  Personen validiert: `martin.forster@…` → `46286 Dorsten`, `ansgar.lange@…` → `17109
  Demmin, Hansestadt`. 1570 von 1573 Adressen so gefunden.
- **E-Mail-Korrektur:** von 1576 Datenzeilen haben **3 Zeilen gar keine E-Mail** (R522, R559,
  R829; z. B. `Jansen, Frank`). Die Angabe „0 leere E-Mails" vom 19.08. trifft für den
  tatsächlichen Datensatz nicht zu. **3 weitere E-Mails** liegen nur in anomalen Spalten
  (`michael.hüneborn@…` in A, `myriam.greshake@…` in C, `eberhard.klempner@…` 33× in G) und
  können keiner Personenzeile eindeutig zugeordnet werden. E-Mail-Duplikate (case-insensitiv):
  **0** unter den 1573 importierbaren.
- **Geschlecht-Korrektur:** die Zählung `m 918 / w 620 / nb 38` vom 19.08. stimmt nicht mit
  den tatsächlichen Zellen überein (F/G enthalten nur `m`/`mw`, je 1× `w`/`nb` als Werte;
  `eberhard.klempner@…` steht 33× in Spalte G). Die Häufigkeitstabelle wird erst nach dem
  Import aus der Datenbank gezogen.
- **Hobby:** 1561 von 1573 Zeilen tragen eine Hobby-Zelle → **4815 Hobby-Zeilen**
  (Priorität zwischen zwei `%`-Zeichen, 0–100). Maximal 5 Hobbys je Person.
- **Importumfang:** 1573 Personen (eine je Datenzeile mit E-Mail). Die 6 abweichenden Fälle
  oben sind dokumentierte Grenzen.

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

### 2026-08-20 — V2-Modell, hybrider Import und Rekonstruktionsregeln

- **ERD-Share-URL (Begleit-Website):** *(hier eintragen — für den Abschluss von Akt 2 Pflicht)*
- **Hybrider Import (Kundin, „Beides"):** zwei Quellen, **ein** Ziel. Excel → PostgreSQL in Java
  (POI + JDBC, `SchemaCreator` + `DataImporter`); MongoDB → PostgreSQL in Python (pymongo +
  SQLAlchemy im Notebook). Die Kundinnen-App liest ausschließlich die PostgreSQL-Views.
- **Zielmodell (3. Normalform):** `users`, `user_interests`, `user_hobbies` (Priorität −100…100,
  `source='excel'`), `likes`, `messages`, `photos`. Die V2-Views (`migration_users`,
  `migration_user_interests`, `migration_user_hobbies`, `migration_likes`,
  `migration_messages`) sind die verbindliche Schnittstelle; interne Tabellen dürfen sich ändern.
- **Werte unverändert, keine Codetabellen:** Geschlecht/Interesse bleiben als Quellwerte
  (`m`, `w`, `nb`, `mw`) erhalten. Verworfen: Übersetzen in eigene Codes — der Vertrag verlangt
  unveränderte Werte.
- **Keine Platzhalter:** 3 Zeilen ohne E-Mail und 3 verwaiste E-Mails werden **nicht** importiert.
  Verworfen: synthetische E-Mail-Adressen — würden den Vertrag („Werte unverändert") verletzen
  und fiktive Personen erzeugen.
- **Hobby-Parsing:** Priorität zwischen zwei `%`-Zeichen (`Name %78%`), mehrere Hobbys
  semikolon-getrennt; nicht parsebare Zellen (z. B. Kopfzeilen-Platzhalter) werden übersprungen.
- **Name via Token-Matching, Adresse via Positionsregel:** s. Korrektur in Abschnitt 1. Beide
  Regeln sind dokumentierte Heuristiken und werden gegen den Kundinnen-Prüfstand (V2) verifiziert.

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

### 2026-08-20 — Für V2 nicht importiert / Grenzen

- **3 Zeilen ohne E-Mail** (R522 `Jansen, Frank`, R559 `Oberwinster, Maurice`, R829
  `Hügel, Margret`): keine E-Mail in der gesamten Zeile; ohne E-Mail kein Primärschlüssel und
  kein Vertragswert — Personen fehlen sichtbar. Alternative verworfen: Platzhalter-E-Mail.
- **3 verwaiste E-Mails** (`michael.hüneborn@…`, `myriam.greshake@…`, `eberhard.klempner@…`):
  liegen nur in anomalen Spalten, keine eigene Personenzeile mit Name/Adresse vorhanden. Eine
  Zuordnung wäre eine nicht belegbare Annahme — bewusst nicht importiert. (`eberhard.klempner@…`
  taucht 33× in Spalte G auf; dort ist die Zelle kein Geschlecht, sondern Rest einer
  verschobenen E-Mail-Spalte.)
- **~118 Personen ohne Namen:** Token-Matching findet für die restlichen E-Mails keinen
  Namenssatz; Name bleibt `NULL` statt erfunden. Zählt der Prüfstand diese als Fehler, wird
  nachgesteuert.
- **Name-Reihenfolge in der Quelle ist uneinheitlich** (`Nachname, Vorname` ist die Regel, aber
  auch `Vorname, Nachname` kommt vor). Das Matching prüft nur, dass beide Tokens vorkommen; die
  Reihenfolge wird wie in der Quelle übernommen.
- **Adresse ist eine Positionsheuristik** (H→A→Vorzeile), kein Wert-Abgleich; gegen zwei
  bekannte Personen validiert, offen für Prüfstandsfälle.

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

### 2026-08-20 — Akt 2: Telefon, Geschlecht, Interessen kommen hinzu

- **Neu schützenswert in V2:** **Telefonnummern** (Kontaktmöglichkeit einer Person) und
  **Geschlecht/Interessen** (besonders sensibel: können als biografische Merkmale gelten).
  Vorher wurden sie nicht importiert (V1-Minimalmodell).
- **Konsequenzen:**
  - Auch in der MongoDB nur lokal erreichbar (Compose bindet `27017` an `127.0.0.1`); Zugang
    ohne `auth` nur im Entwicklungsnetz, im Zweifel Zugriffskontrolle aktivieren.
  - Beispieldaten in Notebooks/SQL anonymisieren; keine echten Telefonnummern oder
    Geschlechtswerte in Doku-Beispielen nennen.
  - Löschung von Personen (Akt 3) betrifft dann auch Telefon/Geschlecht/Interessen.
  - Rechtsgrundlage für die Migration (Auftragsverarbeitung, Art. 6/28 DSGVO) beim Kundinnen-
    Termin erfragen und dokumentieren.

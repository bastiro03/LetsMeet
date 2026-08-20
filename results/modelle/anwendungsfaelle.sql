-- Anwendungsfall-Abfragen — LetsMeet (V2)
-- Eine beispielhafte Abfrage je Anwendungsfall aus dem Anwendungsfalldiagramm
-- (images/use-case.png). Zielsystem: PostgreSQL, Tabellen des Akt-2-Modells
-- (users, user_interests, user_hobbies, likes, messages, photos).
-- E-Mail-Adressen sind anonymisiert; echte Werte stehen nur in der Datenbank.

-- ---------------------------------------------------------------------------
-- 1) Name und Hobbies eines ausgewählten Nutzers ausgeben lassen („ansehen")
-- ---------------------------------------------------------------------------
-- Aktor: Nutzer. Erfordert login. Liefert die Person plus priorisierte Hobbys.
SELECT u.email,
       u.first_name,
       u.last_name,
       u.city,
       h.hobby_name,
       h.priority
FROM   users u
LEFT JOIN user_hobbies h ON h.email = u.email
WHERE  u.email = 'beispiel.person@anonym.te'   -- ausgewählter Nutzer
ORDER  BY h.priority DESC;

-- ---------------------------------------------------------------------------
-- 2) Eigene Stammdaten bearbeiten („bearbeiten")
-- ---------------------------------------------------------------------------
-- Aktor: Nutzer. Erfordert login. Schreibt ausschließlich die eigene Zeile.
-- Hier: Telefonnummer ändern. Die Zeile ist über die E-Mail des angemeldeten
-- Nutzers begrenzt — niemand darf fremde Zeilen ändern.
UPDATE users
SET    phone = '0123 / 4567890'
WHERE  email = 'beispiel.person@anonym.te';

-- ---------------------------------------------------------------------------
-- 3) Hobbies bearbeiten, ergänzen, priorisieren („hobbies")
-- ---------------------------------------------------------------------------
-- Aktor: Nutzer. Erweiterung von „bearbeiten". Ein Hobby mit Priorität setzen.
-- Der vertraglich vereinbarte Wertebereich ist -100 … 100.
INSERT INTO user_hobbies (email, hobby_name, priority, source)
VALUES ('beispiel.person@anonym.te', 'Wandern', 80, 'excel')
ON CONFLICT (email, hobby_name, source)
DO UPDATE SET priority = EXCLUDED.priority;

-- ---------------------------------------------------------------------------
-- 4) Foto anfügen, ändern, löschen („foto")
-- ---------------------------------------------------------------------------
-- Aktor: Nutzer. Erweiterung von „bearbeiten". Neues Profilbild anfügen und
-- das bisherige als solches abwählen. photos ist Teil des Zielmodells (V2).
INSERT INTO photos (email, photo_url, is_profile_picture, uploaded_at)
VALUES ('beispiel.person@anonym.te', 'https://cdn.anonym.te/bild-1.jpg', TRUE, now());

UPDATE photos
SET    is_profile_picture = FALSE
WHERE  email = 'beispiel.person@anonym.te'
  AND  photo_url <> 'https://cdn.anonym.te/bild-1.jpg';

-- ---------------------------------------------------------------------------
-- 5) Andere Teilnehmer kontaktieren („kontaktieren")
-- ---------------------------------------------------------------------------
-- Aktor: Anwender. Erfordert login. Eine Nachricht senden. Absender steht
-- links (gerichtete Beziehung).
INSERT INTO messages (sender_email, receiver_email, body, sent_at, conversation_id)
VALUES ('beispiel.person@anonym.te', 'andere.person@anonym.te', 'Hallo!', now(), 1);

-- ---------------------------------------------------------------------------
-- 6) Alle Daten bearbeiten („alleBearbeiten")
-- ---------------------------------------------------------------------------
-- Aktor: Administrator. Umfasst „bearbeiten". Der Admin sucht zuerst eine
-- Person, dann darf er jede Spalte ändern. Beispiel: Suche nach Name.
SELECT email, first_name, last_name, city, phone
FROM   users
WHERE  last_name ILIKE '%Muster%';

-- ---------------------------------------------------------------------------
-- 7) Nutzer mit ähnlichen Interessen finden („nutzerFinden")
-- ---------------------------------------------------------------------------
-- Aktor: Anwender. Erfordert login. Alle Nutzer, die mindestens ein
-- gemeinsames Interesse mit mir haben — außer mir selbst.
SELECT u.email, u.first_name, u.last_name, u.city,
       array_agg(DISTINCT ui.interest_code) AS gemeinsame_interessen
FROM   user_interests ui
JOIN   user_interests meine ON meine.email = 'beispiel.person@anonym.te'
                           AND meine.interest_code = ui.interest_code
JOIN   users u ON u.email = ui.email
WHERE  ui.email <> 'beispiel.person@anonym.te'
GROUP  BY u.email, u.first_name, u.last_name, u.city
ORDER  BY u.last_name;

-- ---------------------------------------------------------------------------
-- 8) Freundeliste: sich gegenseitig nach beiderseitiger Zustimmung aufnehmen
--    („freund")
-- ---------------------------------------------------------------------------
-- Aktor: Anwender. Erfordert login. Eine Freundschaft entsteht erst durch
-- zweiseitige Likes (zwei gerichtete Zeilen in beide Richtungen). Gesucht
-- werden nur Paare, bei denen beide Seiten zugestimmt haben.
SELECT LEAST(a.liker_email, a.liked_email)  AS a_seite,
       GREATEST(a.liker_email, a.liked_email) AS b_seite
FROM   likes a
JOIN   likes b ON a.liker_email = b.liked_email
              AND a.liked_email = b.liker_email
WHERE  a.status = 'like'
  AND  b.status = 'like'
  AND  a.liker_email < a.liked_email;

-- ---------------------------------------------------------------------------
-- 9) Login / am System anmelden („login")
-- ---------------------------------------------------------------------------
-- Aktor: alle. Erfordert ein Konto. E-Mail dient als Kennung; die
-- Passwortprüfung selbst liegt außerhalb der Datenbank (Hash, niemals
-- Klartext). Die Abfrage holt die Stammdaten für die Sitzung.
SELECT email, first_name, last_name, birth_date, city, gender
FROM   users
WHERE  email = 'beispiel.person@anonym.te';

-- ---------------------------------------------------------------------------
-- 10) Konto erstellen („neuesKonto")
-- ---------------------------------------------------------------------------
-- Aktor: Interessent. Erweiterung von „login" (Bedingung: noch kein Konto).
-- Neues Konto anlegen; die E-Mail ist der verbindliche Schlüssel (eindeutig,
-- case-insensitiv über einen Unique-Index auf lower(email)).
INSERT INTO users (email, first_name, last_name, birth_date, postal_code,
                   city, phone, gender)
VALUES ('neue.person@anonym.te', 'Neue', 'Person', '1990-01-01', '12345',
        'Musterstadt', NULL, NULL)
ON CONFLICT (email) DO NOTHING
RETURNING email;
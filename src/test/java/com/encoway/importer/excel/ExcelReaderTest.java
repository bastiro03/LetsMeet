package com.encoway.importer.excel;

import com.encoway.importer.dto.UserRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet die Rekonstruktion der Personen aus der verschobenen Excel-Quelle.
 *
 * <p>Die Quelle enthält 1576 Datenzeilen. Davon sind 1573 Zeilen einer E-Mail
 * eindeutig zuzuordnen; 3 Zeilen enthalten überhaupt keine E-Mail und werden
 * übersprungen (in der Befundnotiz dokumentiert). 3 weitere E-Mails liegen in
 * anomalen Spalten und werden ebenfalls nicht importiert.</p>
 */
class ExcelReaderTest {

    private static final String SOURCE_FILE = "Lets Meet DB Dump.xlsx";

    private static List<UserRecord> users;

    @BeforeAll
    static void readSource() throws Exception {
        File excel = resolveSource();
        users = new ExcelReader().readUsers(excel);
    }

    private static File resolveSource() throws Exception {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SOURCE_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
        }
        throw new IllegalStateException(SOURCE_FILE + " nicht gefunden.");
    }

    @Test
    void readsAllRowsThatContainAnEmail() {
        assertEquals(1573, users.size());
    }

    @Test
    void emailsAreNeverBlankAndUnique() {
        List<String> emails = users.stream().map(UserRecord::email).collect(Collectors.toList());
        assertTrue(emails.stream().noneMatch(e -> e == null || e.isBlank()), "leere E-Mail vorhanden");
        assertEquals(users.size(), Set.copyOf(emails).size(), "E-Mail nicht eindeutig");
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        Set<String> lower = users.stream()
                .map(u -> u.email().toLowerCase())
                .collect(Collectors.toSet());
        assertEquals(users.size(), lower.size(), "E-Mail case-insensitiv nicht eindeutig");
    }

    @Test
    void reconstructionKnownPersonForster() {
        UserRecord forster = byEmail("martin.forster@web.ork");
        assertEquals("Forster", forster.lastName());
        assertEquals("Martin", forster.firstName());
        assertEquals(LocalDate.of(1959, 3, 7), forster.birthDate());
        assertEquals("46286", forster.postalCode());
        assertEquals("Dorsten", forster.city());
        assertEquals("m", forster.gender());
    }

    @Test
    void reconstructionKnownPersonLange() {
        UserRecord lange = byEmail("ansgar.lange@1mal1.te");
        assertEquals("Lange", lange.lastName());
        assertEquals("Ansgar", lange.firstName());
        assertEquals("17109", lange.postalCode());
        assertEquals("Demmin, Hansestadt", lange.city());
    }

    @Test
    void reconstructionKnownPersonPetrov() {
        UserRecord petrov = byEmail("petrov.stanislav@a-o-l.kom");
        // Quellzelle „Stanislav , Petrov": Reihenfolge wird unverändert übernommen.
        assertEquals("Stanislav", petrov.lastName());
        assertEquals("Petrov", petrov.firstName());
        assertEquals("46149", petrov.postalCode());
        assertEquals("Oberhausen", petrov.city());
    }

    @Test
    void mostPersonsHaveAName() {
        assertTrue(users.stream().filter(u -> u.firstName() != null).count() >= 1450,
                "zu wenige Personen mit Vorname");
        assertTrue(users.stream().filter(u -> u.lastName() != null).count() >= 1450,
                "zu wenige Personen mit Nachname");
    }

    @Test
    void mostPersonsHaveAnAddress() {
        assertTrue(users.stream().filter(u -> u.postalCode() != null).count() >= 1560,
                "zu wenige Personen mit PLZ");
    }

    @Test
    void everyPersonHasAGender() {
        assertTrue(users.stream().noneMatch(u -> u.gender() == null || u.gender().isBlank()));
    }

    private UserRecord byEmail(String email) {
        return users.stream()
                .filter(u -> u.email().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow(() -> new AssertionError("kein User mit E-Mail " + email));
    }
}
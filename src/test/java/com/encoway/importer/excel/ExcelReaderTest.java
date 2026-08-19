package com.encoway.importer.excel;

import com.encoway.importer.model.UserRecord;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void readsEveryPersonFromTheSource() {
        assertEquals(1576, users.size());
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
    void nameSplitKeepsSpaceBeforeComma() {
        UserRecord petrov = users.stream()
                .filter(u -> u.email().equals("petrov.stanislav@a-o-l.kom"))
                .findFirst()
                .orElseThrow();
        assertEquals("Stanislav ", petrov.lastName());
        assertEquals("Petrov", petrov.firstName());
    }

    @Test
    void cityIsEverythingAfterTheSecondComma() {
        UserRecord lange = users.stream()
                .filter(u -> u.email().equals("ansgar.lange@1mal1.te"))
                .findFirst()
                .orElseThrow();
        assertEquals("17109", lange.postalCode());
        assertEquals("Demmin, Hansestadt", lange.city());
    }

    @Test
    void ordinaryAddressIsSplitIntoThreeParts() {
        UserRecord forster = users.stream()
                .filter(u -> u.email().equals("martin.forster@web.ork"))
                .findFirst()
                .orElseThrow();
        assertEquals("46286", forster.postalCode());
        assertEquals("Dorsten", forster.city());
    }

    @Test
    void birthDateIsParsedAsDate() {
        UserRecord forster = users.stream()
                .filter(u -> u.email().equals("martin.forster@web.ork"))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalDate.of(1959, 3, 7), forster.birthDate());
    }

    @Test
    void everyPersonHasCityAndPostalCode() {
        assertTrue(users.stream().noneMatch(u -> u.city() == null || u.city().isBlank()));
        assertTrue(users.stream().noneMatch(u -> u.postalCode() == null || u.postalCode().isBlank()));
    }

    @Test
    void everyPersonHasFirstAndLastName() {
        assertTrue(users.stream().noneMatch(u -> u.firstName() == null || u.firstName().isBlank()));
        assertTrue(users.stream().noneMatch(u -> u.lastName() == null || u.lastName().isBlank()));
    }
}

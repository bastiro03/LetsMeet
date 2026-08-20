package com.encoway.importer.excel;

import com.encoway.importer.dto.HobbyRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testet das Auslesen der Hobby-Zellen („Hobby %Priorität%").
 *
 * <p>Die Priorität steht zwischen zwei Prozentzeichen, mehrere Hobbys sind mit
 * Semikolon getrennt. Zeilen ohne E-Mail werden übersprungen, fehlerhafte
 * Zellen (z. B. der Kopfzeilen-Platzhalter) ebenfalls.</p>
 */
class HobbyReaderTest {

    private static final String SOURCE_FILE = "Lets Meet DB Dump.xlsx";

    private static List<HobbyRecord> hobbies;

    @BeforeAll
    static void readSource() throws Exception {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        File excel = null;
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SOURCE_FILE);
            if (Files.isRegularFile(candidate)) {
                excel = candidate.toFile();
                break;
            }
        }
        if (excel == null) {
            throw new IllegalStateException(SOURCE_FILE + " nicht gefunden.");
        }
        hobbies = new ExcelReader().readHobbies(excel);
    }

    @Test
    void readsEveryParsableHobbyCell() {
        assertEquals(4815, hobbies.size());
    }

    @Test
    void everyHobbyHasAnEmailAndName() {
        assertTrue(hobbies.stream().noneMatch(h -> h.email() == null || h.email().isBlank()));
        assertTrue(hobbies.stream().noneMatch(h -> h.hobbyName() == null || h.hobbyName().isBlank()));
    }

    @Test
    void everyHobbyIsMarkedAsSourceExcel() {
        assertTrue(hobbies.stream().allMatch(h -> ExcelReader.SOURCE_EXCEL.equals(h.source())));
    }

    @Test
    void priorityIsAlwaysWithinTheAgreedRange() {
        assertTrue(hobbies.stream().allMatch(h -> h.priority() >= -100 && h.priority() <= 100));
    }

    @Test
    void aPersonHasAtMostFiveHobbies() {
        Map<String, Long> proPerson = hobbies.stream()
                .collect(Collectors.groupingBy(HobbyRecord::email, Collectors.counting()));
        assertTrue(proPerson.values().stream().allMatch(n -> n <= 5),
                "mehr als 5 Hobbys je Person vorhanden");
    }
}
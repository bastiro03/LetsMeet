package com.encoway.importer.excel;

import com.encoway.importer.dto.HobbyRecord;
import com.encoway.importer.dto.UserRecord;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liest die Excel-Quelle und rekonstruiert je Person einen {@link UserRecord}.
 *
 * <p>Die Quelle ist als Tabelle aufgebaut, deren Spalten A bis H je Zeile folgende
 * fachliche Bedeutung haben:</p>
 *
 * <pre>
 * A = Name („Nachname, Vorname")   E = Geburtsdatum (dd.MM.yyyy)
 * B = Telefon                      F = Interesse (m/w/mw)
 * C = Hobby („Name %Priorität%")   G = Geschlecht (m/w/nb)
 * D = E-Mail                       H = Straße, PLZ, Ort
 * </pre>
 *
 * <p>Die Werte sind in der Datei jedoch verschoben: Eine E-Mail steht nicht in jeder
 * Zeile in Spalte D, ein Geburtsdatum nicht in jeder Zeile in Spalte E. Deshalb werden
 * die Felder <em>wertbasiert</em> erkannt (z. B. „enthält @", „passt auf dd.MM.yyyy"),
 * nicht über eine feste Spaltennummer. Namen und Adressen lassen sich nicht wertbasiert
 * einer E-Mail zuordnen; Namen werden über den E-Mail-Lokalteil abgeglichen
 * (martin.forster@… &rarr; „Forster, Martin"), Adressen über eine Positionsregel mit
 * Rückgriff auf die Vorzeile. Die Regeln sind in der Befundnotiz dokumentiert und
 * gegen den Kundinnen-Prüfstand zu verifizieren.</p>
 */
public class ExcelReader {

    public static final String SOURCE_EXCEL = "excel";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");
    private static final Set<String> GENDER_CODES = Set.of("m", "w", "nb", "mw");

    private static final int COL_NAME_A = 0;
    private static final int COL_PHONE_B = 1;
    private static final int COL_HOBBY_C = 2;
    private static final int COL_EMAIL_D = 3;
    private static final int COL_BIRTH_E = 4;
    private static final int COL_INTEREST_F = 5;
    private static final int COL_GENDER_G = 6;
    private static final int COL_ADDRESS_H = 7;

    public List<UserRecord> readUsers(File excelFile) throws Exception {
        List<UserRecord> users = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fileInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            List<Row> rows = new ArrayList<>();
            for (Row row : sheet) {
                rows.add(row);
            }

            List<RawRow> rawRows = readRawRows(rows);
            NameIndex nameIndex = new NameIndex(rawRows);

            for (int i = 1; i < rawRows.size(); i++) {
                RawRow current = rawRows.get(i);
                RawRow previous = rawRows.get(i - 1);

                String email = detectEmail(current);
                if (email == null) {
                    continue;
                }

                String birthDate = detectBirthDate(current);
                String phone = detectPhone(current);
                String gender = detectGender(current);
                String interest = detectInterest(current);
                String hobbyCell = detectHobbyCell(current);

                String fullName = nameIndex.matchByEmail(email);
                String firstName = null;
                String lastName = null;
                if (fullName != null) {
                    String[] parts = fullName.split(", ", 2);
                    lastName = parts.length > 0 ? parts[0].trim() : null;
                    firstName = parts.length > 1 ? parts[1].trim() : null;
                }

                String address = detectAddress(current, previous);
                String postalCode = null;
                String city = null;
                if (address != null) {
                    String[] parts = address.split(", ", 3);
                    if (parts.length >= 3) {
                        postalCode = parts[1];
                        city = parts[2];
                    }
                }

                LocalDate parsedBirthDate = null;
                if (birthDate != null) {
                    parsedBirthDate = LocalDate.parse(birthDate, DATE_FORMAT);
                }

                users.add(new UserRecord(
                        email,
                        firstName,
                        lastName,
                        parsedBirthDate,
                        postalCode,
                        city,
                        phone,
                        gender,
                        interest
                ));
            }
        }

        return users;
    }

    public List<HobbyRecord> readHobbies(File excelFile) throws Exception {
        List<HobbyRecord> hobbies = new ArrayList<>();
        try (FileInputStream fileInputStream = new FileInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(fileInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            List<Row> rows = new ArrayList<>();
            for (Row row : sheet) {
                rows.add(row);
            }

            List<RawRow> rawRows = readRawRows(rows);
            for (RawRow raw : rawRows) {
                String email = detectEmail(raw);
                if (email == null) {
                    continue;
                }
                String hobbyCell = detectHobbyCell(raw);
                if (hobbyCell == null) {
                    continue;
                }
                for (HobbyRecord hobby : parseHobbies(email, hobbyCell)) {
                    hobbies.add(hobby);
                }
            }
        }
        return hobbies;
    }

    private List<HobbyRecord> parseHobbies(String email, String hobbyCell) {
        List<HobbyRecord> result = new ArrayList<>();
        String[] parts = hobbyCell.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int closePercent = trimmed.lastIndexOf('%');
            int openPercent = trimmed.lastIndexOf('%', closePercent - 1);
            if (openPercent < 0) {
                continue;
            }
            String name = trimmed.substring(0, openPercent).trim();
            String prioText = trimmed.substring(openPercent + 1, closePercent).trim();
            if (name.isEmpty()) {
                continue;
            }
            Integer priority = null;
            try {
                priority = Integer.parseInt(prioText);
            } catch (NumberFormatException ignored) {
                // Priorität nicht lesbar -> Zeile wird nicht importiert (fehlt sichtbar)
            }
            if (priority == null) {
                continue;
            }
            result.add(new HobbyRecord(email, name, priority, SOURCE_EXCEL));
        }
        return result;
    }

    private List<RawRow> readRawRows(List<Row> rows) {
        List<RawRow> raw = new ArrayList<>();
        for (Row row : rows) {
            String a = cellText(row, COL_NAME_A);
            String b = cellText(row, COL_PHONE_B);
            String c = cellText(row, COL_HOBBY_C);
            String d = cellText(row, COL_EMAIL_D);
            String e = cellText(row, COL_BIRTH_E);
            String f = cellText(row, COL_INTEREST_F);
            String g = cellText(row, COL_GENDER_G);
            String h = cellText(row, COL_ADDRESS_H);
            raw.add(new RawRow(a, b, c, d, e, f, g, h));
        }
        return raw;
    }

    private String cellText(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                long l = Math.round(v);
                yield v == l ? String.valueOf(l) : String.valueOf(v);
            }
            default -> null;
        };
    }

    private String detectEmail(RawRow r) {
        for (String candidate : new String[]{
                r.d, r.c, r.e, r.g, r.f}) {
            if (candidate != null && candidate.contains("@")) {
                return candidate;
            }
        }
        return null;
    }

    private String detectBirthDate(RawRow r) {
        for (String candidate : new String[]{r.e, r.d, r.f, r.g}) {
            if (candidate != null && DATE_PATTERN.matcher(candidate.trim()).matches()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String detectPhone(RawRow r) {
        String p = r.b;
        if (p == null || p.contains(",") || p.contains("%") || p.contains("@")) {
            return null;
        }
        if (DATE_PATTERN.matcher(p).matches() || GENDER_CODES.contains(p.trim())) {
            return null;
        }
        if (p.chars().anyMatch(Character::isDigit)) {
            return p;
        }
        return null;
    }

    private String detectGender(RawRow r) {
        if (r.g != null && GENDER_CODES.contains(r.g.trim())) {
            return r.g.trim();
        }
        if (r.f != null && GENDER_CODES.contains(r.f.trim())) {
            return r.f.trim();
        }
        return null;
    }

    private String detectInterest(RawRow r) {
        if (r.f != null && GENDER_CODES.contains(r.f.trim())) {
            return r.f.trim();
        }
        if (r.g != null && GENDER_CODES.contains(r.g.trim())) {
            return r.g.trim();
        }
        return null;
    }

    private String detectHobbyCell(RawRow r) {
        if (r.c != null && r.c.contains("%")) {
            return r.c;
        }
        if (r.d != null && r.d.contains("%")) {
            return r.d;
        }
        return null;
    }

    private String detectAddress(RawRow r, RawRow previous) {
        if (isAddress(r.h)) {
            return r.h;
        }
        if (isAddress(r.a)) {
            return r.a;
        }
        if (previous != null) {
            if (isAddress(previous.h)) {
                return previous.h;
            }
            if (isAddress(previous.a)) {
                return previous.a;
            }
        }
        return null;
    }

    private boolean isAddress(String value) {
        return value != null
                && value.contains(",")
                && value.chars().anyMatch(Character::isDigit);
    }

    private record RawRow(String a, String b, String c, String d, String e, String f, String g, String h) {}

    /**
     * Index, der zu einer E-Mail den passenden Namen („Nachname, Vorname") findet.
     * Abgleich über den Lokalteil der E-Mail: Die Namensbestandteile müssen darin
     * als Tokens vorkommen (martin.forster@web.ork → „Forster, Martin").
     */
    private static final class NameIndex {
        private static final Pattern TOKEN_SPLIT = Pattern.compile("[._-]");
        private final List<NameEntry> entries = new ArrayList<>();

        NameIndex(List<RawRow> rawRows) {
            Set<String> seen = new HashSet<>();
            for (RawRow r : rawRows) {
                addCandidate(r.a, seen);
                addCandidate(r.h, seen);
            }
        }

        private void addCandidate(String value, Set<String> seen) {
            if (value == null || !value.contains(",")
                    || value.chars().anyMatch(Character::isDigit)) {
                return;
            }
            String[] parts = value.split(",", 2);
            if (parts.length != 2) {
                return;
            }
            String last = normalize(parts[0]);
            String first = normalize(parts[1]);
            if (last.isEmpty() || first.isEmpty()) {
                return;
            }
            String key = last + "|" + first;
            if (seen.add(key)) {
                entries.add(new NameEntry(parts[0], parts[1], last, first));
            }
        }

        String matchByEmail(String email) {
            if (email == null) {
                return null;
            }
            String localPart = email.split("@", 2)[0];
            Set<String> tokens = new HashSet<>();
            for (String token : TOKEN_SPLIT.split(localPart.toLowerCase(Locale.ROOT))) {
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
            for (NameEntry entry : entries) {
                if (tokens.contains(entry.last) && tokens.contains(entry.first)) {
                    return entry.rawLast + ", " + entry.rawFirst;
                }
            }
            return null;
        }

        private String normalize(String value) {
            String folded = Normalizer.normalize(value, Normalizer.Form.NFC);
            return folded.toLowerCase(Locale.ROOT).trim();
        }

        private record NameEntry(String rawLast, String rawFirst, String last, String first) {}
    }

    private LocalDate parseDate(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getLocalDateTimeCellValue().toLocalDate();
            case STRING -> LocalDate.parse(
                    cell.getStringCellValue().trim(),
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
            );
            default -> throw new IllegalStateException("Unexpected cell type: " + cell.getCellType());
        };
    }

}

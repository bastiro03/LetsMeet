package com.encoway.importer;

import com.encoway.importer.data.DataImporter;
import com.encoway.importer.db.SchemaCreator;
import com.encoway.importer.excel.ExcelReader;
import com.encoway.importer.dto.UserRecord;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    private static final String SOURCE_FILE = "Lets Meet DB Dump.xlsx";

    public static void main(String[] args) throws Exception {

        new SchemaCreator().createSchema();

        ExcelReader reader = new ExcelReader();
        File excelFile = resolveExcelFile();
        List<UserRecord> users = reader.readUsers(excelFile);

        new DataImporter().importUsers(users);

        System.out.println("Import completed: " + users.size() + " Zeilen gelesen.");
    }

    private static File resolveExcelFile() {
        String override = System.getProperty("letsmeet.excel");
        if (override != null) {
            return new File(override);
        }

        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SOURCE_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
        }
        throw new IllegalStateException(
                SOURCE_FILE + " nicht gefunden. Aufruf aus dem Projektstamm, oder "
                + "Pfad über -Dletsmeet.excel=... setzen.");
    }
}

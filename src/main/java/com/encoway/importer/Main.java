package com.encoway.importer;

import com.encoway.importer.data.DataImporter;
import com.encoway.importer.db.SchemaCreator;
import com.encoway.importer.dto.HobbyRecord;
import com.encoway.importer.dto.UserRecord;
import com.encoway.importer.excel.ExcelReader;

import java.io.File;
import java.util.List;

/**
 * Akt 2 – Import der Excel-Quelle.
 *
 * <p>Reihenfolge für den Neuaufbau: leeren &rarr; DDL + Excel-Import (dieses Programm)
 * &rarr; MongoDB-Import (notebooks/03-import-mongodb.ipynb) &rarr; Prüfung mit dem
 * Kundinnen-Checker (V2).</p>
 */
public final class Main {

    public static void main(String[] args) throws Exception {

        new SchemaCreator().createSchema();

        ExcelReader reader = new ExcelReader();
        File excelFile = findExcelFile();

        List<UserRecord> users = reader.readUsers(excelFile);
        List<HobbyRecord> hobbies = reader.readHobbies(excelFile);

        DataImporter importer = new DataImporter();
        importer.importUsers(users);
        importer.importInterests(users);
        importer.importHobbies(hobbies);

        System.out.println("Excel-Import completed: "
                + users.size() + " users, " + hobbies.size() + " hobbies.");
    }

    private static File findExcelFile() {
        for (String candidate : new String[]{
                "Lets Meet DB Dump.xlsx",
                "../Lets Meet DB Dump.xlsx"}) {
            File file = new File(candidate);
            if (file.isFile()) {
                return file;
            }
        }
        return new File("Lets Meet DB Dump.xlsx");
    }
}
package com.encoway.importer;

import com.encoway.importer.data.DataImporter;
import com.encoway.importer.db.SchemaCreator;
import com.encoway.importer.excel.ExcelReader;
import com.encoway.importer.model.UserRecord;


import java.io.File;
import java.util.List;


public final class Main {

    public static void main(String[] args) throws Exception {

        new SchemaCreator().createSchema();

        ExcelReader reader = new ExcelReader();
        File excelFile = new File("../Lets Meet DB Dump.xlsx");
        List<UserRecord> users = reader.readUsers(excelFile);

        new DataImporter().importUsers(users);

        System.out.println("Import completed.");
    }
}

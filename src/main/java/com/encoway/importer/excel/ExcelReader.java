package com.encoway.importer.excel;

import com.encoway.importer.model.UserRecord;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExcelReader {
    public List<UserRecord> readUsers(File excelFile) throws Exception {
        List<UserRecord> users = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(excelFile);
        Workbook workbook = WorkbookFactory.create(fileInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String fullName = getString(row.getCell(0));
                String address = getString(row.getCell(1));
                String email = getString(row.getCell(4));

                String rawBirthDate = row.getCell(7).getStringCellValue().trim();
                LocalDate birthDate = LocalDate.parse(rawBirthDate,
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));

                String firstName = extractFirstName(fullName);
                String lastName = extractLastName(fullName);
                String postalCode = extractPostalCode(address);
                String city = extractCity(address);

                users.add(new UserRecord(
                        email,
                        firstName,
                        lastName,
                        birthDate,
                        postalCode,
                        city
                ));
            }
        }

        return users;
    }

    private String getString(Cell cell) {
        return cell == null ? null : cell.getStringCellValue();
    }

    private String extractFirstName(String fullName) {
        if (fullName == null) return null;
        String[] parts = fullName.split(", ");
        return parts.length > 1 ? parts[1] : null;
    }

    private String extractLastName(String fullName) {
        if (fullName == null) return null;
        String[] parts = fullName.split(", ");
        return parts.length > 0 ? parts[0] : null;
    }

    private String extractPostalCode(String address) {
        if (address == null) return null;
        String[] parts = address.split(", ", 3);

        return parts.length > 1 ? parts[1] : null;
    }

    private String extractCity(String address) {
        if (address == null) return null;
        String[] parts = address.split(", ", 3);
        return parts.length > 2 ? parts[2] : null;
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

package com.encoway.importer.dto;

import java.time.LocalDate;

//DTO
public record UserRecord(
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String postalCode,
        String city
) {}

package com.encoway.importer.dto;

import java.time.LocalDate;

public record UserRecord(
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String postalCode,
        String city,
        String phone,
        String gender,
        String interest
) {}
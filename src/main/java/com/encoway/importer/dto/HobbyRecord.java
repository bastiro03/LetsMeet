package com.encoway.importer.dto;

public record HobbyRecord(
        String email,
        String hobbyName,
        Integer priority,
        String source
) {}
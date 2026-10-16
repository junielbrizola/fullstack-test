package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

public record PageCursor(LocalDate createdOn, UUID id) {
    private static final int MAX_ENCODED_LENGTH = 128;

    public String encode() {
        String raw = createdOn + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static PageCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_ENCODED_LENGTH) {
            throw new InvalidRequest("O cursor informado é inválido.");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0 || separator == decoded.length() - 1 || decoded.indexOf(':', separator + 1) >= 0) {
                throw new IllegalArgumentException("Cursor malformado");
            }
            String dateText = decoded.substring(0, separator);
            LocalDate createdOn = LocalDate.parse(dateText);
            if (!createdOn.toString().equals(dateText)
                    || createdOn.isBefore(Investment.MIN_SUPPORTED_DATE)
                    || createdOn.isAfter(Investment.MAX_SUPPORTED_DATE)) {
                throw new IllegalArgumentException("Cursor date is outside the finite PostgreSQL DATE range");
            }
            UUID id = UUID.fromString(decoded.substring(separator + 1));
            return new PageCursor(createdOn, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new InvalidRequest("O cursor informado é inválido.");
        }
    }
}

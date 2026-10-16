package com.coderockr.investments.adapter.in.rest;

import com.coderockr.investments.application.InvalidRequest;
import com.coderockr.investments.domain.Investment;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

final class CivilDateParser {
    static final String OPENAPI_FORMAT = "iso-local-date";
    static final String OPENAPI_PATTERN = "^(?:[0-9]{4}|-(?:000[1-9]|00[1-9][0-9]|0[1-9][0-9]{2}|[1-3][0-9]{3}|"
            + "4[0-6][0-9]{2}|470[0-9]|471[0-3])|\\+(?:[1-9][0-9]{4,5}|[1-4][0-9]{6}|5[0-7][0-9]{5}|"
            + "58[0-6][0-9]{4}|587[0-3][0-9]{3}|5874[0-7][0-9]{2}|58748(?:[0-8][0-9]|9[0-7])))"
            + "-(?:0[1-9]|1[0-2])-(?:0[1-9]|1[0-9]|2[0-9]|3[01])(?![\\s\\S])";
    static final String OPENAPI_DESCRIPTION = "Data ISO local canônica no intervalo finito DATE do PostgreSQL entre -4713-11-24 e +5874897-12-31; ano 0000 representa 1 a.C. A aplicação valida o calendário e os limites exatos.";

    private CivilDateParser() {}

    static LocalDate parse(String value) {
        if (value == null) {
            throw new InvalidRequest("A data deve estar no formato ISO civil canônico.");
        }

        final LocalDate parsed;
        try {
            parsed = LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidRequest("A data deve estar no formato ISO civil canônico.");
        }
        if (!parsed.toString().equals(value)
                || parsed.isBefore(Investment.MIN_SUPPORTED_DATE)
                || parsed.isAfter(Investment.MAX_SUPPORTED_DATE)) {
            throw new InvalidRequest("A data está fora do intervalo finito ou do formato canônico de PostgreSQL DATE.");
        }
        return parsed;
    }
}

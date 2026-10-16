package com.coderockr.investments.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CreateInvestmentRequest(
        @Schema(minLength = 1, maxLength = 100, pattern = ApiSchemaPatterns.NON_BLANK_WITHOUT_NUL)
        @NotBlank String ownerName,
        @Schema(
                type = SchemaType.STRING,
                format = CivilDateParser.OPENAPI_FORMAT,
                pattern = CivilDateParser.OPENAPI_PATTERN,
                minLength = 10,
                maxLength = 14,
                description = CivilDateParser.OPENAPI_DESCRIPTION)
        @NotBlank String createdOn,
        @NotBlank @Pattern(regexp = "^\\d{1,17}(\\.\\d{1,2})?$") String initialAmount) {
}

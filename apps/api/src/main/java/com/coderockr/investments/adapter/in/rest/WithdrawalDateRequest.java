package com.coderockr.investments.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record WithdrawalDateRequest(
        @Schema(
                type = SchemaType.STRING,
                format = CivilDateParser.OPENAPI_FORMAT,
                pattern = CivilDateParser.OPENAPI_PATTERN,
                minLength = 10,
                maxLength = 14,
                description = CivilDateParser.OPENAPI_DESCRIPTION)
        @NotBlank String withdrawnOn) {
}

package com.coderockr.investments.adapter.in.rest;

import com.coderockr.investments.application.InvestmentView;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record InvestmentResponse(
        UUID id,
        String ownerName,
        @Schema(
                type = SchemaType.STRING,
                format = CivilDateParser.OPENAPI_FORMAT,
                pattern = CivilDateParser.OPENAPI_PATTERN,
                minLength = 10,
                maxLength = 14,
                description = CivilDateParser.OPENAPI_DESCRIPTION)
        LocalDate createdOn,
        String initialAmount,
        @Schema(
                type = SchemaType.STRING,
                format = CivilDateParser.OPENAPI_FORMAT,
                pattern = CivilDateParser.OPENAPI_PATTERN,
                minLength = 10,
                maxLength = 14,
                description = CivilDateParser.OPENAPI_DESCRIPTION)
        LocalDate asOfDate,
        long completedPeriods,
        String grossBalance,
        String gain,
        String status,
        WithdrawalResultResponse withdrawal) {

    public static InvestmentResponse from(InvestmentView view) {
        return new InvestmentResponse(
                view.id(),
                view.ownerName(),
                view.createdOn(),
                view.initialAmount().toString(),
                view.asOfDate(),
                view.completedPeriods(),
                view.grossBalance().toString(),
                view.gain().toString(),
                view.status(),
                view.withdrawal() == null ? null : WithdrawalResultResponse.from(view.withdrawal()));
    }
}

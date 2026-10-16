package com.coderockr.investments.adapter.in.rest;

import com.coderockr.investments.domain.WithdrawalQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record WithdrawalResultResponse(
        UUID investmentId,
        @Schema(
                type = SchemaType.STRING,
                format = CivilDateParser.OPENAPI_FORMAT,
                pattern = CivilDateParser.OPENAPI_PATTERN,
                minLength = 10,
                maxLength = 14,
                description = CivilDateParser.OPENAPI_DESCRIPTION)
        LocalDate withdrawnOn,
        String initialAmount,
        long completedPeriods,
        String grossAmount,
        String gainAmount,
        String taxRate,
        String taxAmount,
        String netAmount,
        String calculationVersion) {

    public static WithdrawalResultResponse from(WithdrawalQuote quote) {
        BigDecimal rate = quote.taxRate().stripTrailingZeros();
        return new WithdrawalResultResponse(
                quote.investmentId(),
                quote.withdrawnOn(),
                quote.initialAmount().toString(),
                quote.completedPeriods(),
                quote.grossAmount().toString(),
                quote.gainAmount().toString(),
                rate.toPlainString(),
                quote.taxAmount().toString(),
                quote.netAmount().toString(),
                quote.calculationVersion());
    }
}

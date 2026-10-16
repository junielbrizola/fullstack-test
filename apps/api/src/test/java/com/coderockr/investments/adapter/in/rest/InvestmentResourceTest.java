package com.coderockr.investments.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coderockr.investments.application.InvalidRequest;
import com.coderockr.investments.domain.Investment;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvestmentResourceTest {
    private final InvestmentResource resource = new InvestmentResource();

    @Test
    void rejectsMissingOrNullBodiesBeforeAccessingInjectedServices() {
        assertThrows(InvalidRequest.class, () -> resource.create(null, null, null));
        assertThrows(InvalidRequest.class, () -> resource.preview("68b4b7a8-cd36-4f4e-bbf0-c86bcdf9d703", null));
        assertThrows(InvalidRequest.class,
                () -> resource.withdraw("68b4b7a8-cd36-4f4e-bbf0-c86bcdf9d703", null, null));
    }

    @Test
    void canonicalCivilDateParserAcceptsLiteralFinitePostgresBounds() {
        LocalDate minimum = LocalDate.parse("-4713-11-24");
        LocalDate maximum = LocalDate.parse("+5874897-12-31");

        assertEquals(minimum, Investment.MIN_SUPPORTED_DATE);
        assertEquals(maximum, Investment.MAX_SUPPORTED_DATE);
        assertEquals(minimum, CivilDateParser.parse("-4713-11-24"));
        assertEquals(maximum, CivilDateParser.parse("+5874897-12-31"));
    }

    @Test
    void rejectsNonCanonicalExpandedDateSpellingsAtRestBoundary() {
        CreateInvestmentRequest createRequest = new CreateInvestmentRequest("Ana", "+00001-01-01", "1.00");
        WithdrawalDateRequest withdrawalRequest = new WithdrawalDateRequest("+00001-01-01");

        assertThrows(InvalidRequest.class, () -> resource.create("create-0001", createRequest, null));
        assertThrows(InvalidRequest.class,
                () -> resource.preview("68b4b7a8-cd36-4f4e-bbf0-c86bcdf9d703", withdrawalRequest));
    }
}

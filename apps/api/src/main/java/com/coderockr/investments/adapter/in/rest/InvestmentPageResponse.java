package com.coderockr.investments.adapter.in.rest;

import com.coderockr.investments.application.InvestmentPage;
import java.util.List;

public record InvestmentPageResponse(List<InvestmentResponse> items, String nextCursor, int limit) {
    public static InvestmentPageResponse from(InvestmentPage page) {
        return new InvestmentPageResponse(
                page.items().stream().map(InvestmentResponse::from).toList(),
                page.nextCursor(),
                page.limit());
    }
}

package com.coderockr.investments.application;

import java.util.List;

public record InvestmentPage(List<InvestmentView> items, String nextCursor, int limit) {
}

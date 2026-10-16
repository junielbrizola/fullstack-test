package com.coderockr.investments.domain;

public final class InvestmentRuleViolation extends RuntimeException {
    public InvestmentRuleViolation(String message) {
        super(message);
    }
}

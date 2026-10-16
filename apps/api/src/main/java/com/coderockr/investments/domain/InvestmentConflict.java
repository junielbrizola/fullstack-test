package com.coderockr.investments.domain;

public final class InvestmentConflict extends RuntimeException {
    public InvestmentConflict(String message) {
        super(message);
    }
}

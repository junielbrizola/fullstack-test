package com.coderockr.investments.application;

public final class InvestmentNotFound extends RuntimeException {
    public InvestmentNotFound() {
        super("O investimento solicitado não foi encontrado.");
    }
}

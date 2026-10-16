package com.coderockr.investments.bootstrap;

import com.coderockr.investments.domain.InvestmentCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class DomainBeans {
    @Produces
    @Singleton
    public InvestmentCalculator investmentCalculator() {
        return new InvestmentCalculator();
    }
}

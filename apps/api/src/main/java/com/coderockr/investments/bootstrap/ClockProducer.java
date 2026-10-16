package com.coderockr.investments.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Clock;
import java.time.ZoneId;

@ApplicationScoped
public class ClockProducer {
    @ConfigProperty(name = "investment.business-zone", defaultValue = "America/Sao_Paulo")
    String businessZone;

    @Produces
    @Singleton
    public Clock businessClock() {
        return Clock.system(ZoneId.of(businessZone));
    }
}

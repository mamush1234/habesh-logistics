package com.habeshlist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneOffset;

@Configuration
public class ClockConfig {
    @Bean
    public Clock applicationClock() {
        return Clock.tickMillis(ZoneOffset.UTC);
    }
}

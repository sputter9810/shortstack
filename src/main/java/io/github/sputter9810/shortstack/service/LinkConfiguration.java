package io.github.sputter9810.shortstack.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LinkConfiguration {
    @Bean
    Clock linkClock() {
        return Clock.systemUTC();
    }
}

package com.reservacanchas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    public static final String ZONA_HORARIA_ID = "America/Lima";
    public static final ZoneId ZONA_HORARIA = ZoneId.of(ZONA_HORARIA_ID);

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_HORARIA);
    }
}

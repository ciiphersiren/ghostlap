package com.ghostlap.race;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RaceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaceServiceApplication.class, args);
    }
}

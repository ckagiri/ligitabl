package com.ligitabl.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.seed.internal.SeedLoader;
import com.ligitabl.seed.internal.SeedOrchestrator;
import com.ligitabl.seed.internal.SeedOrchestrator.SeedExecutionReport;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SeedingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeedingApplication.class, args);
    }

    @Bean
    CommandLineRunner seedingRunner(DSLContext dsl, ObjectMapper objectMapper) {
        return args -> {
            String mainResource = requireSeedMainResource();
            logSeedingStart(mainResource);

            Map<String, Object> sections = loadSeedConfiguration(mainResource);

            SeedOrchestrator orchestrator = new SeedOrchestrator(dsl, objectMapper);
            SeedExecutionReport report = orchestrator.executeSeed(sections, mainResource);

            report.printToConsole();
            logSeedingComplete(mainResource);
        };
    }

    private String requireSeedMainResource() {
        String systemProperty = System.getProperty("seed.main");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }

        throw new IllegalArgumentException(
                "Missing required system property 'seed.main'. "
                        + "Example: java -Dseed.main=seeding/demo-main.yaml -jar seed/target/ligitabl-seed-0.1.0-SNAPSHOT.jar --spring.profiles.active=default");
    }

    private Map<String, Object> loadSeedConfiguration(String mainResource) {
        try {
            SeedLoader loader = new SeedLoader();
            return loader.loadFromClasspath(mainResource);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load seed configuration from: " + mainResource, e);
        }
    }

    private void logSeedingStart(String mainResource) {
        System.out.println("================================================================================");
        System.out.println("[seed] Starting database seeding");
        System.out.println("[seed] Main resource: " + mainResource);
        System.out.println("================================================================================");
    }

    private void logSeedingComplete(String mainResource) {
        System.out.println("================================================================================");
        System.out.println("[seed] Database seeding completed successfully");
        System.out.println("[seed] Resource: " + mainResource);
        System.out.println("================================================================================");
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}


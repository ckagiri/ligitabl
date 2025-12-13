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

    private static final String DEFAULT_MAIN_RESOURCE = "seeding/demo-main.yaml";
    private static final String PRODUCTION_MAIN_RESOURCE = "seeding/main.yaml";

    public static void main(String[] args) {
        SpringApplication.run(SeedingApplication.class, args);
    }

    @Bean
    CommandLineRunner seedingRunner(DSLContext dsl, ObjectMapper objectMapper) {
        return args -> {
            String mainResource = determineMainResource();
            logSeedingStart(mainResource);

            Map<String, Object> sections = loadSeedConfiguration(mainResource);

            SeedOrchestrator orchestrator = new SeedOrchestrator(dsl, objectMapper);
            SeedExecutionReport report = orchestrator.executeSeed(sections, mainResource);

            report.printToConsole();
            logSeedingComplete(mainResource);
        };
    }

    private String determineMainResource() {
        String systemProperty = System.getProperty("seed.main");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }

        String appEnv = System.getenv("APP_ENV");
        if (appEnv != null && !appEnv.isBlank()) {
            return getResourceForEnvironment(appEnv);
        }

        System.out.println("[seed] No seed.main or APP_ENV specified, defaulting to demo data");
        return DEFAULT_MAIN_RESOURCE;
    }

    private String getResourceForEnvironment(String environment) {
        return switch (environment.toLowerCase()) {
            case "production", "prod" -> {
                System.out.println("[seed] Environment: PRODUCTION - using main.yaml");
                yield PRODUCTION_MAIN_RESOURCE;
            }
            case "staging", "stage" -> {
                System.out.println("[seed] Environment: STAGING - using main.yaml");
                yield PRODUCTION_MAIN_RESOURCE;
            }
            case "demo", "test", "dev", "development" -> {
                System.out.println("[seed] Environment: " + environment.toUpperCase() + " - using demo-main.yaml");
                yield DEFAULT_MAIN_RESOURCE;
            }
            default -> {
                System.out.println("[seed] Unknown environment '" + environment + "', defaulting to demo");
                yield DEFAULT_MAIN_RESOURCE;
            }
        };
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
        System.out.println("[seed] Data type: " + getDataTypeDescription(mainResource));
        System.out.println("================================================================================");
    }

    private void logSeedingComplete(String mainResource) {
        System.out.println("================================================================================");
        System.out.println("[seed] Database seeding completed successfully");
        System.out.println("[seed] Resource: " + mainResource);
        System.out.println("================================================================================");
    }

    private String getDataTypeDescription(String mainResource) {
        if (mainResource.contains("demo")) {
            return "Demo/Test Data (fictional, safe for testing)";
        } else if (mainResource.contains("main")) {
            return "Production/Reference Data (real data)";
        }
        return "Custom Data";
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}


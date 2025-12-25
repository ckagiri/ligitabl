package com.ligitabl.api.usecases.prediction.seeding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;

// application/seeding/SeedingConfigLoader.java
@Component
@Slf4j
public class SeedingConfigLoader {

    public SeedingConfig loadConfig() {
        return loadConfig("seeding-config.yaml");
    }

    public SeedingConfig loadConfig(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            if (!resource.exists()) {
                throw new IllegalStateException("Seeding config not found: " + path);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                Yaml yaml = new Yaml(new Constructor(SeedingConfig.class));
                SeedingConfig config = yaml.load(inputStream);

                log.info("Loaded seeding config: competition={}, season={}, finishedRounds={}, users={}",
                        config.getCompetitionSlug(),
                        config.getSeasonSlug(),
                        config.getFinishedRounds(),
                        config.getDemoUsers().size()
                );

                return config;
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load seeding config", e);
        }
    }
}

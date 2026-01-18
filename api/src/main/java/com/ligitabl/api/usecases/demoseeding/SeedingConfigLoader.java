package com.ligitabl.api.usecases.demoseeding;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SeedingConfigLoader {

    private final String defaultConfigPath;

    public SeedingConfigLoader(@Value("${demoseeding.config:demoseeding-config.yaml}") String defaultConfigPath) {
        this.defaultConfigPath = defaultConfigPath;
    }

    public SeedingConfig loadConfig() {
        return loadConfig(defaultConfigPath);
    }

    public SeedingConfig loadConfig(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            if (!resource.exists()) {
                throw new IllegalStateException("Seeding config not found: " + path);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                mapper.findAndRegisterModules();
                SeedingConfig config = mapper.readValue(inputStream, SeedingConfig.class);

                log.info(
                        "Loaded demoseeding config: competition={}, season={}, finishedRounds={}, users={}",
                        config.getCompetitionSlug(),
                        config.getSeasonSlug(),
                        config.getFinishedRounds(),
                        config.getDemoUsers().size());

                return config;
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load demoseeding config", e);
        }
    }
}

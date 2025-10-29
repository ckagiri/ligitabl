package com.ligitabl.api.seed;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "teams-file")
@Slf4j
public class TeamsSeedRunner implements CommandLineRunner {
    private final TeamsSeeder seeder;
    private final ResourceLoader resourceLoader;
    private final ConfigurableApplicationContext context;

    @Value("${app.seed.teams-file}")
    private String teamsFileLocation;

    @Value("${app.seed.exit-on-completion:true}")
    private boolean exitOnCompletion;

    @Override
    public void run(String... args) throws Exception {
        log.info("Seeding teams from {}", teamsFileLocation);

        Resource resource = resourceLoader.getResource(teamsFileLocation);
        if (!resource.exists()) {
            throw new IllegalArgumentException(
                    "Seed file not found at location: " + teamsFileLocation + " (use classpath: or file:) ");
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        List<TeamSeedEntry> entries = new ArrayList<>();
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = mapper.readTree(in);
            if (root == null || root.isNull()) {
                log.warn("Seed file is empty: {}", teamsFileLocation);
            } else if (root.isArray()) {
                entries = mapper.convertValue(root, new TypeReference<List<TeamSeedEntry>>() {});
            } else if (root.isObject()) {
                JsonNode teamsNode = root.get("teams");
                if (teamsNode != null && teamsNode.isArray()) {
                    entries = mapper.convertValue(teamsNode, new TypeReference<List<TeamSeedEntry>>() {});
                } else {
                    // Try mapping the object itself if it represents a single team
                    TeamSeedEntry single = mapper.convertValue(root, TeamSeedEntry.class);
                    if (single != null) entries.add(single);
                }
            } else {
                log.warn("Unrecognized YAML structure in {}", teamsFileLocation);
            }
        }

        seeder.seed(entries);

        log.info("Team seeding completed ({} entries)", entries.size());

        if (exitOnCompletion) {
            // Gracefully close the application context so the process can exit
            int code = 0;
            try {
                context.close();
            } finally {
                // As a last resort, ensure termination to avoid hanging in CI/Makefile
                System.exit(code);
            }
        }
    }
}

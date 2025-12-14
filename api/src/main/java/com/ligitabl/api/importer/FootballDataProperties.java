package com.ligitabl.api.importer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the external football data API.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "football-data")
public class FootballDataProperties {

    private String baseUrl = "https://api.football-data.org/v4";

    private String apiKey;
}

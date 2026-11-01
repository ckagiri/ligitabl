package com.ligitabl.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Whether the importer and calc-standings runner run on startup, and which competition they import.
 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow")
public class WorkflowConfiguration {
    private boolean run = false;
    private String competition = "PL";
    private boolean exitAfter = true;
}

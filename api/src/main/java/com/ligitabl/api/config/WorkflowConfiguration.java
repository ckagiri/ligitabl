package com.ligitabl.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Workflow runner configuration.
 *
 * Used by the importer & calc-standings workflow runner to decide whether to run on startup
 * and which competition to import.
 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow")
public class WorkflowConfiguration {
    private boolean run = false;
    private String competition = "PL";
    private boolean exitAfter = true;
}

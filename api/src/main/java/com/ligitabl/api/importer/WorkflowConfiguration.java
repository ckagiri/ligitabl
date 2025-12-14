package com.ligitabl.api.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration for the match import workflow runner.
 *
 * Controlled via `workflow.*` properties, typically in the `workflow` profile.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "workflow")
public class WorkflowConfiguration {

    /** Whether the workflow runner should execute on startup. */
    private boolean run = false;

    /** Competition code to import (e.g. PL, PD, SA). */
    private String competition = "PL";

    /** Whether to exit the JVM after the workflow completes. */
    private boolean exitAfter = true;
}

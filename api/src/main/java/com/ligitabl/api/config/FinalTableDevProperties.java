package com.ligitabl.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Whether to render the Final Table "Score now (dev)" controls. Defaults to false, so a
 * misconfigured non-prod environment stays inert.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.final-table.dev-preview")
public class FinalTableDevProperties {
    private boolean enabled = false;
}

package com.ligitabl.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Final Table dev-preview flag.
 *
 * <p>Read by the view layer to decide whether to render the "Score now (dev)" controls. The
 * endpoints themselves are additionally gated by {@code @Profile("!prod")}, so this property being
 * true in production still would not make them reachable.
 *
 * <p>Defaults to <b>false</b>: a misconfigured non-prod environment stays inert.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.final-table.dev-preview")
public class FinalTableDevProperties {
    private boolean enabled = false;
}

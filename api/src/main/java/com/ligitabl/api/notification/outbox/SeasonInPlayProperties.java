package com.ligitabl.api.notification.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Kill-switch for the pre-season → in-play auto-join.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.auto-join")
public class SeasonInPlayProperties {
    private boolean enabled = true;
}

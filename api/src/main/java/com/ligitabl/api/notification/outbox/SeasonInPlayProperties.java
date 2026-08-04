package com.ligitabl.api.notification.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Kill-switch for the pre-season → in-play auto-join.
 *
 * <p>There is no eligibility window to configure: {@code SeasonInPlayEnqueuer} anchors to the
 * season's own {@code preSeasonOpensAt}, so the cohort is defined by the season rather than by
 * a tunable number of days.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.auto-join")
public class SeasonInPlayProperties {
    private boolean enabled = true;
}

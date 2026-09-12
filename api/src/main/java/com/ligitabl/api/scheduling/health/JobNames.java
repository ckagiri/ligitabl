package com.ligitabl.api.scheduling.health;

/**
 * Names of the background jobs monitored by {@link JobHeartbeat}. Constants rather than an enum so
 * the registry key stays a tolerant {@code String} while call sites stay typo-proof.
 *
 * <p>Which jobs are monitored and which deliberately are not: {@code docs/background-jobs.md}.
 * Adding a {@code @Scheduled} method without deciding fails {@code ScheduledJobRegistryTest}.
 */
public final class JobNames {

    /** The chain that stalled for five days on 2026-09-06; see {@code .art/task_89.md}. */
    public static final String MATCH_SYNC = "matchSync";

    public static final String OUTBOX_RELAY = "outboxRelay";

    public static final String SEASON_ACTIVATION = "seasonActivation";

    public static final String SEASON_IN_PLAY = "seasonInPlay";

    public static final String MATCHDAY_ADVANCEMENT = "matchdayAdvancement";

    private JobNames() {}
}

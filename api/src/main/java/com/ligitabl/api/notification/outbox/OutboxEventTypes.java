package com.ligitabl.api.notification.outbox;

public final class OutboxEventTypes {

    private OutboxEventTypes() {}

    public static final String ROUND_ADVANCED = "ROUND_ADVANCED";

    /** One user's round-results email, ready to render and send. */
    public static final String ROUND_RESULTS = "ROUND_RESULTS";

    /** One user's join-reminder email, ready to render and send. */
    public static final String JOIN_REMINDER = "JOIN_REMINDER";

    /** A round has transitioned from OPEN to LOCKED for the first time. */
    public static final String ROUND_LOCKED = "ROUND_LOCKED";

    /** The season has opened for play; auto-join everyone who was around but never submitted. */
    public static final String SEASON_IN_PLAY = "SEASON_IN_PLAY";

    /**
     * Chained from SEASON_IN_PLAY once its auto-joins have committed.
     */
    public static final String SEASON_WELCOME_FANOUT = "SEASON_WELCOME_FANOUT";

    /** One user's season-welcome email, ready to render and send. */
    public static final String SEASON_WELCOME = "SEASON_WELCOME";

    /** An admin has marked the season completed; the full-season standings are now final. */
    public static final String SEASON_COMPLETED = "SEASON_COMPLETED";

    /**
     * One user's segment-success email — the sprint and/or quarter they just finished in
     * the top 3.
     */
    public static final String SEGMENT_RESULTS = "SEGMENT_RESULTS";
}

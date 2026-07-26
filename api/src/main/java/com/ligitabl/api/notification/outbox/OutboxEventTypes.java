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
}

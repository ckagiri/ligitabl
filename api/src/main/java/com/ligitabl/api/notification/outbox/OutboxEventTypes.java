package com.ligitabl.api.notification.outbox;

public final class OutboxEventTypes {

    private OutboxEventTypes() {}

    public static final String ROUND_ADVANCED = "ROUND_ADVANCED";

    /** One user's round-results email, ready to render and send. */
    public static final String ROUND_RESULTS = "ROUND_RESULTS";
}

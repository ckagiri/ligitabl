package com.ligitabl.api.notification.outbox;

/** Event-type discriminators stored in t_outbox_event.c_event_type. */
public final class OutboxEventTypes {

    private OutboxEventTypes() {}

    /**
     * Thin fact written by round finalization; the relay expands it into
     * per-user ROUND_RESULTS events (fan-out happens post-commit).
     */
    public static final String ROUND_FINALIZED = "ROUND_FINALIZED";

    /** One user's round-results email, ready to render and send. */
    public static final String ROUND_RESULTS = "ROUND_RESULTS";
}

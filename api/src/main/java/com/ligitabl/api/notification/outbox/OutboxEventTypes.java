package com.ligitabl.api.notification.outbox;

/** Event-type discriminators stored in t_outbox_event.c_event_type. */
public final class OutboxEventTypes {

    private OutboxEventTypes() {}

    public static final String ROUND_RESULTS = "ROUND_RESULTS";
}

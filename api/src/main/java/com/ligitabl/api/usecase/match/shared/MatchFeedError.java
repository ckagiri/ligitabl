package com.ligitabl.api.usecase.match.shared;

public sealed interface MatchFeedError {
    String message();

    /** Network errors, 5xx and rate limiting: a later attempt may succeed. */
    record Unavailable(String message) implements MatchFeedError {}

    /** Bad credentials, unknown ids and anything unexpected: needs a human. */
    record Rejected(String message) implements MatchFeedError {}
}

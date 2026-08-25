package com.ligitabl.api.notification.email;

import java.time.Instant;

public sealed interface EmailError {
    record NoValidRecipients() implements EmailError {}

    record TemplateRenderError(String templateName, String reason) implements EmailError {}

    record EmailProviderError(String providerMessage) implements EmailError {}

    /**
     * The provider refused the send because an account-level sending limit is exhausted — not a
     * fault in the message, and retrying before {@code retryAfter} can only fail again.
     *
     * <p>{@code retryAfter} is the provider's own reset time when it gave one, otherwise null;
     * callers that can reschedule should park the work until then rather than spending retry
     * attempts against a window that outlasts their backoff budget.
     */
    record RateLimited(String providerMessage, Instant retryAfter) implements EmailError {}
}

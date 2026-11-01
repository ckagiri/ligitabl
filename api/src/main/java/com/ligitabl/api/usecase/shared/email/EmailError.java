package com.ligitabl.api.usecase.shared.email;

import java.time.Instant;

public sealed interface EmailError {
    record NoValidRecipients() implements EmailError {}

    record TemplateRenderError(String templateName, String reason) implements EmailError {}

    record EmailProviderError(String providerMessage) implements EmailError {}

    /**
     * An account-level sending limit is exhausted: not the message's fault, and retrying before
     * {@code retryAfter} (the provider's reset time, or null) can only fail. Callers that can
     * reschedule should park the work until then.
     */
    record RateLimited(String providerMessage, Instant retryAfter) implements EmailError {}
}

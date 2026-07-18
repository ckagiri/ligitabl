package com.ligitabl.api.notification.email;

public sealed interface EmailError {
    record NoValidRecipients() implements EmailError {}

    record TemplateRenderError(String templateName, String reason) implements EmailError {}

    record EmailProviderError(String providerMessage) implements EmailError {}
}

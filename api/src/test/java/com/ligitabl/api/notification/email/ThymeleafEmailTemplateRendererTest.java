package com.ligitabl.api.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ThymeleafEmailTemplateRendererTest {

    private ThymeleafEmailTemplateRenderer renderer;

    @BeforeEach
    void setup() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new ThymeleafEmailTemplateRenderer(engine);
    }

    @Test
    void rendersEmailVerificationWithUrlAndExpiry() {
        var result = renderer.render(
                EmailCommand.EmailType.EMAIL_VERIFICATION,
                Map.of(
                        "verificationUrl", "http://localhost:8080/auth/verify-email?token=abc-123",
                        "expiryHours", 48,
                        "recipientEmail", "player@example.com"));

        assertThat(result.isRight()).isTrue();
        EmailContent content = result.get();
        assertThat(content.subject()).isEqualTo("Verify your LigiPredictor email");
        assertThat(content.htmlBody()).contains("http://localhost:8080/auth/verify-email?token=abc-123");
        assertThat(content.htmlBody()).contains("48");
        assertThat(content.htmlBody()).contains("player@example.com");
        assertThat(content.textBody()).isNotBlank();
    }

    @Test
    void rendersAllEmailTypesWithoutError() {
        Map<EmailCommand.EmailType, Map<String, Object>> dataByType = Map.of(
                EmailCommand.EmailType.PASSWORD_RESET,
                        Map.of("resetUrl", "http://x", "expiryMinutes", 30, "recipientEmail", "a@b.c"),
                EmailCommand.EmailType.PASSWORD_RESET_CONFIRMATION, Map.of("recipientEmail", "a@b.c"),
                EmailCommand.EmailType.EMAIL_VERIFICATION,
                        Map.of("verificationUrl", "http://x", "expiryHours", 48, "recipientEmail", "a@b.c"));

        for (EmailCommand.EmailType type : EmailCommand.EmailType.values()) {
            var result = renderer.render(type, dataByType.get(type));
            assertThat(result.isRight()).as("render %s", type).isTrue();
            assertThat(result.get().subject()).isNotBlank();
            assertThat(result.get().htmlBody()).isNotBlank();
        }
    }
}

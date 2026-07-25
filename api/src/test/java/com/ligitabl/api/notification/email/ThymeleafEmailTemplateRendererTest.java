package com.ligitabl.api.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.model.domain.HitDistribution;

class ThymeleafEmailTemplateRendererTest {

    private ThymeleafEmailTemplateRenderer renderer;

    @BeforeEach
    void setup() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        // SpringTemplateEngine (not the bare thymeleaf-core TemplateEngine) so the
        // SpringStandardDialect is registered — templates use SpringEL's T(...)
        // operator (e.g. round-results.html's ScoreTier lookup), which the plain
        // OGNL-based StandardDialect does not support.
        SpringTemplateEngine engine = new SpringTemplateEngine();
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
                        Map.of("verificationUrl", "http://x", "expiryHours", 48, "recipientEmail", "a@b.c"),
                EmailCommand.EmailType.ROUND_RESULTS, roundResultsData());

        for (EmailCommand.EmailType type : EmailCommand.EmailType.values()) {
            var result = renderer.render(type, dataByType.get(type));
            assertThat(result.isRight()).as("render %s", type).isTrue();
            assertThat(result.get().subject()).isNotBlank();
            assertThat(result.get().htmlBody()).isNotBlank();
        }
    }

    @Test
    void roundResultsSubjectCarriesRoundAndScore() {
        var result = renderer.render(EmailCommand.EmailType.ROUND_RESULTS, roundResultsData());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().subject()).isEqualTo("Your Gameweek 22 Results — 175 points!");
        assertThat(result.get().htmlBody()).contains("Alice").contains("175");
    }

    @Test
    void roundResultsRendersWithoutErrorForVariantCases() {
        // Zero score: no ScoreTier badge should render, and the block must not throw.
        var zeroScore = new java.util.HashMap<>(roundResultsData());
        zeroScore.put("score", 0);
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, zeroScore).isRight())
                .as("zero score")
                .isTrue();

        // Negative movement on both sprint and quarter.
        var negativeMovement = new java.util.HashMap<>(roundResultsData());
        negativeMovement.put(
                "sprint", new RoundResultsPayload.SprintPlacement("S8", 21, 23, 6, 120, -2, 190, false));
        negativeMovement.put(
                "quarter", new RoundResultsPayload.QuarterPlacement("Q3", 20, 28, 6, 130, -2, 190, false));
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, negativeMovement)
                        .isRight())
                .as("negative movement")
                .isTrue();

        // round == fromRound for both phases: the 🏁 "your best" variant.
        var phaseOpener = new java.util.HashMap<>(roundResultsData());
        phaseOpener.put("round", 20);
        phaseOpener.put("sprint", new RoundResultsPayload.SprintPlacement("S8", 20, 22, 2, 120, null, 175, false));
        phaseOpener.put(
                "quarter", new RoundResultsPayload.QuarterPlacement("Q3", 20, 28, 5, 130, null, 175, false));
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, phaseOpener)
                        .isRight())
                .as("round == fromRound")
                .isTrue();

        // No quarter phase configured — its best-callout block and standings row must both be
        // skipped gracefully, not throw. Sprint's block still renders.
        var noQuarter = new java.util.HashMap<>(roundResultsData());
        noQuarter.put("quarter", null);
        var noQuarterResult = renderer.render(EmailCommand.EmailType.ROUND_RESULTS, noQuarter);
        assertThat(noQuarterResult.isRight()).as("null quarter").isTrue();
        assertThat(noQuarterResult.get().htmlBody()).doesNotContain("quarter best");
        assertThat(noQuarterResult.get().htmlBody()).contains("sprint best");

        // No sprint phase configured — same, but for the sprint block/row.
        var noSprint = new java.util.HashMap<>(roundResultsData());
        noSprint.put("sprint", null);
        var noSprintResult = renderer.render(EmailCommand.EmailType.ROUND_RESULTS, noSprint);
        assertThat(noSprintResult.isRight()).as("null sprint").isTrue();
        assertThat(noSprintResult.get().htmlBody()).doesNotContain("sprint best");
        assertThat(noSprintResult.get().htmlBody()).contains("quarter best");

        // No full-season phase configured (season is the only plain secondary standing).
        var noSeason = new java.util.HashMap<>(roundResultsData());
        noSeason.put("season", null);
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, noSeason).isRight())
                .as("null season")
                .isTrue();

        // Season's final round: CTA button must not render (and must not throw).
        var finalRound = new java.util.HashMap<>(roundResultsData());
        finalRound.put("currentRound", 38);
        finalRound.put("lastRound", 38);
        finalRound.put("showDetailedResultsLink", false);
        var finalRoundResult = renderer.render(EmailCommand.EmailType.ROUND_RESULTS, finalRound);
        assertThat(finalRoundResult.isRight()).as("final round").isTrue();
        assertThat(finalRoundResult.get().htmlBody()).doesNotContain("View Detailed Results");
    }

    private static Map<String, Object> roundResultsData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("userDisplayName", "Alice");
        data.put("round", 22);
        data.put("score", 175);
        data.put("currentRound", 22);
        data.put("lastRound", 38);
        data.put("hitDistribution", new HitDistribution(5, 8, 5, 2));
        data.put("sprint", new RoundResultsPayload.SprintPlacement("S8", 21, 23, 2, 120, 1, 175, true));
        data.put("quarter", new RoundResultsPayload.QuarterPlacement("Q3", 20, 28, 5, 130, 1, 175, true));
        data.put("season", new RoundResultsPayload.Placement("FS", 18, 140));
        data.put("frontendUrl", "http://localhost:8080");
        data.put("showDetailedResultsLink", true);
        data.put("detailedResultsUrl", "http://localhost:8080/my-table?round=22");
        return data;
    }
}

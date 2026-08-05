package com.ligitabl.api.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.api.notification.outbox.SegmentResultsPayload;
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
                EmailCommand.EmailType.ROUND_RESULTS, roundResultsData(),
                EmailCommand.EmailType.JOIN_REMINDER,
                        Map.of(
                                "stage",
                                1,
                                "myTableUrl",
                                "http://x/my-table",
                                "leaderboardUrl",
                                "http://x/leaderboard"),
                EmailCommand.EmailType.SEASON_WELCOME, seasonWelcomeData(),
                EmailCommand.EmailType.SEGMENT_RESULTS, segmentResultsData());

        for (EmailCommand.EmailType type : EmailCommand.EmailType.values()) {
            // Without this, a newly added type renders with null variables and passes
            // meaninglessly — the loop would claim coverage it does not have.
            assertThat(dataByType)
                    .as("every EmailType needs fixture data here, or this loop proves nothing for it")
                    .containsKey(type);

            var result = renderer.render(type, dataByType.get(type));
            assertThat(result.isRight()).as("render %s", type).isTrue();
            assertThat(result.get().subject()).isNotBlank();
            assertThat(result.get().htmlBody()).isNotBlank();
        }
    }

    private static Map<String, Object> seasonWelcomeData() {
        return Map.of(
                "myTableUrl", "http://x/my-table",
                "leaderboardUrl", "http://x/leaderboard",
                "faqUrl", "http://x/faq",
                "whatIfUrl", "http://x/my-table/what-if");
    }

    /**
     * A sprint-only podium, the simplest shape. Phase 5 pins the copy itself (the double callout,
     * the season finale, the ordinal subjects); this exists so the exhaustive-type loop above has
     * real variables to render with rather than passing vacuously on nulls.
     */
    private static Map<String, Object> segmentResultsData() {
        return Map.of(
                "userDisplayName",
                "Ada",
                "placements",
                List.of(new SegmentResultsPayload.SegmentPlacement("SPRINT", "S1", "Sprint 1", 1, 4, 2, 40, 180)),
                "headlineName",
                "Sprint 1",
                "headlineRank",
                2,
                "isSeasonFinale",
                false,
                "isDouble",
                false,
                "leaderboardUrl",
                "http://x/leaderboard?phase=S1",
                "myTableUrl",
                "http://x/my-table");
    }

    @Test
    void seasonWelcomeStatesTheSwapAllowanceAndTheDeadline() {
        var result = renderer.render(EmailCommand.EmailType.SEASON_WELCOME, seasonWelcomeData());

        assertThat(result.isRight()).isTrue();
        EmailContent content = result.get();

        assertThat(content.subject()).isEqualTo("The season's underway — you have 5 swaps");
        assertThat(content.htmlBody())
                .as("the allowance, matching CreatePredictionUseCase.MAX_INITIAL_SWAPS")
                .contains("5 swaps");
        assertThat(content.htmlBody())
                .as("names the deadline concretely; SeasonInPlayEnqueuer guarantees round 1 is open")
                .contains("Gameweek 1 is still open")
                .contains("Once Gameweek 1 locks");
        assertThat(content.htmlBody())
                .as("What-If is how a new joiner reasons about spending the swaps, so it is linked")
                .contains("What-If")
                .contains("http://x/my-table/what-if");
        assertThat(content.htmlBody())
                .contains("http://x/my-table")
                .contains("http://x/leaderboard")
                .contains("http://x/faq");
        assertThat(content.textBody()).isNotBlank();
    }

    @Test
    void joinReminderSubjectEscalatesWithStage() {
        var earlyStage = renderer.render(
                EmailCommand.EmailType.JOIN_REMINDER,
                Map.of("stage", 1, "myTableUrl", "http://x", "leaderboardUrl", "http://x"));
        var midStage = renderer.render(
                EmailCommand.EmailType.JOIN_REMINDER,
                Map.of("stage", 4, "myTableUrl", "http://x", "leaderboardUrl", "http://x"));
        var lateStage = renderer.render(
                EmailCommand.EmailType.JOIN_REMINDER,
                Map.of("stage", 11, "myTableUrl", "http://x", "leaderboardUrl", "http://x"));

        assertThat(earlyStage.get().subject()).isEqualTo("Set your table for the season!");
        assertThat(midStage.get().subject()).isEqualTo("Still haven't set your table?");
        assertThat(lateStage.get().subject()).isEqualTo("Last chance to set your table before reminders stop");
    }

    // ---------------------------------------------------------- segment results

    private static SegmentResultsPayload.SegmentPlacement sprint(int rank) {
        return new SegmentResultsPayload.SegmentPlacement("SPRINT", "S2", "Sprint 2", 5, 9, rank, 41, 210);
    }

    private static SegmentResultsPayload.SegmentPlacement quarter(int rank) {
        return new SegmentResultsPayload.SegmentPlacement("QUARTER", "Q1", "Quarter 1", 1, 9, rank, 58, 395);
    }

    private static SegmentResultsPayload.SegmentPlacement season(int rank) {
        return new SegmentResultsPayload.SegmentPlacement("FULL_SEASON", "FS", "Season", 1, 38, rank, 61, 1500);
    }

    /**
     * Mirrors {@code OutboxEventProcessor.segmentResultsTemplateData}: the headline is the
     * <em>last</em> placement, since they arrive smallest-window-first.
     */
    private static Map<String, Object> segmentData(List<SegmentResultsPayload.SegmentPlacement> placements) {
        var headline = placements.get(placements.size() - 1);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("userDisplayName", "Ada");
        data.put("placements", placements);
        data.put("headlineName", headline.name());
        data.put("headlineRank", headline.rank());
        data.put("isSeasonFinale", "FULL_SEASON".equals(headline.type()));
        data.put("isDouble", placements.size() > 1);
        data.put("leaderboardUrl", "http://x/leaderboard?phase=" + headline.code());
        data.put("myTableUrl", "http://x/my-table");
        return data;
    }

    @Test
    void segmentResultsSprintOnlyStatesTheSegmentAndTheField() {
        var content = renderer
                .render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(sprint(2))))
                .get();

        assertThat(content.subject()).isEqualTo("Sprint 2 wrapped — you finished 2nd");
        assertThat(content.htmlBody())
                .contains("Sprint 2")
                .contains("runner-up")
                .as("rank, field size, points and the window are all quoted")
                .contains("2")
                .contains("41")
                .contains("210")
                .contains("http://x/leaderboard?phase=S2");
        assertThat(content.htmlBody())
                .as("no double callout for a single placement")
                .doesNotContain("Two at once");
        assertThat(content.htmlBody())
                .as("a sprint ending is not the end of the season")
                .doesNotContain("That's the season")
                .contains("Podium finish");
        assertThat(content.textBody()).isNotBlank();
    }

    /** The standout case: one email covering both, with the double called out as one achievement. */
    @Test
    void segmentResultsDoubleShowsBothBlocksAndTheCallout() {
        var content = renderer
                .render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(sprint(1), quarter(2))))
                .get();

        assertThat(content.subject())
                .as("headlines the quarter — the larger of the two — not the sprint")
                .isEqualTo("Quarter 1 wrapped — you finished 2nd");
        assertThat(content.htmlBody())
                .contains("Sprint 2")
                .contains("Quarter 1")
                .contains("Two at once")
                .contains("http://x/leaderboard?phase=Q1");
        assertThat(content.htmlBody())
                .as("each segment quotes its own field size, not one shared number")
                .contains("41")
                .contains("58");
    }

    @Test
    void segmentResultsSeasonFinaleUsesFinaleCopy() {
        var content = renderer
                .render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(season(1))))
                .get();

        assertThat(content.subject()).isEqualTo("You won the season 🏆");
        assertThat(content.htmlBody())
                .contains("That's the season")
                .contains("final table is locked in")
                .contains("See you next season");
        assertThat(content.htmlBody())
                .as("nothing is 'already running' once the season is over")
                .doesNotContain("already running")
                .doesNotContain("Podium finish");
    }

    @Test
    void segmentResultsSubjectVariesWithRank() {
        assertThat(renderer.render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(sprint(1))))
                        .get()
                        .subject())
                .isEqualTo("Sprint 2 is yours 🏆");
        assertThat(renderer.render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(sprint(2))))
                        .get()
                        .subject())
                .isEqualTo("Sprint 2 wrapped — you finished 2nd");
        assertThat(renderer.render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(sprint(3))))
                        .get()
                        .subject())
                .isEqualTo("Sprint 2 wrapped — you finished 3rd");
        assertThat(renderer.render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(season(2))))
                        .get()
                        .subject())
                .as("the finale gets its own wording rather than naming the 'Season' phase")
                .isEqualTo("The season's done — you finished 2nd");
    }

    /**
     * The plain-text alternative currently inherits the {@code <style>} block (see task 81
     * follow-ups), so this asserts the one thing that <em>is</em> in this template's control:
     * entities must not reach the reader as literal {@code &middot;}.
     */
    @Test
    void segmentResultsTextBodyCarriesNoRawEntities() {
        var content = renderer
                .render(EmailCommand.EmailType.SEGMENT_RESULTS, segmentData(List.of(sprint(1), quarter(2))))
                .get();

        assertThat(content.textBody())
                .doesNotContain("&middot;")
                .doesNotContain("&ndash;")
                .doesNotContain("&mdash;")
                .doesNotContain("&nbsp;");
        assertThat(content.textBody()).contains("Sprint 2").contains("Quarter 1");
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
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, zeroScore)
                        .isRight())
                .as("zero score")
                .isTrue();

        // Negative movement on both sprint and season.
        var negativeMovement = new java.util.HashMap<>(roundResultsData());
        negativeMovement.put("sprint", new RoundResultsPayload.SprintPlacement("S8", 21, 23, 6, 120, -2, 190, false));
        negativeMovement.put(
                "season", new RoundResultsPayload.SeasonPlacement("Season", 1, 38, 6, 140, -2, 1900, false));
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, negativeMovement)
                        .isRight())
                .as("negative movement")
                .isTrue();

        // round == fromRound for both phases: the 🏁 "your best" variant.
        var phaseOpener = new java.util.HashMap<>(roundResultsData());
        phaseOpener.put("round", 20);
        phaseOpener.put("sprint", new RoundResultsPayload.SprintPlacement("S8", 20, 22, 2, 120, null, 175, false));
        phaseOpener.put("season", new RoundResultsPayload.SeasonPlacement("Season", 20, 38, 5, 140, null, 175, false));
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, phaseOpener)
                        .isRight())
                .as("round == fromRound")
                .isTrue();

        // No season best-callout (suppressed, or no data) — its block and standings row must
        // both be skipped gracefully, not throw. Sprint's block still renders.
        var noSeason = new java.util.HashMap<>(roundResultsData());
        noSeason.put("season", null);
        var noSeasonResult = renderer.render(EmailCommand.EmailType.ROUND_RESULTS, noSeason);
        assertThat(noSeasonResult.isRight()).as("null season").isTrue();
        assertThat(noSeasonResult.get().htmlBody()).doesNotContain("season best");
        assertThat(noSeasonResult.get().htmlBody()).contains("sprint best");

        // No sprint phase configured — same, but for the sprint block/row.
        var noSprint = new java.util.HashMap<>(roundResultsData());
        noSprint.put("sprint", null);
        var noSprintResult = renderer.render(EmailCommand.EmailType.ROUND_RESULTS, noSprint);
        assertThat(noSprintResult.isRight()).as("null sprint").isTrue();
        assertThat(noSprintResult.get().htmlBody()).doesNotContain("sprint best");
        assertThat(noSprintResult.get().htmlBody()).contains("season best");

        // No quarter phase configured (quarter is the only plain secondary standing).
        var noQuarter = new java.util.HashMap<>(roundResultsData());
        noQuarter.put("quarter", null);
        assertThat(renderer.render(EmailCommand.EmailType.ROUND_RESULTS, noQuarter)
                        .isRight())
                .as("null quarter")
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
        data.put("season", new RoundResultsPayload.SeasonPlacement("Season", 1, 38, 18, 140, 1, 175, true));
        data.put("quarter", new RoundResultsPayload.Placement("Q3", 5, 130));
        data.put("frontendUrl", "http://localhost:8080");
        data.put("showDetailedResultsLink", true);
        data.put("detailedResultsUrl", "http://localhost:8080/my-table?round=22");
        return data;
    }
}

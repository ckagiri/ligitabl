package com.ligitabl.api.domain.season;

import static com.ligitabl.api.domain.season.SeasonTestFixtures.NEXT_SEASON_END;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.NEXT_SEASON_START;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.NOW;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.PREDICTIONS_OPEN_AT;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.PREVIOUS_SEASON_END;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.PRE_SEASON_OPENS_AT;
import static com.ligitabl.api.domain.season.SeasonTestFixtures.utc;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonState;

/**
 * Walks the upcoming season through its real year, in order.
 *
 * <p>The {@code Season_is*Test} truth tables vary {@code completed}, {@code preSeasonOpensAt} and
 * {@code predictionsOpenAt} independently, which is what makes them exhaustive — but it also means
 * nothing there asserts that the ordering the app actually produces
 * ({@code preSeasonOpensAt < predictionsOpenAt < startDate < endDate}) yields a sensible
 * <em>progression</em>. A state machine can be right on every isolated combination and still walk
 * its own calendar wrongly. This is the test for that.
 */
@DisplayName("Season — a full year, in order")
class Season_lifecycleTest {

    /** The upcoming season as configured for play, before the admin marks it complete. */
    private static Season running() {
        return SeasonTestFixtures.season(false, PRE_SEASON_OPENS_AT, PREDICTIONS_OPEN_AT);
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("theYear")
    void theSeasonProgressesThroughItsPhases(String moment, Instant at, SeasonState expected) {
        assertThat(running().getSeasonState(at)).isEqualTo(expected);
    }

    private static Stream<Arguments> theYear() {
        LocalDate preSeasonOpens = PRE_SEASON_OPENS_AT.toLocalDate();
        LocalDate predictionsOpen = PREDICTIONS_OPEN_AT.toLocalDate();

        return Stream.of(
                at("a fortnight before pre-season opens", preSeasonOpens.minusDays(14), SeasonState.OFF_SEASON),
                // isAfter is strict, so the season is not yet open on the instant it opens.
                Arguments.of(
                        "midnight on the exact opening instant", PRE_SEASON_OPENS_AT.toInstant(), SeasonState.OFF_SEASON),
                at("the day after, pre-season underway", preSeasonOpens.plusDays(1), SeasonState.PRE_SEASON),
                at("the day before predictions open", predictionsOpen.minusDays(1), SeasonState.PRE_SEASON),
                at("the day after predictions open", predictionsOpen.plusDays(1), SeasonState.IN_PLAY),
                at("first kickoff", NEXT_SEASON_START, SeasonState.IN_PLAY),
                at("New Year's Day, mid-season", LocalDate.of(NEXT_SEASON_END.getYear(), 1, 1), SeasonState.IN_PLAY),
                // Still IN_PLAY on the final day: completion is a separate admin action, not a date.
                at("the final day", NEXT_SEASON_END, SeasonState.IN_PLAY));
    }

    /**
     * ⚠️ The one transition that is not automatic, and the reason it is worth its own test.
     *
     * <p>Marking the season complete the day after the last match does <em>not</em> put it in
     * OFF_SEASON. Its own {@code preSeasonOpensAt} is still last July's, so {@code preSeasonOpen} is
     * true and the {@code completed && preSeasonOpen && predictionsOpen} branch claims it as
     * INACTIVE. OFF_SEASON only arrives once the admin rolls that field forward to the next
     * handover date — which is exactly what {@code UpdateSeasonDatesUseCase} does when the
     * successor is configured.
     *
     * <p>So "completed" and "off-season" are two steps, not one. Nothing else in this package says
     * so, because the truth tables never hold a season still and move the clock past its end.
     */
    @Test
    void completingTheSeasonIsNotEnoughToReachOffSeason_theHandoverDateMustRollForwardToo() {
        Instant dayAfterTheLastMatch = utc(NEXT_SEASON_END.plusDays(1)).toInstant();

        Season justCompleted = SeasonTestFixtures.season(true, PRE_SEASON_OPENS_AT, PREDICTIONS_OPEN_AT);
        assertThat(justCompleted.getSeasonState(dayAfterTheLastMatch)).isEqualTo(SeasonState.INACTIVE);

        OffsetDateTime nextHandover = utc(PRE_SEASON_OPENS_AT.toLocalDate().plusYears(1));
        Season rolledForward = SeasonTestFixtures.season(true, nextHandover, PREDICTIONS_OPEN_AT);
        assertThat(rolledForward.getSeasonState(dayAfterTheLastMatch)).isEqualTo(SeasonState.OFF_SEASON);
    }

    /**
     * And once that rolled-forward date passes, the outgoing season goes INACTIVE again — the state
     * its javadoc describes as "a completed season whose preSeasonOpensAt has passed but whose
     * successor hasn't been promoted yet". It is the same instant that makes the *successor*
     * PRE_SEASON, which is the dual meaning recorded on
     * {@link SeasonTestFixtures#PRE_SEASON_OPENS_AT}.
     */
    @Test
    void afterTheNextHandoverDateTheOutgoingSeasonIsInactive_whileItsSuccessorIsPreSeason() {
        OffsetDateTime nextHandover = utc(PRE_SEASON_OPENS_AT.toLocalDate().plusYears(1));
        Instant justAfterHandover = nextHandover.toInstant().plusSeconds(86_400);

        Season outgoing = SeasonTestFixtures.season(true, nextHandover, PREDICTIONS_OPEN_AT);
        assertThat(outgoing.getSeasonState(justAfterHandover)).isEqualTo(SeasonState.INACTIVE);

        // The season after next, on the same shape one year on.
        Season successor = SeasonTestFixtures.season(
                false,
                nextHandover,
                utc(PREDICTIONS_OPEN_AT.toLocalDate().plusYears(1)),
                NEXT_SEASON_START.plusYears(1),
                NEXT_SEASON_END.plusYears(1));
        assertThat(successor.getSeasonState(justAfterHandover)).isEqualTo(SeasonState.PRE_SEASON);
    }

    /**
     * The fixture anchor is derived from the calendar constants rather than written out, so this
     * asserts the four properties that derivation is supposed to guarantee.
     *
     * <p>Without it, editing a date above could move {@code NOW} outside the pre-season window and
     * every fixture in the package would quietly start describing a different world — the default
     * season would read IN_PLAY or OFF_SEASON instead of PRE_SEASON, and the truth tables would go
     * on passing while asserting something else. A fixture this much is built on should say out
     * loud what it assumes.
     */
    @Test
    void theFixtureAnchorSitsMidPreSeason() {
        assertThat(NOW)
                .as("after the previous season ended, so a completed season is genuinely past")
                .isAfter(utc(PREVIOUS_SEASON_END).toInstant())
                .as("before the next season starts, so an upcoming season is genuinely upcoming")
                .isBefore(utc(NEXT_SEASON_START).toInstant())
                .as("after pre-season opened")
                .isAfter(PRE_SEASON_OPENS_AT.toInstant())
                .as("before predictions open")
                .isBefore(PREDICTIONS_OPEN_AT.toInstant());

        assertThat(NOW.atOffset(ZoneOffset.UTC).toLocalTime())
                .as("midday, so a zone bug in the date derivation cannot hide")
                .isEqualTo(LocalTime.NOON);

        // The properties above are what the package relies on; this is the state they add up to.
        assertThat(running().getSeasonState(NOW)).isEqualTo(SeasonState.PRE_SEASON);
    }

    private static Arguments at(String moment, LocalDate date, SeasonState expected) {
        // Midday, so the assertion is about the date rather than about the zone the date is read in.
        return Arguments.of(moment, utc(date).toInstant().plusSeconds(43_200), expected);
    }
}

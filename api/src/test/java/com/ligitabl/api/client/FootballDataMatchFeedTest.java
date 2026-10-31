package com.ligitabl.api.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ligitabl.api.client.footballdata.CompetitionResponse;
import com.ligitabl.api.client.footballdata.CurrentSeason;
import com.ligitabl.api.client.footballdata.FullTime;
import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.client.footballdata.MatchesResponse;
import com.ligitabl.api.client.footballdata.Score;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.match.shared.FeedMatch;
import com.ligitabl.api.usecase.match.shared.MatchFeedError;
import com.ligitabl.model.domain.league.MatchStatus;

class FootballDataMatchFeedTest {

    private static final OffsetDateTime KICKOFF = OffsetDateTime.of(2026, 10, 24, 14, 0, 0, 0, ZoneOffset.UTC);

    private final FootballDataClient client = mock(FootballDataClient.class);
    private final FootballDataMatchFeed feed = new FootballDataMatchFeed(client);

    @ParameterizedTest
    @CsvSource({
        "SCHEDULED, SCHEDULED",
        "TIMED, SCHEDULED",
        "IN_PLAY, LIVE",
        "PAUSED, LIVE",
        "FINISHED, FINISHED",
        "AWARDED, FINISHED",
        "SUSPENDED, SUSPENDED",
        "POSTPONED, POSTPONED",
        "CANCELLED, CANCELLED",
        "SOMETHING_NEW, SCHEDULED"
    })
    void mapsFootballDataStatuses(String apiStatus, MatchStatus expected) {
        assertThat(feed.toDomainStatus(apiStatus)).isEqualTo(expected);
    }

    @Test
    void mapsMatchFields() {
        var score = new Score(null, "REGULAR", new FullTime(2, 1), null);
        when(client.getMatchById(560616))
                .thenReturn(Either.right(
                        new MatchDto(560616L, KICKOFF, "IN_PLAY", 8, "REGULAR_SEASON", null, null, score, 67, 2)));

        assertThat(feed.match(560616).get())
                .isEqualTo(new FeedMatch(560616L, MatchStatus.LIVE, KICKOFF, 8, 67, 2, 2, 1));
    }

    @Test
    void leavesGoalsNullWithoutAFullTimeScore() {
        var noScore = new MatchDto(1L, KICKOFF, "TIMED", 8, "REGULAR_SEASON", null, null, null, null, null);
        var noFullTime = new MatchDto(
                2L, KICKOFF, "TIMED", 8, "REGULAR_SEASON", null, null, new Score(null, null, null, null), null, null);
        var halfKnown = new MatchDto(
                3L,
                KICKOFF,
                "IN_PLAY",
                8,
                "REGULAR_SEASON",
                null,
                null,
                new Score(null, null, new FullTime(1, null), null),
                null,
                null);
        when(client.getLiveMatches("PL"))
                .thenReturn(Either.right(new MatchesResponse(List.of(noScore, noFullTime, halfKnown), null)));

        var matches = feed.liveMatches("PL").get();

        assertThat(matches).extracting(FeedMatch::homeGoals).containsExactly(null, null, 1);
        assertThat(matches).extracting(FeedMatch::awayGoals).containsExactly(null, null, null);
    }

    @Test
    void transientFailuresAreUnavailable() {
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.NetworkError("timeout", null)))
                .isEqualTo(new MatchFeedError.Unavailable("timeout"));
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.ServerError("502", 502)))
                .isInstanceOf(MatchFeedError.Unavailable.class);
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.RateLimitExceeded("slow down")))
                .isInstanceOf(MatchFeedError.Unavailable.class);
    }

    @Test
    void everythingElseIsRejected() {
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.Unauthorized("bad token")))
                .isEqualTo(new MatchFeedError.Rejected("bad token"));
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.NotFound("404")))
                .isInstanceOf(MatchFeedError.Rejected.class);
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.UnknownError("418", null)))
                .isInstanceOf(MatchFeedError.Rejected.class);
        assertThat(FootballDataMatchFeed.toFeedError(new FootballDataApiError.UnexpectedError("null body")))
                .isInstanceOf(MatchFeedError.Rejected.class);
    }

    @Test
    void passesClientErrorsThrough() {
        when(client.getMatchesByIds("PL", List.of(1, 2)))
                .thenReturn(Either.left(new FootballDataApiError.RateLimitExceeded("Rate limit exceeded: 0")));

        assertThat(feed.matchesByIds("PL", List.of(1, 2)).getLeft())
                .isEqualTo(new MatchFeedError.Unavailable("Rate limit exceeded: 0"));
    }

    @Test
    void readsCurrentMatchday() {
        when(client.getCompetition("PL")).thenReturn(Either.right(competitionAtMatchday(9)));

        assertThat(feed.currentMatchday("PL").get()).isEqualTo(9);
    }

    @Test
    void rejectsCompetitionWithoutCurrentMatchday() {
        when(client.getCompetition("PL")).thenReturn(Either.right(competitionAtMatchday(null)));

        assertThat(feed.currentMatchday("PL").getLeft()).isInstanceOf(MatchFeedError.Rejected.class);
    }

    private static CompetitionResponse competitionAtMatchday(Integer matchday) {
        return new CompetitionResponse(
                2021L, "Premier League", "PL", "LEAGUE", null, new CurrentSeason(1L, null, null, matchday));
    }
}

package com.ligitabl.api.usecase.match.syncmatches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.usecase.match.MatchUpdateHelper;
import com.ligitabl.api.usecase.match.shared.FeedMatch;
import com.ligitabl.api.usecase.match.shared.MatchFeed;
import com.ligitabl.api.usecase.match.shared.MatchFeedError;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.MatchRepo;

/**
 * Ported from .art/testing/SyncMatchesUseCaseTest.java.
 */
@ExtendWith(MockitoExtension.class)
class SyncMatchesUseCaseTest {

    private static final String COMPETITION_CODE = "PL";
    private static final String COMPETITION_SLUG = "premier-league";

    @Mock
    private MatchFeed matchFeed;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private AsyncStandingsService standingsService;

    @Mock
    private LiveMatchTracker liveMatchTracker;

    private SyncMatchesUseCase useCase;

    private UUID seasonId;
    private UUID roundId;
    private UUID competitionId;

    @BeforeEach
    void setUp() throws Exception {
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        competitionId = UUID.randomUUID();

        useCase = new SyncMatchesUseCase(
                matchFeed,
                hierarchyValidator,
                matchRepo,
                standingsService,
                new CompetitionDefaults(COMPETITION_SLUG),
                liveMatchTracker,
                new MatchUpdateHelper(),
                TestClock.FIXED);

        var field = SyncMatchesUseCase.class.getDeclaredField("competitionCode");
        field.setAccessible(true);
        field.set(useCase, COMPETITION_CODE);
    }

    @Test
    void shouldHandleCompetitionNotFound() {
        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(Either.left(UseCaseErrors.notFound("Competition", COMPETITION_SLUG)));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(SyncMatchesError.HierarchyError.class);
        var error = (SyncMatchesError.HierarchyError) result.getLeft();
        assertThat(error.error().getMessage()).contains("Competition");
    }

    @Test
    void shouldHandleSeasonNotFound() {
        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(Either.left(UseCaseErrors.validation("Competition has no active season")));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(SyncMatchesError.HierarchyError.class);
        var error = (SyncMatchesError.HierarchyError) result.getLeft();
        assertThat(error.error().getMessage()).contains("active season");
    }

    @Test
    void shouldHandleApiError() {
        var season = createSeason();
        var round = createRound();
        var existingMatches = List.of(
                createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10)));

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(false, Set.of(), Set.of()));
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches);

        when(matchFeed.matchesBetween(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.left(new MatchFeedError.Unavailable("Network error")));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(SyncMatchesError.DataAccessError.class);
    }

    @Test
    void shouldDetectAllMatchesComplete() {
        var season = createSeason();
        var round = createRound();

        var m1 = createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10));
        var m2 = createMatch(2, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(11));
        var existingMatches = List.of(m1, m2);

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(false, Set.of(), Set.of()));

        var apiMatches = List.of(
                new FeedMatch(1L, MatchStatus.FINISHED, OffsetDateTime.now(), 1, null, null, null, null),
                new FeedMatch(2L, MatchStatus.FINISHED, OffsetDateTime.now(), 1, null, null, null, null));

        when(matchFeed.matchesBetween(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.right(apiMatches));

        // First call in getCurrentRound, second call after updateMatches reload.
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().allMatchesComplete()).isTrue();
        assertThat(result.get().nextSchedule().delay()).isZero();

        verify(standingsService).recalculateAsync(eq(seasonId), eq(1));
    }

    @Test
    void shouldDetectRoundObstructedWhenCancelledMatchPresentAndAllTerminalOrBlocking() {
        var season = createSeason();
        var round = createRound();

        var m1 = createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10));
        var m2 = createMatch(2, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(11));
        var existingMatches = List.of(m1, m2);

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(false, Set.of(), Set.of()));

        var apiMatches = List.of(
                new FeedMatch(1L, MatchStatus.FINISHED, OffsetDateTime.now(), 1, null, null, null, null),
                new FeedMatch(2L, MatchStatus.CANCELLED, OffsetDateTime.now(), 1, null, null, null, null));

        when(matchFeed.matchesBetween(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.right(apiMatches));

        // First call in resolveHierarchy, second call after updateMatches reload.
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);
        when(matchRepo.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().roundObstructed()).isTrue();
        assertThat(result.get().allMatchesComplete()).isFalse();
        assertThat(result.get().obstructedMatchIds()).containsExactly(m2.getId());
        assertThat(result.get().nextSchedule().delay()).isZero();

        // A match became FINISHED, so standings recalculation is triggered.
        verify(standingsService).recalculateAsync(eq(seasonId), eq(1));
    }

    @Test
    void shouldCalculateNextSyncForLiveMatches() {
        var season = createSeason();
        var round = createRound();

        var live = createMatch(1, MatchStatus.LIVE, OffsetDateTime.now().minusMinutes(10));
        var existingMatches = List.of(live);

        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(true, Set.of(), Set.of(live.getId())));

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);

        var apiMatches = List.of(new FeedMatch(1L, MatchStatus.LIVE, OffsetDateTime.now(), 1, null, null, null, null));
        when(matchFeed.liveMatches(COMPETITION_CODE)).thenReturn(Either.right(apiMatches));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().allMatchesComplete()).isFalse();
        assertThat(result.get().nextSchedule().delay()).isEqualTo(Duration.ofSeconds(90));

        verify(standingsService, never()).recalculateAsync(any(UUID.class), anyInt());
    }

    @Test
    void shouldReturnTwiceDailyScheduleWhenApiReturnsNoMatches() {
        var season = createSeason();
        var round = createRound();
        var existingMatches = List.of(
                createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10)));

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(false, Set.of(), Set.of()));
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);

        when(matchFeed.matchesBetween(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.right(List.of()));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().matchesProcessed()).isZero();
        assertThat(result.get().nextSchedule().delay().toHours()).isEqualTo(12);
    }

    @Test
    void fetchesFromOverdueKickoffDate_whenScheduledMatchKickedOffOnEarlierDay() {
        var season = createSeason();
        var round = createRound();
        var yesterdayKickoff = TestClock.TODAY.minusDays(1).atTime(14, 0).atOffset(ZoneOffset.UTC);
        var overdue = createMatch(1, MatchStatus.SCHEDULED, yesterdayKickoff);

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(false, Set.of(), Set.of()));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(overdue));
        when(matchFeed.matchesBetween(COMPETITION_CODE, TestClock.TODAY.minusDays(1), TestClock.TODAY.plusDays(1)))
                .thenReturn(Either.right(List.of()));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        verify(matchFeed, never()).matchesOn(any(), any());
    }

    @Test
    void recalculatesStandings_whenFinishedMatchScoreIsCorrected() {
        var season = createSeason();
        var round = createRound();
        var finished = createMatch(1, MatchStatus.FINISHED, OffsetDateTime.now().minusHours(3));
        finished.setScore(2, 1);
        var upcoming =
                createMatch(2, MatchStatus.SCHEDULED, OffsetDateTime.now().plusDays(1));

        when(hierarchyValidator.resolveHierarchy(COMPETITION_SLUG))
                .thenReturn(
                        Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(liveMatchTracker.updateTracking(any()))
                .thenReturn(new LiveMatchTracker.TrackingResult(false, Set.of(), Set.of()));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(finished, upcoming));

        var apiMatches = List.of(new FeedMatch(1L, MatchStatus.FINISHED, finished.getKickOff(), 1, null, null, 2, 2));
        when(matchFeed.matchesBetween(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.right(apiMatches));

        var result = useCase.execute(new SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().newlyFinishedMatches()).isZero();
        verify(standingsService).recalculateAsync(eq(seasonId), eq(1));
    }

    private Season createSeason() {
        return Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .clientId(1)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .currentRoundId(roundId)
                .currentMatchDay(1)
                .build();
    }

    private Round createRound() {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(1)
                .name("Round 1")
                .slug("round-1")
                .build();
    }

    private Match createMatch(int clientId, MatchStatus status, OffsetDateTime kickOff) {
        return Match.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .seasonId(seasonId)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("h-v-a-" + clientId)
                .status(status)
                .kickOff(kickOff)
                .matchday(1)
                .build();
    }
}

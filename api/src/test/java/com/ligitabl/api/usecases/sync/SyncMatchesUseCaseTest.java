package com.ligitabl.api.usecases.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.client.footballdata.MatchesResponse;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.scheduling.AsyncStandingsService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Ported from .art/testing/SyncMatchesUseCaseTest.java.
 */
@ExtendWith(MockitoExtension.class)
class SyncMatchesUseCaseTest {

    private static final String COMPETITION_CODE = "PL";
    private static final String COMPETITION_SLUG = "premier-league";

    @Mock
    private FootballDataClient footballDataClient;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private AsyncStandingsService standingsService;

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
                footballDataClient,
                seasonRepo,
                competitionRepo,
                roundRepo,
                matchRepo,
                standingsService,
                new CompetitionDefaults(COMPETITION_SLUG));

        var field = SyncMatchesUseCase.class.getDeclaredField("competitionCode");
        field.setAccessible(true);
        field.set(useCase, COMPETITION_CODE);
    }

    @Test
    void shouldHandleCompetitionNotFound() {
        when(competitionRepo.findBySlug(COMPETITION_SLUG)).thenReturn(Optional.empty());

        var result = useCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(SyncMatchesUseCase.SyncMatchesError.CompetitionNotFound.class);
    }

    @Test
    void shouldHandleSeasonNotFound() {
        when(competitionRepo.findBySlug(COMPETITION_SLUG)).thenReturn(Optional.of(createCompetition()));
        when(seasonRepo.findActiveSeason(COMPETITION_SLUG)).thenReturn(Optional.empty());

        var result = useCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(SyncMatchesUseCase.SyncMatchesError.SeasonNotFound.class);
    }

    @Test
    void shouldHandleApiError() {
        var season = createSeason();
        var round = createRound();
        var existingMatches = List.of(
                createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10)));

        when(competitionRepo.findBySlug(COMPETITION_SLUG)).thenReturn(Optional.of(createCompetition()));
        when(seasonRepo.findActiveSeason(COMPETITION_SLUG)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches);

        when(footballDataClient.getMatchesInDateRange(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.left(new FootballDataClient.ApiError.NetworkError("Network error", null)));

        var result = useCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(SyncMatchesUseCase.SyncMatchesError.DataAccessError.class);
    }

    @Test
    void shouldDetectAllMatchesComplete() {
        var season = createSeason();
        var round = createRound();

        var m1 = createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10));
        var m2 = createMatch(2, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(11));
        var existingMatches = List.of(m1, m2);

        when(competitionRepo.findBySlug(COMPETITION_SLUG)).thenReturn(Optional.of(createCompetition()));
        when(seasonRepo.findActiveSeason(COMPETITION_SLUG)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        var apiMatches = List.of(
                new MatchDto(1L, OffsetDateTime.now(), "FINISHED", 1, "REGULAR_SEASON", null, null, null),
                new MatchDto(2L, OffsetDateTime.now(), "FINISHED", 1, "REGULAR_SEASON", null, null, null));

        when(footballDataClient.getMatchesInDateRange(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.right(new MatchesResponse(apiMatches, null)));

        // First call in getCurrentRound, second call after updateMatches reload.
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);

        var result = useCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().allMatchesComplete()).isTrue();
        assertThat(result.get().nextSchedule().delay()).isZero();

        verify(standingsService).recalculateAsync(eq(seasonId), eq(1));
    }

    @Test
    void shouldCalculateNextSyncForLiveMatches() {
        var season = createSeason();
        var round = createRound();

        var live = createMatch(1, MatchStatus.LIVE, OffsetDateTime.now().minusMinutes(10));
        var existingMatches = List.of(live);

        when(competitionRepo.findBySlug(COMPETITION_SLUG)).thenReturn(Optional.of(createCompetition()));
        when(seasonRepo.findActiveSeason(COMPETITION_SLUG)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);

        var apiMatches =
                List.of(new MatchDto(1L, OffsetDateTime.now(), "IN_PLAY", 1, "REGULAR_SEASON", null, null, null));
        when(footballDataClient.getLiveMatches(COMPETITION_CODE))
                .thenReturn(Either.right(new MatchesResponse(apiMatches, null)));

        var result = useCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().allMatchesComplete()).isFalse();
        assertThat(result.get().nextSchedule().delay().toMinutes()).isEqualTo(3);

        verify(standingsService, never()).recalculateAsync(any(UUID.class), anyInt());
    }

    @Test
    void shouldReturnTwiceDailyScheduleWhenApiReturnsNoMatches() {
        var season = createSeason();
        var round = createRound();
        var existingMatches = List.of(
                createMatch(1, MatchStatus.SCHEDULED, OffsetDateTime.now().plusHours(10)));

        when(competitionRepo.findBySlug(COMPETITION_SLUG)).thenReturn(Optional.of(createCompetition()));
        when(seasonRepo.findActiveSeason(COMPETITION_SLUG)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(existingMatches).thenReturn(existingMatches);

        when(footballDataClient.getMatchesInDateRange(eq(COMPETITION_CODE), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Either.right(new MatchesResponse(List.of(), null)));

        var result = useCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().matchesProcessed()).isZero();
        assertThat(result.get().nextSchedule().delay().toHours()).isEqualTo(12);
    }

    private Competition createCompetition() {
        return Competition.builder()
                .id(competitionId)
                .slug(CompetitionSlug.of(COMPETITION_SLUG))
                .code(COMPETITION_CODE)
                .name("Premier League")
                .activeSeasonId(seasonId)
                .build();
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

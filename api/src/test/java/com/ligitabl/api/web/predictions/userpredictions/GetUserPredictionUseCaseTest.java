package com.ligitabl.api.web.predictions.userpredictions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.PredictionAccessMode;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

@ExtendWith(MockitoExtension.class)
class GetUserPredictionUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private SeasonPredictionRepo seasonPredictionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private RoundResultRepo roundResultRepo;

    @Mock
    private StandingsRepo standingsRepo;

    @Mock
    private MatchRepo matchRepo;

    private GetUserPredictionUseCase useCase;

    private UUID seasonId;
    private UUID roundId;
    private UUID userId;
    private List<TeamRank> baselineRankings;

    @BeforeEach
    void setUp() {
        useCase = new GetUserPredictionUseCase(
                competitionDefaults,
                competitionRepo,
                seasonPredictionRepo,
                seasonRepo,
                roundRepo,
                roundResultRepo,
                standingsRepo,
                matchRepo);

        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        userId = UUID.randomUUID();
        baselineRankings = List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2));
    }

    private Season createSeason(int maxRounds) {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .maxRounds(maxRounds)
                .completed(false)
                .initialRankings(baselineRankings)
                .build();
    }

    private void stubActiveSeason(Season season) {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
    }

    /**
     * Stubs the by-id lookup used by the baseline-rankings fallback ({@code getPreviousRoundRankings}
     * → {@code getSeasonBaselineRankings}) — only reached by the guest and no-prediction-yet view
     * builders, not the has-a-prediction paths, so call this only where that path is exercised.
     */
    private void stubSeasonBaseline(Season season) {
        when(seasonRepo.findById(season.getId())).thenReturn(Optional.of(season));
    }

    private Round createRound(int position, boolean finalized, boolean advanced) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .finalized(finalized)
                .advanced(advanced)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    // ── Guest view ──────────────────────────────────────────────────────────

    @Test
    void guestView_returnsFallbackRankingsWithReadonlyAccessMode() {
        Season season = createSeason(20);
        Round round = createRound(5, false, false);

        stubActiveSeason(season);
        stubSeasonBaseline(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 3)).thenReturn(Optional.empty());

        var query = GetUserPredictionQuery.forGuest(seasonId, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        UserPredictionViewData data = result.get();
        assertEquals(PredictionAccessMode.READONLY, data.accessMode());
        assertTrue(data.isGuest());
        assertEquals(baselineRankings, data.rankings());
    }

    // ── Matches fetched exactly once (E1) ──────────────────────────────────

    @Test
    void authenticatedCurrentRound_fetchesMatchesExactlyOnce() {
        Season season = createSeason(20);
        Round round = createRound(5, false, false);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(1)
                .build();
        // findBySeasonAndRound keys matches by team code — the same match appears under both
        // ARS and LIV, with homeTeam/awayTeam NOT loaded, exactly like the real (team-less) finder.
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .status(MatchStatus.SCHEDULED)
                .build();

        stubActiveSeason(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5))
                .thenReturn(Map.of("ARS", List.of(match), "LIV", List.of(match)));
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        verify(matchRepo, times(1)).findBySeasonAndRound(seasonId, 5);
        verify(matchRepo, never()).findByRoundId(any());
    }

    // ── hasPreSeasonRegistration wiring ──────────────────────────────────────

    @Test
    void authenticatedCurrentRound_hasPreSeasonRegistration_trueWhenAtRoundNumberZero() {
        Season season = createSeason(20);
        Round round = createRound(5, false, false);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(0)
                .build();

        stubActiveSeason(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertTrue(result.get().hasPreSeasonRegistration());
    }

    @Test
    void authenticatedCurrentRound_hasPreSeasonRegistration_falseWhenAtRoundNumberNonZero() {
        Season season = createSeason(20);
        Round round = createRound(5, false, false);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(3)
                .build();

        stubActiveSeason(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertFalse(result.get().hasPreSeasonRegistration());
    }

    // ── Historical round (genuinely past) ──────────────────────────────────

    @Test
    void authenticatedHistoricalRound_withRoundResult_returnsHistoricalView() {
        Season season = createSeason(20);
        Round currentRound = createRound(10, false, false);
        Round viewingRoundEntity = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(8)
                .finalized(true)
                .advanced(true)
                .name("Round 8")
                .slug("round-8")
                .build();
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(1)
                .build();
        RoundResult roundResult = RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(UUID.randomUUID())
                .rankings(List.of(new ResultTeamRank(TeamRank.of("ARS", 1), 1, 0)))
                .totalScore(42)
                .build();
        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .code("PL")
                .phases(List.of(RoundSpan.builder()
                        .code("s1")
                        .name("Sprint 1")
                        .from(1)
                        .to(10)
                        .type(PhaseType.SPRINT)
                        .build()))
                .build();

        stubActiveSeason(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 8)).thenReturn(Optional.of(viewingRoundEntity));
        when(matchRepo.findBySeasonAndRound(seasonId, 10)).thenReturn(Map.of());
        when(matchRepo.findByRoundId(viewingRoundEntity.getId())).thenReturn(List.of());
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundResultRepo.findByUserAndRound(userId, 8)).thenReturn(Optional.of(roundResult));
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(roundResultRepo.findByUserAndSeasonAndRoundPositionRange(eq(userId), eq(seasonId), anyInt(), eq(8)))
                .thenReturn(List.of(roundResult));

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, 8);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        UserPredictionViewData data = result.get();
        assertEquals(PredictionAccessMode.READONLY, data.accessMode());
        assertSame(roundResult, data.roundResult());
        assertEquals(42, data.seasonBestScore());
        assertEquals(Map.of(), data.matches());
    }

    // ── Last round advanced while still "current" (E3) ─────────────────────

    @Test
    void authenticatedCurrentRound_lastRoundAdvanced_withRoundResult_rendersAsHistorical() {
        Season season = createSeason(10);
        Round round = createRound(10, true, true); // finalized + advanced, still season.currentRoundId
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(1)
                .build();
        RoundResult roundResult = RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(UUID.randomUUID())
                .rankings(List.of(new ResultTeamRank(TeamRank.of("ARS", 1), 1, 0)))
                .totalScore(77)
                .build();
        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .code("PL")
                .phases(List.of(RoundSpan.builder()
                        .code("s1")
                        .name("Sprint 1")
                        .from(1)
                        .to(10)
                        .type(PhaseType.SPRINT)
                        .build()))
                .build();

        stubActiveSeason(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 10)).thenReturn(Map.of());
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundResultRepo.findByUserAndRound(userId, 10)).thenReturn(Optional.of(roundResult));
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(roundResultRepo.findByUserAndSeasonAndRoundPositionRange(eq(userId), eq(seasonId), anyInt(), eq(10)))
                .thenReturn(List.of(roundResult));

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        UserPredictionViewData data = result.get();
        assertEquals("ADVANCED", data.roundState());
        assertSame(roundResult, data.roundResult());
        assertEquals(PredictionAccessMode.READONLY, data.accessMode());
        // Never fell through to the live-prediction path
        verify(standingsRepo, never()).findBySeasonAndRoundPosition(seasonId, 10);
    }

    @Test
    void authenticatedCurrentRound_lastRoundAdvanced_missingRoundResult_throws() {
        Season season = createSeason(10);
        Round round = createRound(10, true, true);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(1)
                .build();

        stubActiveSeason(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 10)).thenReturn(Map.of());
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundResultRepo.findByUserAndRound(userId, 10)).thenReturn(Optional.empty());

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, true, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isLeft());
    }

    // ── seasonCompleted derivation (F2): from round state, not Season.completed ────

    @Test
    void seasonCompleted_trueWhenLastRoundAdvanced_regardlessOfSeasonCompletedFlag() {
        Season season = createSeason(5);
        season.setCompleted(false); // admin hasn't completed the season — irrelevant to the new derivation
        Round round = createRound(5, true, true); // finalized + advanced, last round

        stubActiveSeason(season);
        stubSeasonBaseline(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());

        var query = GetUserPredictionQuery.forGuest(seasonId, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertTrue(result.get().seasonCompleted());
    }

    @Test
    void seasonCompleted_falseWhenCurrentRoundNotAdvanced_evenIfSeasonCompletedFlagIsTrue() {
        Season season = createSeason(20);
        season.setCompleted(true); // stale/admin-set flag — no longer authoritative
        Round round = createRound(5, false, false); // not the last round, not advanced

        stubActiveSeason(season);
        stubSeasonBaseline(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());

        var query = GetUserPredictionQuery.forGuest(seasonId, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertFalse(result.get().seasonCompleted());
    }

    // ── resolveRoundStatus: advanced takes precedence over finalized ───────

    @Test
    void roundState_prefersAdvancedOverFinalized_whenBothFlagsSet() {
        Season season = createSeason(20);
        Round round = createRound(5, true, true); // both finalized and advanced

        stubActiveSeason(season);
        stubSeasonBaseline(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());

        var query = GetUserPredictionQuery.forGuest(seasonId, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertEquals("ADVANCED", result.get().roundState());
    }

    // ── No prediction yet ────────────────────────────────────────────────────

    @Test
    void authenticatedNoPrediction_currentRoundOpen_returnsCanCreateEntry() {
        Season season = createSeason(20);
        Round round = createRound(5, false, false);

        stubActiveSeason(season);
        stubSeasonBaseline(season);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findBySeasonAndRound(seasonId, 5)).thenReturn(Map.of());

        var query = GetUserPredictionQuery.forAuthenticatedUser(userId, seasonId, false, null);
        Either<?, UserPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        UserPredictionViewData data = result.get();
        assertEquals(PredictionAccessMode.CAN_CREATE_ENTRY, data.accessMode());
        assertEquals(5, data.atRoundNumber());
        verify(seasonPredictionRepo, never()).save(any());
    }
}

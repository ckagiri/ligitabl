package com.ligitabl.api.web.publicpredictions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.shared.season.SeasonPredictionSupport;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class GetPublicPredictionUseCaseTest {

    @Mock
    private SeasonPredictionSupport seasonPredictionSupport;

    @Mock
    private RoundResultRepo roundResultRepo;

    @Mock
    private StandingsRepo standingsRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private TeamRepo teamRepo;

    private GetPublicPredictionUseCase useCase;

    private UUID seasonId;
    private UUID roundId;
    private UUID userId;
    private String publicId;
    private List<TeamRank> baselineRankings;

    @BeforeEach
    void setUp() {
        useCase = new GetPublicPredictionUseCase(seasonPredictionSupport, roundResultRepo, standingsRepo, userRepo, teamRepo);

        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        userId = UUID.randomUUID();
        publicId = "T2ADsSc8hQ";
        baselineRankings = List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2));

        // TeamRepo lookups aren't the focus of these tests — no team rows means DTOs fall back to
        // the raw team code for every display field, which is fine for assertions on position/delta.
        lenient().when(teamRepo.findAllByCodes(any())).thenReturn(List.of());
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

    private Round createRound(int position, boolean advanced) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .advanced(advanced)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    private User createUser() {
        return User.builder()
                .id(userId)
                .publicId(PublicId.create(publicId))
                .displayName("Jane Doe")
                .build();
    }

    /** Stubs season + current-round resolution — every test needs this. */
    private void stubSeasonAndRound(Season season, Round round) {
        when(seasonPredictionSupport.findSeasonById(seasonId)).thenReturn(Optional.of(season));
        when(seasonPredictionSupport.findCurrentRound(season)).thenReturn(Optional.of(round));
    }

    @Test
    void userNotFound_returnsStandingsOnlyFallback() {
        Season season = createSeason(20);
        Round round = createRound(5, false);

        stubSeasonAndRound(season, round);
        when(userRepo.findByPublicId(PublicId.create(publicId))).thenReturn(Optional.empty());
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());

        var query = new GetPublicPredictionQuery(publicId, seasonId, null);
        Either<?, PublicPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        PublicPredictionViewData data = result.get();
        assertFalse(data.userFound());
        assertFalse(data.hasPrediction());
        assertNull(data.targetDisplayName());
        assertEquals(5, data.viewingRound());
        // Fallback rows come straight from the baseline — predicted == actual, delta always 0.
        assertEquals(2, data.rows().size());
        assertTrue(data.rows().stream().allMatch(row -> row.getDelta() == 0));
    }

    @Test
    void malformedPublicId_treatedAsNotFound() {
        Season season = createSeason(20);
        Round round = createRound(5, false);

        stubSeasonAndRound(season, round);
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.empty());

        var query = new GetPublicPredictionQuery("not-a-valid-id!!", seasonId, null);
        Either<?, PublicPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertFalse(result.get().userFound());
        verify(userRepo, never()).findByPublicId(any());
    }

    @Test
    void preSeasonRegistration_rendersInitialRankingsAgainstBaseline_withZeroDelta() {
        Season season = createSeason(20);
        Round round = createRound(1, false);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .initialRankings(baselineRankings)
                .atRoundNumber(0)
                .build();

        stubSeasonAndRound(season, round);
        when(userRepo.findByPublicId(PublicId.create(publicId))).thenReturn(Optional.of(createUser()));
        when(seasonPredictionSupport.findPrediction(userId, seasonId)).thenReturn(Optional.of(prediction));

        var query = new GetPublicPredictionQuery(publicId, seasonId, null);
        Either<?, PublicPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        PublicPredictionViewData data = result.get();
        assertTrue(data.userFound());
        assertTrue(data.hasPrediction());
        assertEquals("Jane Doe", data.targetDisplayName());
        assertFalse(data.hasRoundResult());
        assertTrue(data.rows().stream().allMatch(row -> row.getDelta() == 0));
        verify(standingsRepo, never()).findBySeasonAndRoundPosition(any(), anyInt());
    }

    @Test
    void currentInPlayRound_comparesCurrentRankingsAgainstStandings() {
        Season season = createSeason(20);
        Round round = createRound(5, false);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2)))
                .atRoundNumber(1)
                .build();
        Standings standings = Standings.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundPosition(5)
                .rankings(List.of(
                        new StandingsTeamRank(TeamRank.of("ARS", 3), zeroMetadata()),
                        new StandingsTeamRank(TeamRank.of("LIV", 2), zeroMetadata())))
                .build();

        stubSeasonAndRound(season, round);
        when(userRepo.findByPublicId(PublicId.create(publicId))).thenReturn(Optional.of(createUser()));
        when(seasonPredictionSupport.findPrediction(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 5)).thenReturn(Optional.of(standings));

        var query = new GetPublicPredictionQuery(publicId, seasonId, null);
        Either<?, PublicPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        PublicPredictionViewData data = result.get();
        assertFalse(data.hasRoundResult());
        var arsRow =
                data.rows().stream().filter(r -> r.getTeamCode().equals("ARS")).findFirst().orElseThrow();
        assertEquals(1, arsRow.getPosition());
        assertEquals(3, arsRow.getActualPosition());
        assertEquals(2, arsRow.getDelta());
        var livRow =
                data.rows().stream().filter(r -> r.getTeamCode().equals("LIV")).findFirst().orElseThrow();
        assertEquals(0, livRow.getDelta());
    }

    @Test
    void historicalScoredRound_mapsResultTeamRanksDirectly() {
        Season season = createSeason(20);
        Round round = createRound(10, false);
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
                .rankings(List.of(
                        new ResultTeamRank(TeamRank.of("ARS", 1), 1, 0),
                        new ResultTeamRank(TeamRank.of("LIV", 2), 5, 3)))
                .totalScore(197)
                .build();

        stubSeasonAndRound(season, round);
        when(userRepo.findByPublicId(PublicId.create(publicId))).thenReturn(Optional.of(createUser()));
        when(seasonPredictionSupport.findPrediction(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundResultRepo.findByUserAndRound(userId, 8)).thenReturn(Optional.of(roundResult));

        var query = new GetPublicPredictionQuery(publicId, seasonId, 8);
        Either<?, PublicPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        PublicPredictionViewData data = result.get();
        assertTrue(data.hasRoundResult());
        assertEquals(8, data.viewingRound());
        assertEquals(197, data.totalScore());
        assertEquals(3, data.totalHits());
        assertEquals(1, data.zeroesCount());
        var livRow =
                data.rows().stream().filter(r -> r.getTeamCode().equals("LIV")).findFirst().orElseThrow();
        assertEquals(5, livRow.getActualPosition());
        assertEquals(3, livRow.getDelta());
    }

    @Test
    void requestedRoundBelowUserAtRoundNumber_clampsToMinRound() {
        Season season = createSeason(20);
        Round round = createRound(10, false);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(baselineRankings)
                .atRoundNumber(6)
                .build();
        RoundResult roundResult = RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(UUID.randomUUID())
                .rankings(List.of(new ResultTeamRank(TeamRank.of("ARS", 1), 1, 0)))
                .totalScore(200)
                .build();

        stubSeasonAndRound(season, round);
        when(userRepo.findByPublicId(PublicId.create(publicId))).thenReturn(Optional.of(createUser()));
        when(seasonPredictionSupport.findPrediction(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundResultRepo.findByUserAndRound(userId, 6)).thenReturn(Optional.of(roundResult));

        // Requested round 2 is before this user's atRoundNumber (6) — clamp up to 6, not down.
        var query = new GetPublicPredictionQuery(publicId, seasonId, 2);
        Either<?, PublicPredictionViewData> result = useCase.execute(query);

        assertTrue(result.isRight());
        assertEquals(6, result.get().viewingRound());
    }

    private static StandingsMetadata zeroMetadata() {
        return StandingsMetadata.builder()
                .played(0)
                .won(0)
                .drawn(0)
                .lost(0)
                .points(0)
                .gf(0)
                .ga(0)
                .gd(0)
                .build();
    }
}

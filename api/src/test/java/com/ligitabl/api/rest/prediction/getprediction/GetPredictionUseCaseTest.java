package com.ligitabl.api.rest.prediction.getprediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

@ExtendWith(MockitoExtension.class)
class GetPredictionUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private SeasonPredictionRepo predictionRepo;

    @Mock
    private StandingsRepo standingsRepo;

    @Mock
    private PredictionRankEnricher rankEnricher;

    @Mock
    private Clock clock;

    private GetPredictionUseCase useCase;

    private UUID seasonId;
    private UUID roundId;
    private UUID userId;

    private Season season;
    private Round round;

    @BeforeEach
    void setup() {
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        userId = UUID.randomUUID();

        season = Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .completed(false)
                .initialRankings(List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2)))
                .build();

        round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(1)
                .name("Round 1")
                .slug("round-1")
                .finalized(false)
                .build();

        useCase = new GetPredictionUseCase(
                competitionDefaults,
                seasonRepo,
                roundRepo,
                new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults),
                predictionRepo,
                standingsRepo,
                rankEnricher,
                clock);
    }

    @Test
    void returns_user_prediction_when_present() {
        var prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(List.of(TeamRank.of("ARS", 1)))
                .initialRankings(List.of(TeamRank.of("ARS", 1)))
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(rankEnricher.enrich(any())).thenReturn(List.of());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().rankingSource()).isEqualTo(RankingSource.USER_PREDICTION);
        verify(predictionRepo).findByUserAndSeason(userId, seasonId);
    }

    @Test
    void falls_back_to_standings_when_no_prediction() {
        var standings = Standings.builder()
                .seasonId(seasonId)
                .roundPosition(1)
                .rankings(List.of(StandingsTeamRank.builder()
                        .ranking(TeamRank.of("ARS", 1))
                        .metadata(StandingsMetadata.builder()
                                .played(0)
                                .won(0)
                                .drawn(0)
                                .lost(0)
                                .points(0)
                                .gf(0)
                                .ga(0)
                                .gd(0)
                                .build())
                        .build()))
                .finalised(false)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).thenReturn(Optional.of(standings));
        when(rankEnricher.enrich(any())).thenReturn(List.of());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().rankingSource()).isEqualTo(RankingSource.CURRENT_ROUND_STANDINGS);
    }

    @Test
    void falls_back_to_baseline_when_no_prediction_and_no_standings() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).thenReturn(Optional.empty());
        when(rankEnricher.enrich(any())).thenReturn(List.of());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().rankingSource()).isEqualTo(RankingSource.SEASON_BASELINE);
    }

    @Test
    void returns_error_when_baseline_missing() {
        Season noBaselineSeason = Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .completed(false)
                .initialRankings(List.of())
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(noBaselineSeason));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).thenReturn(Optional.empty());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetPredictionError.BaselineRankingsMissing.class);
    }

    @Test
    void returns_error_when_round_missing_for_anonymous() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.empty());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(null);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetPredictionError.CurrentRoundNotFound.class);
        verifyNoInteractions(predictionRepo);
    }

    @Test
    void returns_error_when_round_missing_for_user() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.empty());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetPredictionError.CurrentRoundNotFound.class);
        verifyNoInteractions(predictionRepo);
    }

    @Test
    void returns_error_when_season_has_no_current_round_for_user() {
        Season noRoundSeason = Season.builder()
                .id(seasonId)
                .completed(false)
                .initialRankings(List.of(TeamRank.of("ARS", 1)))
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(noRoundSeason));

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetPredictionError.HasNoCurrentRound.class);
        verifyNoInteractions(predictionRepo);
    }

    @Test
    void returns_error_when_season_not_found() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(GetPredictionError.NotFound.class);
        verifyNoInteractions(roundRepo, predictionRepo, standingsRepo);
    }

    @Test
    void swap_status_blocked_when_season_completed() {
        Season completedSeason = Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .completed(true)
                .initialRankings(List.of(TeamRank.of("ARS", 1)))
                .build();

        var prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(List.of(TeamRank.of("ARS", 1)))
                .initialRankings(List.of(TeamRank.of("ARS", 1)))
                .lastSwapAt(TestCalendar.MID_SEASON)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(completedSeason));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(rankEnricher.enrich(any())).thenReturn(List.of());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().swapStatus().canSwap()).isFalse();
        assertThat(result.get().swapStatus().blockedReason()).isEqualTo("SEASON_COMPLETED");
    }

    @Test
    void swap_status_blocked_when_cooldown_active() {
        Instant now = TestCalendar.MID_SEASON;

        var prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(List.of(TeamRank.of("ARS", 1)))
                .initialRankings(List.of(TeamRank.of("ARS", 1)))
                .lastSwapAt(now.minus(Duration.ofHours(23)))
                .build();

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(rankEnricher.enrich(any())).thenReturn(List.of());

        Either<GetPredictionError, GetPredictionResult> result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().swapStatus().canSwap()).isFalse();
        assertThat(result.get().swapStatus().blockedReason()).isEqualTo("COOLDOWN_ACTIVE");
        assertThat(result.get().swapStatus().nextSwapAt()).isEqualTo(now.plus(Duration.ofHours(1)));
    }
}

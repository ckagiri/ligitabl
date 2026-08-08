package com.ligitabl.api.rest.finaltable.scorefinaltable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.service.FinalTableScorer;
import com.ligitabl.model.domain.service.ScoringEngine;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.StandingsRepo;

@ExtendWith(MockitoExtension.class)
class ScoreFinalTablePredictionsUseCaseTest {

    private static final int MAX_ROUNDS = 38;
    /** 4 teams: max_hit_points = 2 * (4/2)^2 = 8. */
    private static final int MAX_HIT_POINTS = 8;

    @Mock
    private FinalTablePredictionRepo predictionRepo;

    @Mock
    private StandingsRepo standingsRepo;

    private final Instant now = TestCalendar.MID_SEASON;
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private ScoreFinalTablePredictionsUseCase useCase;
    private UUID seasonId;
    private Season season;

    private static final List<String> ACTUAL_ORDER = List.of("ARS", "LIV", "MCI", "CHE");

    @BeforeEach
    void setUp() {
        seasonId = UUID.randomUUID();
        season = Season.builder()
                .id(seasonId)
                .maxRounds(MAX_ROUNDS)
                .maxHitPoints(MAX_HIT_POINTS)
                .build();

        useCase = new ScoreFinalTablePredictionsUseCase(
                predictionRepo, standingsRepo, new FinalTableScorer(new ScoringEngine()), clock);
    }

    private static Standings standings(int roundPosition, List<String> order) {
        List<StandingsTeamRank> ranks = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            ranks.add(new StandingsTeamRank(
                    TeamRank.of(order.get(i), i + 1),
                    StandingsMetadata.builder().build()));
        }
        return Standings.create(UUID.randomUUID(), roundPosition, ranks);
    }

    private FinalTablePrediction row(List<String> predictedOrder) {
        List<TeamRank> rankings = new ArrayList<>();
        for (int i = 0; i < predictedOrder.size(); i++) {
            rankings.add(TeamRank.of(predictedOrder.get(i), i + 1));
        }
        return FinalTablePrediction.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .seasonId(seasonId)
                .rankings(rankings)
                .settledAt(now.minusSeconds(86_400))
                .build();
    }

    private void stubFinalRoundStandings() {
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, MAX_ROUNDS))
                .thenReturn(Optional.of(standings(MAX_ROUNDS, ACTUAL_ORDER)));
    }

    private void stubEchoingSave() {
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void scoresAnExactTableToTheMaximum() {
        FinalTablePrediction exact = row(ACTUAL_ORDER);
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(exact));
        stubEchoingSave();

        var summary = useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(summary.scored()).isEqualTo(1);
        assertThat(exact.getBaseScore()).isEqualTo(MAX_HIT_POINTS);
        assertThat(exact.getZeroesCount()).isEqualTo(4);
        assertThat(exact.getBonusPoints()).isEqualTo(40);
        assertThat(exact.getTotalScore()).isEqualTo(MAX_HIT_POINTS + 40);
        assertThat(exact.getScoredAt()).isEqualTo(now);
        assertThat(exact.isScored()).isTrue();
        assertThat(exact.getResultRankings()).hasSize(4);
    }

    @Test
    void doesNotDisturbThePredictionOrItsSettleTime() {
        // Scoring writes only the result columns: the table and the tiebreak key are history.
        FinalTablePrediction prediction = row(List.of("CHE", "MCI", "LIV", "ARS"));
        Instant settledAt = prediction.getSettledAt();
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(prediction));
        stubEchoingSave();

        useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(prediction.getSettledAt()).isEqualTo(settledAt);
        assertThat(prediction.getRankings()).hasSize(4);
        assertThat(prediction.getRankings().get(0).getCode()).isEqualTo("CHE");
    }

    @Test
    void skipsAlreadyScoredRowsUnlessRecomputing() {
        FinalTablePrediction alreadyScored = row(ACTUAL_ORDER);
        alreadyScored.setScoredAt(now.minusSeconds(3600));
        alreadyScored.setTotalScore(999);
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(alreadyScored));

        var summary = useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(summary.scored()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        verify(predictionRepo, never()).save(any());
        assertThat(alreadyScored.getTotalScore()).isEqualTo(999);
    }

    @Test
    void recomputeOverwritesAnAlreadyScoredRow() {
        FinalTablePrediction alreadyScored = row(ACTUAL_ORDER);
        alreadyScored.setScoredAt(now.minusSeconds(3600));
        alreadyScored.setTotalScore(999);
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(alreadyScored));
        stubEchoingSave();

        var summary = useCase.execute(season, StandingsSource.FINAL_ROUND, true);

        assertThat(summary.scored()).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
        assertThat(alreadyScored.getTotalScore()).isEqualTo(MAX_HIT_POINTS + 40);
        assertThat(alreadyScored.getScoredAt()).isEqualTo(now);
    }

    @Test
    void isIdempotentAcrossRepeatedRuns() {
        FinalTablePrediction prediction = row(ACTUAL_ORDER);
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(prediction));
        stubEchoingSave();

        useCase.execute(season, StandingsSource.FINAL_ROUND, false);
        int firstScore = prediction.getTotalScore();
        var second = useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(second.scored()).isZero();
        assertThat(prediction.getTotalScore()).isEqualTo(firstScore);
    }

    @Test
    void aRowWithAnUnknownTeamFailsAloneAndDoesNotStopThePass() {
        // ScoringEngine throws when a predicted team is absent from the standings. One bad row must
        // not cost a whole season its scoring.
        FinalTablePrediction broken = row(List.of("ARS", "LIV", "MCI", "GONE"));
        FinalTablePrediction good = row(ACTUAL_ORDER);
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(broken, good));
        stubEchoingSave();

        var summary = useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.scored()).isEqualTo(1);
        assertThat(broken.isScored()).isFalse();
        assertThat(good.isScored()).isTrue();
    }

    @Test
    void aFailedRowIsNeverMarkedScored() {
        // scoredAt is the reveal predicate, so a row whose numbers failed must not read as revealed.
        FinalTablePrediction broken = row(List.of("ARS", "LIV", "MCI", "GONE"));
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(broken));

        useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(broken.getScoredAt()).isNull();
        assertThat(broken.getTotalScore()).isNull();
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void finalRoundAndCurrentReadDifferentStandings() {
        FinalTablePrediction prediction = row(ACTUAL_ORDER);
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(prediction));
        stubEchoingSave();

        // FINAL_ROUND asks for the last round's standings specifically.
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, MAX_ROUNDS))
                .thenReturn(Optional.of(standings(MAX_ROUNDS, ACTUAL_ORDER)));
        useCase.execute(season, StandingsSource.FINAL_ROUND, true);
        verify(standingsRepo).findBySeasonAndRoundPosition(seasonId, MAX_ROUNDS);
        verify(standingsRepo, never()).findLatestBySeason(any());

        // CURRENT asks for whatever is latest — a mid-season round, in the dev-preview case.
        reset(standingsRepo);
        when(standingsRepo.findLatestBySeason(seasonId)).thenReturn(Optional.of(standings(7, ACTUAL_ORDER)));
        useCase.execute(season, StandingsSource.CURRENT, true);
        verify(standingsRepo).findLatestBySeason(seasonId);
        verify(standingsRepo, never()).findBySeasonAndRoundPosition(any(), anyInt());
    }

    @Test
    void scoresNothingWhenStandingsAreMissing() {
        when(standingsRepo.findBySeasonAndRoundPosition(seasonId, MAX_ROUNDS)).thenReturn(Optional.empty());

        var summary = useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(summary.total()).isZero();
        verify(predictionRepo, never()).findBySeason(any());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void reportsCountsAcrossAMixedSeason() {
        FinalTablePrediction fresh = row(ACTUAL_ORDER);
        FinalTablePrediction done = row(ACTUAL_ORDER);
        done.setScoredAt(now.minusSeconds(3600));
        FinalTablePrediction broken = row(List.of("ARS", "LIV", "MCI", "GONE"));
        stubFinalRoundStandings();
        when(predictionRepo.findBySeason(seasonId)).thenReturn(List.of(fresh, done, broken));
        stubEchoingSave();

        var summary = useCase.execute(season, StandingsSource.FINAL_ROUND, false);

        assertThat(summary.scored()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(3);
    }
}

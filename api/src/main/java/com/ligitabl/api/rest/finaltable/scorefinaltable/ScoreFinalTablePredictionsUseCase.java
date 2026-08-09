package com.ligitabl.api.rest.finaltable.scorefinaltable;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.service.FinalTableScorer;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scores every Final Table prediction for a season, once, against the final standings.
 *
 * <p>Scoring is per-row fault-isolated: a single malformed prediction cannot fail the
 * rest of the season.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScoreFinalTablePredictionsUseCase {

    private final FinalTablePredictionRepo predictionRepo;
    private final StandingsRepo standingsRepo;
    private final FinalTableScorer scorer;
    private final Clock clock;

    /**
     * @param scored rows written this pass
     * @param skipped rows already scored and left alone (only when {@code recompute} is false)
     * @param failed rows whose own scoring threw; logged and stepped over
     */
    public record ScoringSummary(int scored, int skipped, int failed) {
        public int total() {
            return scored + skipped + failed;
        }
    }

    public ScoringSummary execute(Season season, StandingsSource source, boolean recompute) {
        List<StandingsTeamRank> standings = resolveStandings(season, source);
        if (standings.isEmpty()) {
            log.warn(
                    "[FINAL_TABLE_SCORING] season={} source={} no standings available, nothing scored",
                    season.getId(),
                    source);
            return new ScoringSummary(0, 0, 0);
        }

        List<FinalTablePrediction> rows = predictionRepo.findBySeason(season.getId());
        Instant now = clock.instant();
        int scored = 0;
        int skipped = 0;
        int failed = 0;

        for (FinalTablePrediction row : rows) {
            if (row.isScored() && !recompute) {
                skipped++;
                continue;
            }
            try {
                scoreRow(row, standings, season.getMaxHitPoints(), now);
                scored++;
            } catch (Exception e) {
                // ScoringEngine throws when a predicted team is absent from the standings, and again
                // if the hit total exceeds maxHitPoints. Neither may fail a whole season's run.
                failed++;
                log.error(
                        "[FINAL_TABLE_SCORING_ROW_FAILED] season={} prediction={} user={}: {}",
                        season.getId(),
                        row.getId(),
                        row.getUserId(),
                        e.getMessage(),
                        e);
            }
        }

        log.info(
                "[FINAL_TABLE_SCORING] season={} source={} recompute={} scored={} skipped={} failed={}",
                season.getId(),
                source,
                recompute,
                scored,
                skipped,
                failed);

        return new ScoringSummary(scored, skipped, failed);
    }

    private void scoreRow(FinalTablePrediction row, List<StandingsTeamRank> standings, int maxHitPoints, Instant now) {
        var score = scorer.score(row.getRankings(), standings, maxHitPoints);

        row.setResultRankings(score.resultRankings());
        row.setBaseScore(score.baseScore());
        row.setZeroesCount(score.zeroesCount());
        row.setBonusPoints(score.bonusPoints());
        row.setChampionBonus(score.championBonus());
        row.setTotalScore(score.totalScore());
        row.setScoredAt(now);

        predictionRepo.save(row);
    }

    private List<StandingsTeamRank> resolveStandings(Season season, StandingsSource source) {
        Optional<Standings> standings =
                switch (source) {
                    case FINAL_ROUND -> standingsRepo.findBySeasonAndRoundPosition(
                            season.getId(), season.getMaxRounds());
                    case CURRENT -> standingsRepo.findLatestBySeason(season.getId());
                };

        return standings.map(Standings::getRankings).orElse(List.of());
    }
}

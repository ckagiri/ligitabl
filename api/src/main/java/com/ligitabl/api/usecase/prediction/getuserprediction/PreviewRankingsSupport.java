package com.ligitabl.api.usecase.prediction.getuserprediction;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.usecase.prediction.shared.RankingSource;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.Standings;
import com.ligitabl.model.domain.league.StandingsTeamRank;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;

/**
 * Default/starting rankings for a season and round — the same baseline shown to guests and
 * users without a prediction yet.
 */
@Component
@RequiredArgsConstructor
public class PreviewRankingsSupport {

    private final SeasonRepo seasonRepo;
    private final StandingsRepo standingsRepo;

    public record RankingsWithSource(RankingSource source, List<TeamRank> rankings) {}

    /**
     * Current standings, for lightweight previews (e.g. the homepage) that don't need the source.
     */
    public List<TeamRank> getPreviewRankings(UUID seasonId, int currentRound) {
        for (int round = currentRound; round >= 1; round--) {
            var standings = standingsRepo.findBySeasonAndRoundPosition(seasonId, round);
            if (standings.isPresent()) {
                return convertStandingsRankingsToTeamRankings(standings.get());
            }
        }
        return getSeasonBaselineRankings(seasonId).rankings();
    }

    /**
     * Fallback for users without a prediction: standings from currentRound - 2, for contrast when
     * deciding moves. GW5 → GW3, GW3 → GW1, GW2/GW1 (or no standings) → season baseline.
     */
    public RankingsWithSource getPreviousRoundRankings(UUID seasonId, int currentRound) {
        if (currentRound < 3) {
            return getSeasonBaselineRankings(seasonId);
        }

        var roundStandings = standingsRepo.findBySeasonAndRoundPosition(seasonId, currentRound - 2);
        if (roundStandings.isEmpty()) {
            return getSeasonBaselineRankings(seasonId);
        }

        return new RankingsWithSource(
                RankingSource.PREVIOUS_ROUND_STANDINGS, convertStandingsRankingsToTeamRankings(roundStandings.get()));
    }

    private List<TeamRank> convertStandingsRankingsToTeamRankings(Standings standings) {
        return standings.getRankings().stream()
                .map(StandingsTeamRank::getRanking)
                .toList();
    }

    /**
     * Get season baseline rankings — the shared starting point for all users.
     */
    private RankingsWithSource getSeasonBaselineRankings(UUID seasonId) {
        var baseline = seasonRepo
                .findById(seasonId)
                .map(Season::getInitialRankings)
                .orElseThrow(
                        () -> new IllegalStateException("Season baseline rankings not found for season: " + seasonId));

        return new RankingsWithSource(RankingSource.SEASON_BASELINE, baseline);
    }
}

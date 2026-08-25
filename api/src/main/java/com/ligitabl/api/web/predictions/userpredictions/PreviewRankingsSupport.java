package com.ligitabl.api.web.predictions.userpredictions;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
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
     * Get previous round standings as fallback for users without a prediction.
     *
     * Always uses currentRound - 2, giving users contrast to help decide where to move teams.
     * Falls back to season baseline when currentRound < 3 (GW1/GW2) or standings unavailable.
     *
     * GW5 → GW3, GW3 → GW1, GW2/GW1 → season baseline.
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

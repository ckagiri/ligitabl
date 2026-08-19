package com.ligitabl.api.rest.finaltable.leaderboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.rest.finaltable.shared.FinalTableError;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.FinalTableLeaderboardEntry;
import com.ligitabl.model.domain.service.FinalTableScorer;
import com.ligitabl.model.repo.FinalTablePredictionRepo;

import lombok.RequiredArgsConstructor;

/** The Final Table leaderboard: revealed only once at least one row in the season has been scored. */
@Component
@RequiredArgsConstructor
public class GetFinalTableLeaderboardUseCase {

    private final FinalTablePredictionRepo predictionRepo;
    private final FinalTableSupport finalTableSupport;

    /**
     * @param revealed false while nothing is scored — the page shows a "results at the end of the
     *     season" state rather than an empty table
     * @param entries the requested page, already ranked
     * @param displayPositions per-entry position with genuine ties collapsed (=4, =4, 6)
     * @param userEntry the viewer's own standing, or null
     * @param totalEntries scored rows in the season, for pagination
     * @param maxScore the best score obtainable this season — distance ceiling, the per-club bonus
     *     and the champion bonus, so a ranked number can be read against something. Derived from
     *     the season rather than assuming 20 clubs / 425 points
     */
    public record FinalTableLeaderboardData(
            boolean revealed,
            List<FinalTableLeaderboardEntry> entries,
            List<String> displayPositions,
            FinalTableLeaderboardEntry userEntry,
            int totalEntries,
            int totalPlayers,
            /** For building per-player public links; the view has no Season to ask. */
            String seasonShorthand,
            Integer maxScore) {}

    public Either<FinalTableError, FinalTableLeaderboardData> execute(UUID userId, int offset, int limit) {
        return finalTableSupport.activeSeason().map(season -> {
            UUID seasonId = season.getId();
            String shorthand =
                    season.getSlug() == null ? null : season.getSlug().toShorthand();
            int scored = predictionRepo.countScoredBySeason(seasonId);
            int totalPlayers = predictionRepo.countBySeason(seasonId);

            int teamCount = season.getInitialRankings() == null
                    ? 0
                    : season.getInitialRankings().size();
            Integer maxScore = teamCount == 0 ? null : FinalTableScorer.maxScore(season.getMaxHitPoints(), teamCount);

            if (scored == 0) {
                return new FinalTableLeaderboardData(
                        false, List.of(), List.of(), null, 0, totalPlayers, shorthand, maxScore);
            }

            List<FinalTableLeaderboardEntry> entries = predictionRepo.leaderboard(seasonId, offset, limit);
            FinalTableLeaderboardEntry userEntry = userId == null
                    ? null
                    : predictionRepo.userStanding(seasonId, userId).orElse(null);

            return new FinalTableLeaderboardData(
                    true, entries, displayPositions(entries), userEntry, scored, totalPlayers, shorthand, maxScore);
        });
    }

    /**
     * <p>⚠️ A tie spanning a page boundary cannot be detected from within the page: the first row of
     * page 2 shows its own number even if it ties the last row of page 1. Accepted — the alternative
     * is a window function whose positions no longer match the offsets.
     */
    private List<String> displayPositions(List<FinalTableLeaderboardEntry> entries) {
        List<String> positions = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            FinalTableLeaderboardEntry entry = entries.get(i);
            boolean tiesPrevious = i > 0 && tied(entries.get(i - 1), entry);
            boolean tiesNext = i + 1 < entries.size() && tied(entry, entries.get(i + 1));

            if (tiesPrevious) {
                // Repeat the position already shown for this tie group.
                positions.add(positions.get(i - 1));
            } else {
                positions.add((tiesNext ? "=" : "") + entry.position());
            }
        }

        return positions;
    }

    private boolean tied(FinalTableLeaderboardEntry a, FinalTableLeaderboardEntry b) {
        return a.totalScore() == b.totalScore()
                && a.zeroesCount() == b.zeroesCount()
                && java.util.Objects.equals(a.settledAt(), b.settledAt());
    }
}

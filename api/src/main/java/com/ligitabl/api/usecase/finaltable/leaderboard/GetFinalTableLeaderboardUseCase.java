package com.ligitabl.api.usecase.finaltable.leaderboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.finaltable.shared.FinalTableError;
import com.ligitabl.api.usecase.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.usecase.shared.user.DisplayNames;
import com.ligitabl.model.domain.prediction.FinalTableScorer;
import com.ligitabl.model.query.FinalTableEntrant;
import com.ligitabl.model.query.FinalTableLeaderboardEntry;
import com.ligitabl.model.repo.FinalTablePredictionRepo;

import lombok.RequiredArgsConstructor;

/** The Final Table leaderboard: revealed only once at least one row in the season has been scored. */
@Component
@RequiredArgsConstructor
public class GetFinalTableLeaderboardUseCase {

    private final FinalTablePredictionRepo predictionRepo;
    private final FinalTableSupport finalTableSupport;

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
                // Names are cleaned here rather than in the view: display_name is nullable, and an
                // uncleaned one would render as "null" or paint markup into the page.
                List<FinalTableEntrant> entrants = predictionRepo.entrantsBySeason(seasonId).stream()
                        .map(e -> new FinalTableEntrant(
                                e.publicId(), DisplayNames.cleanOr(e.displayName(), "This player")))
                        .toList();

                return new FinalTableLeaderboardData(
                        false, List.of(), List.of(), null, 0, totalPlayers, shorthand, maxScore, entrants);
            }

            List<FinalTableLeaderboardEntry> entries = predictionRepo.leaderboard(seasonId, offset, limit);
            FinalTableLeaderboardEntry userEntry = userId == null
                    ? null
                    : predictionRepo.userStanding(seasonId, userId).orElse(null);

            // No entrants list once revealed: `entries` is the same people, ranked and scored.
            return new FinalTableLeaderboardData(
                    true,
                    entries,
                    displayPositions(entries),
                    userEntry,
                    scored,
                    totalPlayers,
                    shorthand,
                    maxScore,
                    List.of());
        });
    }

    /**
     * ⚠️ A tie spanning a page boundary goes undetected: page 2's first row shows its own number
     * even if it ties page 1's last. A window function would fix it but break the match with offsets.
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

package com.ligitabl.api.rest.finaltable.getfinaltable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;

/**
 * Everything the Final Table page renders, in one object.
 *
 * @param rankings the table to display, in position order — the user's own, or the season baseline
 * @param teamsByCode crest/name lookup for the rows
 * @param entryOpen whether the table may still be edited (round 1 open, season not completed)
 * @param hasEntry whether a row exists; drives the Save button's enablement on an empty batch
 * @param revealed whether the result may be shown — {@code prediction.isScored()}, not season
 *     completion, so a season marked complete before scoring runs still reads as waiting
 * @param swaps recorded swaps; not rendered pre-lock, kept for the result view
 * @param swapCount denormalised count, shown on the leaderboard
 * @param settledAt the tiebreak key
 * @param resultRankings per-team predicted vs actual, non-null only once revealed
 * @param baseScore distance score, null until revealed
 * @param zeroesCount exact positions, null until revealed
 * @param bonusPoints zeroes * 10, null until revealed
 * @param totalScore base + bonus, null until revealed
 * @param roundStatus round 1's resolved status, for the locked-state copy
 * @param isGuest true when nobody is signed in — read-only teaser with a sign-up CTA
 * @param devPreviewEnabled renders the dev score/clear controls
 */
public record FinalTableViewData(
        List<TeamRank> rankings,
        Map<String, Team> teamsByCode,
        boolean entryOpen,
        boolean hasEntry,
        boolean revealed,
        List<SwapChange> swaps,
        int swapCount,
        Instant settledAt,
        List<ResultTeamRank> resultRankings,
        Integer baseScore,
        Integer zeroesCount,
        Integer bonusPoints,
        Integer totalScore,
        String roundStatus,
        String shareUrl,
        String shareText,
        boolean isGuest,
        boolean devPreviewEnabled) {

    /** The order the client echoes back as its checksum on save. */
    public List<String> expectedOrder() {
        return TeamRank.inPositionOrder(rankings).stream()
                .map(TeamRank::getCode)
                .toList();
    }
}

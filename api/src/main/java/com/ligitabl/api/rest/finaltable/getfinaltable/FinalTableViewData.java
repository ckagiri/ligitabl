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
 * @param teamsByCode name lookup for the server-rendered result table
 * @param rowsJson the same rows as JSON for the Alpine component — a swap has to reorder rows
 *     client-side, so it needs code and display name without another round trip
 * @param zonesJson qualification zones as inclusive position ranges, for the row bands
 * @param competitionName e.g. "Premier League", for the banner and the share card
 * @param seasonLabel short season form, e.g. "26/27"
 * @param teamCount clubs in the table, shown in the banner
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
 * @param shareRowsJson rows for the share canvas, with actual/hit once revealed
 * @param devPreviewEnabled renders the dev score/clear controls
 * @param liveProgress the third state — locked, not yet scored, but current standings exist, so
 *     the table can show how it is tracking. Distinct from {@code revealed}: no score of any kind
 *     is computed or shown, and nothing is persisted by rendering it
 * @param liveRowsJson rows with each team's current standings position, {@code "[]"} unless
 *     {@code liveProgress}. Carries no score and deliberately no aggregate — see
 *     {@code FinalTableRowsJson.liveRows}
 * @param ownerName the player's display name for the share card, cleaned via {@code DisplayNames}
 *     and null when nothing legible survives. Never an email: the card is an image people post
 *     publicly, so the caller falls back to generic wording rather than to any identifier
 * @param maxHitPoints the distance ceiling a base score counts down from, so "90" can be shown as
 *     "90 / 200". From the season, never assumed
 * @param maxScore the best obtainable total — {@code maxHitPoints} plus the per-club bonus
 * @param totalHits places lost across every club; what separates {@code baseScore} from the
 *     ceiling. Null until revealed
 */
public record FinalTableViewData(
        List<TeamRank> rankings,
        Map<String, Team> teamsByCode,
        String rowsJson,
        String zonesJson,
        String competitionName,
        String seasonLabel,
        int teamCount,
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
        String shareRowsJson,
        boolean isGuest,
        boolean devPreviewEnabled,
        boolean liveProgress,
        String liveRowsJson,
        String ownerName,
        Integer maxHitPoints,
        Integer maxScore,
        Integer totalHits) {

    /** The order the client echoes back as its checksum on save. */
    public List<String> expectedOrder() {
        return TeamRank.inPositionOrder(rankings).stream()
                .map(TeamRank::getCode)
                .toList();
    }
}

package com.ligitabl.api.rest.finaltable.shared;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The Final Table Predictor's shared predicates, in one place so the save path and the read paths
 * cannot drift apart on the question of whether the game is open.
 *
 * <p>Deliberately not {@code SwapHelper}: that class's season/round/cooldown logic is all about the
 * <em>current</em> round, and this game has none. Only {@code applySwap} is borrowed from it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalTableSupport {

    /** The game locks when round 1 stops being open, so this is the only round that matters. */
    private static final int LOCK_ROUND_POSITION = 1;

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RoundSupport roundSupport;

    /**
     * The season this game is played in, resolved but deliberately <em>not</em> judged for play
     * state.
     *
     * <p>Unlike {@code SwapHelper.getActiveSeason} this does not reject on
     * {@code isInPlay}/{@code isCompleted}. A completed season is not {@code IN_PLAY} by
     * construction, so rejecting here would shadow {@link #isEntryOpen} and report a locked table as
     * "season not in play" — the wrong answer for a game whose whole point is that it stays visible,
     * frozen, for nine months and then reveals a result. Whether the table may be touched is one
     * predicate, and it is {@link #isEntryOpen}.
     */
    public Either<FinalTableError, Season> activeSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<FinalTableError, Season>right)
                .orElseGet(() -> {
                    log.warn(
                            "No active season for competition {}, final table unavailable",
                            competitionDefaults.defaultCompetitionSlug());
                    return Either.left(new FinalTableError.SeasonNotFound(null));
                });
    }

    /**
     * Whether the table may still be created or edited: round 1 resolves to OPEN and the season is
     * not completed. Derived on read rather than stored, matching how the codebase treats round
     * state everywhere else — no scheduler, no lock column, and no way for a flag to disagree with
     * the round it describes.
     *
     * <p>Both the save and the create paths gate on this: a user arriving after GW1 with no row yet
     * must be refused too, not merely blocked from editing an existing one.
     */
    public boolean isEntryOpen(Season season) {
        return entryStatus(season) == RoundStatus.OPEN;
    }

    /**
     * The status behind {@link #isEntryOpen}, for error messages. COMPLETED when the season itself
     * is over, otherwise round 1's resolved status (UNKNOWN if there is no round 1 yet).
     *
     * <p>Uses {@code resolveJoinEligibilityStatus}, not {@code resolveStatus}: the latter
     * short-circuits to OPEN for a round with no matches loaded, <em>before</em>
     * {@code Round.computeStatus} can report FINALIZED — so a finalized round 1 with no match rows
     * would read as open and let someone enter a game that has already been scored. "May someone
     * still enter?" is the join-eligibility question, and the finalized check also skips the match
     * lookup entirely.
     */
    public RoundStatus entryStatus(Season season) {
        if (season == null || season.isCompleted()) {
            return RoundStatus.COMPLETED;
        }
        return roundSupport.resolveJoinEligibilityStatus(firstRound(season));
    }

    private Round firstRound(Season season) {
        List<Round> rounds = roundRepo.findBySeasonIdOrderByPosition(season.getId());
        if (rounds == null) {
            return null;
        }
        return rounds.stream()
                .filter(r -> r.getPosition() == LOCK_ROUND_POSITION)
                .findFirst()
                .orElse(null);
    }

    /**
     * The starting table for a first-time player: the season's shared baseline, the same list guests
     * see on the main prediction page.
     */
    public List<TeamRank> baselineRankings(Season season) {
        List<TeamRank> baseline = season == null ? null : season.getInitialRankings();
        return baseline == null ? List.of() : TeamRank.inPositionOrder(baseline);
    }

    /** The codes a swap may name — the same source {@code SwapHelper.validateTeams} uses. */
    public Set<String> validCodes(Season season) {
        List<TeamRank> baseline = season == null ? null : season.getInitialRankings();
        return baseline == null
                ? Set.of()
                : baseline.stream().map(TeamRank::getCode).collect(Collectors.toSet());
    }
}

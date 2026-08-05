package com.ligitabl.api.rest.round.shared;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoundSupport {

    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;

    /**
     * The round's live status (OPEN/LOCKED/COMPLETED/FINALIZED/ADVANCED), computed from its own
     * lifecycle flags and its current matches. RoundStatus.UNKNOWN if round is null. A round with
     * no matches loaded yet (not synced) reads as OPEN rather than {@link Round#computeStatus}'s
     * own empty-round default of COMPLETED — matches aren't in place to judge by yet.
     */
    public RoundStatus resolveStatus(Round round) {
        if (round == null) {
            return RoundStatus.UNKNOWN;
        }
        var matches = matchRepo.findByRoundId(round.getId());
        return (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : round.computeStatus(matches);
    }

    /**
     * Like {@link #resolveStatus} but with the round's own lifecycle flag taking precedence over
     * the no-matches default.
     *
     * <p>Needed because {@code resolveStatus} short-circuits to OPEN when a round has no matches
     * loaded, <em>before</em> {@link Round#computeStatus} gets a chance to report FINALIZED — so a
     * finalized round with no matches otherwise reads as open, and a join would be accepted into a
     * round that has already been scored.
     *
     * <p>Use this wherever the question is "may someone still join at this round?".
     * {@code resolveStatus} remains correct for display and for match-driven questions.
     */
    public RoundStatus resolveJoinEligibilityStatus(Round round) {
        if (round == null) {
            return RoundStatus.UNKNOWN;
        }
        return round.isFinalized() ? RoundStatus.FINALIZED : resolveStatus(round);
    }

    /** The default competition's current round status. */
    public RoundStatus currentRoundStatus() {
        return resolveStatus(resolveCurrentRound());
    }

    /** The default competition's current round. */
    public Round resolveCurrentRound() {
        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .fold(error -> null, HierarchyValidator.HierarchyContext::round);
    }

    /** The default competition's current round position, or 1 if unresolvable. */
    public int resolveCurrentRoundPosition() {
        Round round = resolveCurrentRound();
        return round != null ? round.getPosition() : 1;
    }

    /**
     * Resolves the current round for a specific season, for callers that already hold a Season
     * rather than assuming the default competition.
     */
    public Round resolveCurrentRound(Season season) {
        return season.getCurrentRoundId() == null
                ? null
                : roundRepo.findById(season.getCurrentRoundId()).orElse(null);
    }
}

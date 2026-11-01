package com.ligitabl.api.usecase.round.shared;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.domain.league.Season;
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
     * The round's live status from its lifecycle flags and current matches; UNKNOWN for null. A
     * round with no matches loaded yet reads as OPEN, not {@link Round#computeStatus}'s COMPLETED.
     */
    public RoundStatus resolveStatus(Round round) {
        if (round == null) {
            return RoundStatus.UNKNOWN;
        }
        var matches = matchRepo.findByRoundId(round.getId());
        return (matches == null || matches.isEmpty()) ? RoundStatus.OPEN : round.computeStatus(matches);
    }

    /**
     * Like {@link #resolveStatus}, but the round's lifecycle flag beats the no-matches default.
     * {@code resolveStatus} returns OPEN for a round with no matches before checking FINALIZED, so a
     * scored round could accept a join. Use this for "may someone still join at this round?".
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

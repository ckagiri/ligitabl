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
     * lifecycle flags and its current matches. RoundStatus.UNKNOWN if round is null.
     */
    public RoundStatus resolveStatus(Round round) {
        return round == null ? RoundStatus.UNKNOWN : round.computeStatus(matchRepo.findByRoundId(round.getId()));
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

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

    public boolean isEntryOpen(Season season) {
        return entryStatus(season) == RoundStatus.OPEN;
    }

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

    public List<TeamRank> baselineRankings(Season season) {
        List<TeamRank> baseline = season == null ? null : season.getInitialRankings();
        return baseline == null ? List.of() : TeamRank.inPositionOrder(baseline);
    }

    public Set<String> validCodes(Season season) {
        List<TeamRank> baseline = season == null ? null : season.getInitialRankings();
        return baseline == null
                ? Set.of()
                : baseline.stream().map(TeamRank::getCode).collect(Collectors.toSet());
    }
}

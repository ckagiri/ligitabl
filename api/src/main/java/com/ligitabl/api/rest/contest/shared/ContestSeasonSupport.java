package com.ligitabl.api.rest.contest.shared;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

/**
 * Season-lifecycle checks shared by the private-contest use cases and their controllers: whether
 * a contest belongs to a season that is no longer the competition's active one (read-only,
 * historical), and whether a contest's own final sprint is currently underway (leave/remove
 * locked, same boundary as the join-window-closed and renewal-timing checks).
 */
@Component
@RequiredArgsConstructor
public class ContestSeasonSupport {

    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;

    /** True once the contest's season is no longer the competition's current active season. */
    public boolean isPastSeason(Contest contest) {
        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null) return false;

        Season activeSeason =
                seasonRepo.findActiveSeason(season.getCompetitionId()).orElse(null);
        return activeSeason == null || !activeSeason.getId().equals(season.getId());
    }

    /** True once the current round has reached the start of the contest's own final sprint. */
    public boolean isFinalSprintUnderway(Contest contest, int currentRoundPosition) {
        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null) return false;

        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null || competition.getPhases() == null) return false;

        return PhaseRules.isFinalSprintUnderway(
                contest.getToRoundPosition(), currentRoundPosition, competition.getPhases());
    }
}

package com.ligitabl.api.rest.contest.shared;

import org.springframework.stereotype.Component;

import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.ContestJoinWindow;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContestSeasonSupport {

    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final RoundSupport roundSupport;

    public record SeasonGateStatus(boolean isPastSeason, boolean isJoinWindowClosed) {}

    /** True once the contest's season is no longer the competition's current active season. */
    public boolean isPastSeason(Contest contest) {
        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        return season == null ? false : isPastSeason(season);
    }

    /** True once the contest's own join window has closed — the same boundary joining itself uses. */
    public boolean isJoinWindowClosed(Contest contest) {
        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        return season == null ? false : isJoinWindowClosed(contest, season);
    }

    /**
     * Combines {@link #isPastSeason} and {@link #isJoinWindowClosed}, resolving the season once
     * instead of twice — use this instead of calling both separately when a caller needs both
     * (the common case: gate an action on "not past season and window still open").
     */
    public SeasonGateStatus resolveSeasonGateStatus(Contest contest) {
        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null) return new SeasonGateStatus(false, false);

        if (isPastSeason(season)) {
            return new SeasonGateStatus(true, false);
        }
        return new SeasonGateStatus(false, isJoinWindowClosed(contest, season));
    }

    private boolean isPastSeason(Season season) {
        Season activeSeason =
                seasonRepo.findActiveSeason(season.getCompetitionId()).orElse(null);
        return activeSeason == null || !activeSeason.getId().equals(season.getId());
    }

    private boolean isJoinWindowClosed(Contest contest, Season season) {
        Competition competition =
                competitionRepo.findById(season.getCompetitionId()).orElse(null);
        if (competition == null || competition.getPhases() == null) return false;

        Round currentRound = roundSupport.resolveCurrentRound(season);
        if (currentRound == null) return false;

        return ContestJoinWindow.isJoinWindowClosed(
                contest.getToRoundPosition(),
                currentRound,
                competition,
                () -> roundSupport.resolveStatus(currentRound));
    }
}

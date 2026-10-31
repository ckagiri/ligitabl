package com.ligitabl.api.usecase.shared.standings;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.league.Standings;
import com.ligitabl.model.domain.league.StandingsTeamRank;
import com.ligitabl.model.domain.league.Team;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.domain.league.standings.StandingsConverter;
import com.ligitabl.model.domain.league.standings.stats.Standing;
import com.ligitabl.model.domain.league.standings.table.StandingsCalculator;
import com.ligitabl.model.repo.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A round's standings from the season's stored teams and finished matches, optionally persisted.
 * For standings from an in-memory match list, call the domain {@link StandingsCalculator} directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoundStandingsCalculator {

    private final TeamRepo teamRepo;
    private final MatchRepo matchRepo;
    private final SeasonRepo seasonRepo;
    private final StandingsRepo standingsRepo;

    @Transactional(readOnly = true)
    public Either<StandingsError, List<StandingsTeamRank>> calculateRankings(UUID seasonId, int roundPosition) {

        log.debug("Calculating rankings: season={}, round={}", seasonId, roundPosition);

        return fetchTeamsAndMatches(seasonId, roundPosition)
                .flatMap(data -> calculateStandings(data.matches(), data.teams())
                        .map(standings -> StandingsConverter.convert(standings, data.teams())));
    }

    @Transactional
    public Either<StandingsError, Standings> calculateAndPersist(UUID seasonId, int roundPosition) {
        return calculateRankings(seasonId, roundPosition)
                .flatMap(Either.catching(
                        rankings -> {
                            Standings existing = standingsRepo
                                    .findBySeasonAndRoundPosition(seasonId, roundPosition)
                                    .orElse(null);

                            if (existing == null) {
                                return standingsRepo.save(Standings.builder()
                                        .seasonId(seasonId)
                                        .roundPosition(roundPosition)
                                        .rankings(rankings)
                                        .finalised(false)
                                        .build());
                            }

                            return standingsRepo.updateRankings(existing.getId(), rankings);
                        },
                        e -> {
                            log.error("Failed to persist standings: season={}, round={}", seasonId, roundPosition, e);
                            return new StandingsError.CalculationFailed(e.getMessage());
                        }));
    }

    private Either<StandingsError, StandingsData> fetchTeamsAndMatches(UUID seasonId, int roundPosition) {

        var season = seasonRepo.findById(seasonId).orElse(null);
        if (season == null) {
            return Either.left(new StandingsError.SeasonNotFound(seasonId));
        }

        var initialRankings = season.getInitialRankings();
        if (initialRankings == null || initialRankings.isEmpty()) {
            log.error("Season {} has no initialRankings!", seasonId);
            return Either.left(new StandingsError.NoInitialRankings(seasonId));
        }

        var teamCodes = initialRankings.stream().map(TeamRank::getCode).collect(Collectors.toSet());

        log.debug("Fetching {} teams for season {}: {}", teamCodes.size(), seasonId, teamCodes);

        var teams = teamRepo.findAllByCodes(teamCodes);
        if (teams.isEmpty()) {
            log.error("No teams found for codes: {}", teamCodes);
            return Either.left(new StandingsError.TeamsNotFound(teamCodes));
        }

        var finishedMatches = matchRepo.findFinishedMatchesUpToRoundWithTeams(seasonId, roundPosition);

        return Either.right(new StandingsData(teams, finishedMatches));
    }

    private Either<StandingsError, List<Standing>> calculateStandings(List<Match> finishedMatches, List<Team> teams) {
        try {
            var calculatedStandings = StandingsCalculator.calculate(teams, finishedMatches);
            log.debug("Calculated standings: {} teams", calculatedStandings.size());
            return Either.right(calculatedStandings);
        } catch (Exception e) {
            log.error("Standings calculation failed", e);
            return Either.left(new StandingsError.CalculationFailed(e.getMessage()));
        }
    }

    private record StandingsData(List<Team> teams, List<Match> matches) {}

    public sealed interface StandingsError {
        record SeasonNotFound(UUID seasonId) implements StandingsError {}

        record NoInitialRankings(UUID seasonId) implements StandingsError {}

        record TeamsNotFound(Set<String> codes) implements StandingsError {}

        record CalculationFailed(String message) implements StandingsError {}
    }
}

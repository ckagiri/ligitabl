package com.ligitabl.api.rest.standings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class StandingsEnricher {

    private final TeamRepo teamRepo;

    /**
     * Enriches standings with team info.
     * Returns empty list if standings has no rankings.
     * Handles missing teams gracefully by logging a warning.
     */
    public Either<UseCaseError, List<StandingsEntryDto>> enrichWithTeams(Standings standings) {
        try {
            if (standings == null || standings.getRankings().isEmpty()) {
                return Either.right(List.of());
            }

            // Extract all team codes from rankings
            Set<String> teamCodes = standings.getRankings().stream()
                    .map(rank -> rank.getRanking().getCode())
                    .collect(Collectors.toSet());

            // Bulk fetch teams
            List<Team> teams = teamRepo.findAllByCodes(teamCodes);
            Map<String, Team> teamsByCode =
                    teams.stream().collect(Collectors.toMap(Team::getCode, Function.identity()));

            // Log warning if any teams are missing
            if (teams.size() < teamCodes.size()) {
                Set<String> foundCodes = teams.stream().map(Team::getCode).collect(Collectors.toSet());
                Set<String> missingCodes = teamCodes.stream()
                        .filter(code -> !foundCodes.contains(code))
                        .collect(Collectors.toSet());
                log.warn("Teams not found for codes: {}", missingCodes);
            }

            return Either.right(StandingsEntryDto.listOf(standings, teamsByCode));
        } catch (Exception e) {
            log.error("Failed to enrich standings with teams", e);
            return Either.left(UseCaseErrors.fromException(e));
        }
    }
}

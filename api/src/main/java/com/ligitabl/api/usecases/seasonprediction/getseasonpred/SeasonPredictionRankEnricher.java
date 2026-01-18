package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeasonPredictionRankEnricher {

    private final TeamRepo teamRepo;

    public List<SeasonPredictionRankDto> enrich(List<TeamRank> rankings) {
        try {
            if (rankings == null || rankings.isEmpty()) {
                return List.of();
            }

            Set<String> teamCodes = rankings.stream().map(TeamRank::getCode).collect(Collectors.toSet());
            List<Team> teams = teamRepo.findAllByCodes(teamCodes);
            Map<String, Team> teamsByCode =
                    teams.stream().collect(Collectors.toMap(Team::getCode, Function.identity()));

            if (teams.size() < teamCodes.size()) {
                Set<String> foundCodes = teams.stream().map(Team::getCode).collect(Collectors.toSet());
                Set<String> missingCodes = teamCodes.stream()
                        .filter(code -> !foundCodes.contains(code))
                        .collect(Collectors.toSet());
                log.warn("Teams not found for codes: {}", missingCodes);
            }

            return SeasonPredictionRankDto.listOf(rankings, teamsByCode);
        } catch (Exception e) {
            log.error("Failed to enrich prediction rankings", e);
            return rankings == null
                    ? List.of()
                    : rankings.stream()
                            .map(rank -> SeasonPredictionRankDto.builder()
                                    .position(rank.getPosition())
                                    .teamCode(rank.getCode())
                                    .teamId(null)
                                    .teamName(rank.getCode())
                                    .teamShortName(rank.getCode())
                                    .teamSlug(rank.getCode())
                                    .teamTla(rank.getCode())
                                    .build())
                            .toList();
        }
    }
}

package com.ligitabl.api.usecases.shared;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchEnricher {

    private final TeamRepo teamRepo;

    public Either<UseCaseError, List<MatchDto>> enrichWithTeams(List<Match> matches) {
        return Either.catching(() -> {
            if (matches.isEmpty()) {
                return List.of();
            }

            // Get all unique team IDs
            Set<UUID> teamIds = matches.stream()
                    .flatMap(m -> Stream.of(m.getHomeTeamId(), m.getAwayTeamId()))
                    .collect(Collectors.toSet());

            // Fetch all teams at once
            List<Team> teams = teamRepo.findAllByIds(teamIds);
            Map<UUID, Team> teamsById = teams.stream()
                    .collect(Collectors.toMap(Team::getId, Function.identity()));

            return MatchDto.listOf(matches, teamsById);
        }, UseCaseErrors::fromException);
    }
}

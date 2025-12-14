package com.ligitabl.api.usecases.shared;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.match.MatchDto;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MatchEnricher {

    private final TeamRepo teamRepo;

    public Either<UseCaseError, List<MatchDto>> enrichWithTeams(List<Match> matches) {
        try {
            if (matches.isEmpty()) {
                return Either.right(List.of());
            }

            // Get all unique team IDs
            Set<UUID> teamIds = matches.stream()
                    .flatMap(m -> Stream.of(m.getHomeTeamId(), m.getAwayTeamId()))
                    .collect(Collectors.toSet());

            // Fetch all teams at once
            if (!teamIds.isEmpty()) {
                teamRepo.findAllByIds(teamIds);
            }

            return Either.right(MatchDto.listOf(matches));
        } catch (Exception e) {
            return Either.left(UseCaseErrors.fromException(e));
        }
    }
}

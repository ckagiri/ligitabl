package com.ligitabl.api.rest.standings;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ligitabl.api.web.shared.dto.FixtureDto;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundStandingsUseCase
        implements UseCase<GetDefaultRoundStandingsQuery, Either<UseCaseError, RoundStandingsResult>> {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundRepo roundRepo;
    private final StandingsRepo standingsRepo;
    private final StandingsEnricher standingsEnricher;
    private final MatchRepo matchRepo;

    @Override
    public Either<UseCaseError, RoundStandingsResult> execute(GetDefaultRoundStandingsQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, query.getRoundPosition())
                .flatMap(ctx -> resolveCurrentRound(ctx.season())
                        .flatMap(currentRound -> fetchStandings(ctx.season(), ctx.round())
                                .flatMap(standingsEnricher::enrichWithTeams)
                                .map(standings -> {
                                    boolean isCurrentRound =
                                            ctx.round().getPosition() == currentRound.getPosition();
                                    Map<String, List<FixtureDto>> nextFixtures = isCurrentRound
                                            ? buildNextFixtures(ctx.season().getId(), ctx.round().getPosition())
                                            : null;
                                    return new RoundStandingsResult(
                                            ctx.season().getId(),
                                            ctx.round().getPosition(),
                                            currentRound.getPosition(),
                                            ctx.season().getMaxRounds(),
                                            standings,
                                            nextFixtures);
                                })));
    }

    private Either<UseCaseError, Round> resolveCurrentRound(Season season) {
        if (season.getCurrentRoundId() == null) {
            return Either.left(UseCaseErrors.validation("Season has no current round"));
        }

        return requireFound(
                roundRepo.findById(season.getCurrentRoundId()),
                UseCaseErrors.notFound("Round", season.getCurrentRoundId()));
    }

    private Map<String, List<FixtureDto>> buildNextFixtures(java.util.UUID seasonId, int round) {
        var matchesByTeam = matchRepo.findBySeasonAndRound(seasonId, round);
        if (matchesByTeam == null || matchesByTeam.isEmpty()) {
            return Map.of();
        }
        return matchesByTeam.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .filter(Match::hasTeamsLoaded)
                                .map(m -> toFixtureDto(e.getKey(), m))
                                .collect(Collectors.toList())));
    }

    private FixtureDto toFixtureDto(String teamCode, Match match) {
        boolean isHome = match.getHomeTeam().getCode().equals(teamCode);
        String opponent = isHome ? match.getAwayTeam().getCode() : match.getHomeTeam().getCode();
        String status = normalizeStatus(match.getStatus());
        String result = resolveResult(match, isHome);
        return new FixtureDto(opponent, isHome, status, result);
    }

    private String normalizeStatus(com.ligitabl.model.domain.MatchStatus status) {
        if (status == null) return "SCHEDULED";
        return switch (status) {
            case LIVE, SUSPENDED -> "LIVE";
            case FINISHED -> "FINISHED";
            case POSTPONED -> "POSTPONED";
            default -> "SCHEDULED";
        };
    }

    private String resolveResult(Match match, boolean isHome) {
        return match.result().map(r -> {
            int home = r.homeGoals();
            int away = r.awayGoals();
            if (home == away) return "DRAW";
            boolean homeWon = home > away;
            return (isHome == homeWon) ? "WIN" : "LOSS";
        }).orElse(null);
    }

    private String getEffectiveCompetitionIdentifier(GetDefaultRoundStandingsQuery query) {
        return query.getCompetitionSlug() != null
                ? query.getCompetitionSlug()
                : competitionDefaults.defaultCompetitionSlug();
    }

    private Either<UseCaseError, Standings> fetchStandings(Season season, Round round) {
        return Either.catching(
                () -> standingsRepo
                        .findBySeasonAndRoundPosition(season.getId(), round.getPosition())
                        .orElseGet(() -> {
                            log.debug(
                                    "No standings found for season={} round={}, returning empty",
                                    season.getId(),
                                    round.getPosition());
                            return Standings.builder()
                                    .seasonId(season.getId())
                                    .roundPosition(round.getPosition())
                                    .rankings(List.of())
                                    .finalised(false)
                                    .build();
                        }),
                UseCaseErrors::fromException);
    }
}

package com.ligitabl.api.web.shared.fixtures;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.web.shared.dto.FixtureDto;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchResult;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Team;

/**
 * Shapes {@link Match} domain objects into the per-team {@link FixtureDto} lists sent to the
 * client — shared between the owner's own predictions page and the public prediction view.
 */
@Component
public class FixtureJsonMapper {

    public Map<String, List<FixtureDto>> buildFixtures(Map<String, List<Match>> matchesByTeam) {
        if (matchesByTeam == null || matchesByTeam.isEmpty()) {
            return Map.of();
        }

        return matchesByTeam.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .map(match -> toFixture(entry.getKey(), match))
                        .filter(Objects::nonNull)
                        .toList()));
    }

    public FixtureDto toFixture(String teamCode, Match match) {
        if (match == null || !match.hasTeamsLoaded()) {
            return null;
        }

        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();
        if (home == null || away == null) {
            return null;
        }

        boolean isHome = teamCode.equals(home.getCode());
        String opponent = isHome ? away.getCode() : home.getCode();
        return new FixtureDto(
                opponent,
                isHome,
                normalizeFixtureStatus(match.getStatus()),
                resolveFixtureResult(match, isHome),
                resolveGoalsFor(match, isHome),
                resolveGoalsAgainst(match, isHome));
    }

    public static Integer resolveGoalsFor(Match match, boolean isHome) {
        return match == null
                ? null
                : match.result()
                        .map(result -> isHome ? result.homeGoals() : result.awayGoals())
                        .orElse(null);
    }

    public static Integer resolveGoalsAgainst(Match match, boolean isHome) {
        return match == null
                ? null
                : match.result()
                        .map(result -> isHome ? result.awayGoals() : result.homeGoals())
                        .orElse(null);
    }

    public static String normalizeFixtureStatus(MatchStatus status) {
        if (status == null) {
            return MatchStatus.SCHEDULED.name();
        }

        return switch (status) {
            case LIVE, SUSPENDED -> MatchStatus.LIVE.name();
            case FINISHED -> MatchStatus.FINISHED.name();
            case POSTPONED -> MatchStatus.POSTPONED.name();
            case SCHEDULED, CANCELLED -> MatchStatus.SCHEDULED.name();
        };
    }

    public static String resolveFixtureResult(Match match, boolean isHome) {
        if (match == null || match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }

        return match.result().map(result -> toPerspectiveResult(result, isHome)).orElse(null);
    }

    private static String toPerspectiveResult(MatchResult result, boolean isHome) {
        if (result.isDraw()) {
            return "DRAW";
        }

        boolean teamWon = isHome ? result.isHomeWin() : result.isAwayWin();
        return teamWon ? "WIN" : "LOSS";
    }
}

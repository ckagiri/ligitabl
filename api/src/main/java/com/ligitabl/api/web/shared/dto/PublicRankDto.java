package com.ligitabl.api.web.shared.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * A single row in the public, read-only prediction view — always Pos/Team/Actual/Delta, whether
 * it's a scored historical round or a predicted-vs-standings comparison for the current round.
 */
@Value
@Builder
@AllArgsConstructor
public class PublicRankDto {
    int position;
    String teamCode;

    UUID teamId;
    String teamName;
    String teamShortName;
    String teamShorterName;
    String teamSlug;
    String teamTla;

    int actualPosition;
    int delta;

    /** From a scored {@link ResultTeamRank} — predicted/standings/hit map straight across. */
    public static PublicRankDto fromResult(ResultTeamRank resultRank, Map<String, Team> teamsByCode) {
        TeamRank ranking = resultRank.getRanking();
        Team team = teamsByCode.get(ranking.getCode());

        return builderFor(ranking, team)
                .actualPosition(resultRank.getStandingsPosition())
                .delta(resultRank.getHit())
                .build();
    }

    /**
     * From a predicted {@link TeamRank} plus a code→position lookup for "actual" (current
     * standings, or the season baseline when nothing has been played yet) — delta computed the
     * same way {@code ScoringEngine} computes {@code hit}.
     */
    public static PublicRankDto fromPrediction(
            TeamRank rank, Map<String, Integer> actualPositionByCode, Map<String, Team> teamsByCode) {
        Team team = teamsByCode.get(rank.getCode());
        int actualPosition = actualPositionByCode.getOrDefault(rank.getCode(), rank.getPosition());

        return builderFor(rank, team)
                .actualPosition(actualPosition)
                .delta(Math.abs(rank.getPosition() - actualPosition))
                .build();
    }

    private static PublicRankDtoBuilder builderFor(TeamRank rank, Team team) {
        if (team == null) {
            return PublicRankDto.builder()
                    .position(rank.getPosition())
                    .teamCode(rank.getCode())
                    .teamId(null)
                    .teamName(rank.getCode())
                    .teamShortName(rank.getCode())
                    .teamShorterName(rank.getCode())
                    .teamSlug(rank.getCode())
                    .teamTla(rank.getCode());
        }

        return PublicRankDto.builder()
                .position(rank.getPosition())
                .teamCode(rank.getCode())
                .teamId(team.getId())
                .teamName(team.getName())
                .teamShortName(team.getShortName())
                .teamShorterName(team.getShorterName() != null ? team.getShorterName() : team.getShortName())
                .teamSlug(team.getSlug().value())
                .teamTla(team.getTla());
    }

    public static List<PublicRankDto> listOfResults(List<ResultTeamRank> resultRanks, Map<String, Team> teamsByCode) {
        if (resultRanks == null || resultRanks.isEmpty()) {
            return List.of();
        }

        return resultRanks.stream().map(rank -> fromResult(rank, teamsByCode)).toList();
    }

    public static List<PublicRankDto> listOfPrediction(
            List<TeamRank> ranks, Map<String, Integer> actualPositionByCode, Map<String, Team> teamsByCode) {
        if (ranks == null || ranks.isEmpty()) {
            return List.of();
        }

        return ranks.stream().map(rank -> fromPrediction(rank, actualPositionByCode, teamsByCode)).toList();
    }
}

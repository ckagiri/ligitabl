package com.ligitabl.api.usecases.seasonprediction.getseasonpred;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class SeasonPredictionRankDto {
    int position;
    String teamCode;

    UUID teamId;
    String teamName;
    String teamShortName;
    String teamSlug;
    String teamTla;

    public static SeasonPredictionRankDto from(TeamRank rank, Map<String, Team> teamsByCode) {
        Team team = teamsByCode.get(rank.getCode());

        if (team == null) {
            return SeasonPredictionRankDto.builder()
                    .position(rank.getPosition())
                    .teamCode(rank.getCode())
                    .teamId(null)
                    .teamName(rank.getCode())
                    .teamShortName(rank.getCode())
                    .teamSlug(rank.getCode())
                    .teamTla(rank.getCode())
                    .build();
        }

        return SeasonPredictionRankDto.builder()
                .position(rank.getPosition())
                .teamCode(rank.getCode())
                .teamId(team.getId())
                .teamName(team.getName())
                .teamShortName(team.getShortName())
                .teamSlug(team.getSlug().value())
                .teamTla(team.getTla())
                .build();
    }

    public static List<SeasonPredictionRankDto> listOf(List<TeamRank> rankings, Map<String, Team> teamsByCode) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        return rankings.stream().map(rank -> from(rank, teamsByCode)).toList();
    }
}

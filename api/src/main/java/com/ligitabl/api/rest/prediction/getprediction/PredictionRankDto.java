package com.ligitabl.api.rest.prediction.getprediction;

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
public class PredictionRankDto {
    int position;
    String teamCode;

    UUID teamId;
    String teamName;
    String teamShortName;
    String teamSlug;
    String teamTla;

    public static PredictionRankDto from(TeamRank rank, Map<String, Team> teamsByCode) {
        Team team = teamsByCode.get(rank.getCode());

        if (team == null) {
            return PredictionRankDto.builder()
                    .position(rank.getPosition())
                    .teamCode(rank.getCode())
                    .teamId(null)
                    .teamName(rank.getCode())
                    .teamShortName(rank.getCode())
                    .teamSlug(rank.getCode())
                    .teamTla(rank.getCode())
                    .build();
        }

        return PredictionRankDto.builder()
                .position(rank.getPosition())
                .teamCode(rank.getCode())
                .teamId(team.getId())
                .teamName(team.getName())
                .teamShortName(team.getShortName())
                .teamSlug(team.getSlug().value())
                .teamTla(team.getTla())
                .build();
    }

    public static List<PredictionRankDto> listOf(List<TeamRank> rankings, Map<String, Team> teamsByCode) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        return rankings.stream().map(rank -> from(rank, teamsByCode)).toList();
    }
}

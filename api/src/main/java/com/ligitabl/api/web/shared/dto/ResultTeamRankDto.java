package com.ligitabl.api.web.shared.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class ResultTeamRankDto {
    // Predicted position (from TeamRank)
    int predictedPosition;

    // Team information
    String teamCode;
    UUID teamId;
    String teamName;
    String teamShortName;
    String teamSlug;
    String teamTla;

    // Actual position and scoring
    int standingsPosition;
    int hit;

    public static ResultTeamRankDto from(ResultTeamRank resultRank, Map<String, Team> teamsByCode) {
        String code = resultRank.getRanking().getCode();
        Team team = teamsByCode.get(code);

        if (team == null) {
            return ResultTeamRankDto.builder()
                    .predictedPosition(resultRank.getRanking().getPosition())
                    .teamCode(code)
                    .teamId(null)
                    .teamName(code)
                    .teamShortName(code)
                    .teamSlug(code)
                    .teamTla(code)
                    .standingsPosition(resultRank.getStandingsPosition())
                    .hit(resultRank.getHit())
                    .build();
        }

        return ResultTeamRankDto.builder()
                .predictedPosition(resultRank.getRanking().getPosition())
                .teamCode(code)
                .teamId(team.getId())
                .teamName(team.getName())
                .teamShortName(team.getShortName())
                .teamSlug(team.getSlug().value())
                .teamTla(team.getTla())
                .standingsPosition(resultRank.getStandingsPosition())
                .hit(resultRank.getHit())
                .build();
    }

    public static List<ResultTeamRankDto> listOf(List<ResultTeamRank> resultRanks, Map<String, Team> teamsByCode) {
        if (resultRanks == null || resultRanks.isEmpty()) {
            return List.of();
        }

        return resultRanks.stream().map(rank -> from(rank, teamsByCode)).toList();
    }
}

package com.ligitabl.api.web.shared.dto;

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
public class TeamRankDto {
    int position;
    String teamCode;

    UUID teamId;
    String teamName;
    String teamShortName;
    // Compact name for space-constrained UI (Team.shorterName); falls back to teamShortName
    // when a team hasn't had one seeded yet.
    String teamShorterName;
    String teamSlug;
    String teamTla;

    public static TeamRankDto from(TeamRank rank, Map<String, Team> teamsByCode) {
        Team team = teamsByCode.get(rank.getCode());

        if (team == null) {
            return TeamRankDto.builder()
                    .position(rank.getPosition())
                    .teamCode(rank.getCode())
                    .teamId(null)
                    .teamName(rank.getCode())
                    .teamShortName(rank.getCode())
                    .teamShorterName(rank.getCode())
                    .teamSlug(rank.getCode())
                    .teamTla(rank.getCode())
                    .build();
        }

        return TeamRankDto.builder()
                .position(rank.getPosition())
                .teamCode(rank.getCode())
                .teamId(team.getId())
                .teamName(team.getName())
                .teamShortName(team.getShortName())
                .teamShorterName(team.getShorterName() != null ? team.getShorterName() : team.getShortName())
                .teamSlug(team.getSlug().value())
                .teamTla(team.getTla())
                .build();
    }

    public static List<TeamRankDto> listOf(List<TeamRank> rankings, Map<String, Team> teamsByCode) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        return rankings.stream().map(rank -> from(rank, teamsByCode)).toList();
    }
}

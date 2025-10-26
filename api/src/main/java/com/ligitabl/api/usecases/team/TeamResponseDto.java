package com.ligitabl.api.usecases.team;

import com.ligitabl.model.domain.Team;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class TeamResponseDto {
    UUID id;
    String name;
    String shortName;
    String slug;
    String tla;
    OffsetDateTime createDate;
    OffsetDateTime updateDate;

    public static TeamResponseDto from(Team team) {
        if (team == null)
            return null;
        return TeamResponseDto.builder()
                .id(team.getId())
                .name(team.getName())
                .shortName(team.getShortName())
                .slug(team.getSlug())
                .tla(team.getTla())
                .createDate(team.getCreateDate())
                .updateDate(team.getUpdateDate())
                .build();
    }

    public static List<TeamResponseDto> listOf(List<Team> teams) {
        return teams.stream().map(TeamResponseDto::from).toList();
    }
}

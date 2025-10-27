package com.ligitabl.api.usecases.team;

import com.ligitabl.model.domain.Team;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class TeamDto {
    UUID id;
    String name;
    String shortName;
    String slug;
    String tla;
    OffsetDateTime updateDate;

    public static TeamDto from(Team team) {
        if (team == null)
            return null;
        return TeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .shortName(team.getShortName())
                .slug(team.getSlug())
                .tla(team.getTla())
                .updateDate(team.getUpdateDate())
                .build();
    }

    public static List<TeamDto> listOf(List<Team> teams) {
        return teams.stream().map(TeamDto::from).toList();
    }
}

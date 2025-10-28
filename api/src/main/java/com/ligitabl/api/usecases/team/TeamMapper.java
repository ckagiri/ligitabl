package com.ligitabl.api.usecases.team;

import org.springframework.stereotype.Component;

import com.ligitabl.api.usecases.team.createteam.CreateTeamCommand;
import com.ligitabl.api.usecases.team.updateteam.UpdateTeamCommand;
import com.ligitabl.model.domain.Team;

@Component
public class TeamMapper {
    public Team toEntity(CreateTeamCommand cmd) {
        return baseBuilder(cmd).build();
    }

    public Team toEntity(UpdateTeamCommand cmd) {
        return baseBuilder(cmd).id(cmd.getUuid()).build();
    }

    private Team.TeamBuilder<?, ?> baseBuilder(TeamPayload cmd) {
        return Team.builder()
                .name(cmd.getName())
                .shortName(cmd.getShortName())
                .slug(cmd.getSlug())
                .tla(cmd.getTla());
    }
}

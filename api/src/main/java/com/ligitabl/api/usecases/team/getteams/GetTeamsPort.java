package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.usecases.team.TeamDto;

public interface GetTeamsPort extends UseCase<Void, List<TeamDto>> {
    List<TeamDto> execute();
}

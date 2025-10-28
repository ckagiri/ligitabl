package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTeamsUseCase implements UseCase<Void, List<TeamDto>> {
    private final TeamRepo teamRepo;

    @Override
    public List<TeamDto> execute(Void unused) {
        return TeamDto.listOf(teamRepo.findAll());
    }

    public List<TeamDto> execute() {
        return execute(null);
    }
}

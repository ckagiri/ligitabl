package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTeamsUseCase implements UseCase<Void, List<Team>> {

    private final TeamRepo teamRepo;

    @Override
    public List<Team> execute(Void unused) {
        return teamRepo.findAll();
    }
}

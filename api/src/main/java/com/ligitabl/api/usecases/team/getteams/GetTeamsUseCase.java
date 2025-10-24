package com.ligitabl.api.usecases.team.getteams;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.domain.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetTeamsUseCase implements UseCase<Void, List<Team>> {

    private final TeamRepo teamDao;

    @Override
    public List<Team> execute(Void unused) {
        return teamDao.findAll();
    }
}

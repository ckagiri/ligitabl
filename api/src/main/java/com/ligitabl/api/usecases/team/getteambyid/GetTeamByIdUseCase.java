package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.api.shared.ModelValidator;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.domain.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTeamByIdUseCase implements UseCase<UUID, Team> {
    private final TeamRepo teamRepo;
    private final ModelValidator modelValidator;

    @Override
    public Team execute(UUID id) {
        var team = teamRepo.findById(id);
        return modelValidator.requireFound(team);
    }
}

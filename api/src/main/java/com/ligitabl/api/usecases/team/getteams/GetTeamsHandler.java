package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetTeamsHandler implements GetTeamsUseCase {
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, List<TeamDto>> execute(Void unused) {
        return Either.catching(() -> TeamDto.listOf(teamRepo.findAll()), UseCaseErrors::fromException);
    }
}

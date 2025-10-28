package com.ligitabl.api.usecases.team.deleteteam;

import static com.ligitabl.api.shared.ValidationUtils.requireExists;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.shared.Either;
import com.ligitabl.model.shared.Unit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteTeamUseCase implements UseCase<DeleteTeamCommand, Either<UseCaseError, Unit>> {
    private final RequestValidator requestValidator;
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Unit> execute(DeleteTeamCommand command) {
        return requestValidator
                .validate(command)
                .map(DeleteTeamCommand::getUuid)
                .flatMap(id -> requireExists(teamRepo.existsById(id), id, "Team"))
                .map(id -> {
                    teamRepo.delete(id);
                    return Unit.INSTANCE;
                });
    }
}

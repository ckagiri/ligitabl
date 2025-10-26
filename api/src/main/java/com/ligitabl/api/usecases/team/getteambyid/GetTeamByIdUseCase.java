package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validator.RequestValidator;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.domain.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTeamByIdUseCase implements UseCase<GetTeamByIdQuery, Either<UseCaseError, Team>> {
    private final RequestValidator requestValidator;
    private final TeamRepo teamRepo;

    @Override
    public Either<UseCaseError, Team> execute(GetTeamByIdQuery query) {
        return requestValidator.validate(query)
            .map(GetTeamByIdQuery::getUuid)
            .flatMap(id -> teamRepo.findById(id)
                .map(Either::<UseCaseError, Team>right)
                .orElseGet(() -> Either.left(new NotFoundError("Team", id))));
    }
}

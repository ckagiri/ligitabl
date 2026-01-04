package com.ligitabl.api.usecases.competition.getcompetitions;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.competition.CompetitionDto;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetCompetitionsUseCase implements UseCase<Void, Either<UseCaseError, List<CompetitionDto>>> {

    private final CompetitionRepo competitionRepo;

    @Override
    public Either<UseCaseError, List<CompetitionDto>> execute(Void unused) {
        return Either.catching(() -> CompetitionDto.listOf(competitionRepo.findAll()), UseCaseErrors::fromException);
    }
}

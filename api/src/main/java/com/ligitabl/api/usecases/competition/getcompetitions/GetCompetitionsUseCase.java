package com.ligitabl.api.usecases.competition.getcompetitions;

import java.util.List;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.competition.CompetitionDto;

public interface GetCompetitionsUseCase extends UseCase<Void, Either<UseCaseError, List<CompetitionDto>>> {}

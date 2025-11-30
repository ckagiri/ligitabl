package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.usecases.competition.CompetitionDto;

public interface GetCompetitionBySlugUseCase
        extends UseCase<GetCompetitionBySlugQuery, CompetitionDto> {}

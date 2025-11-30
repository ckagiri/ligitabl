package com.ligitabl.api.usecases.competition.getcompetitions;

import java.util.List;

import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.usecases.competition.CompetitionDto;

public interface GetCompetitionsUseCase extends UseCase<Void, List<CompetitionDto>> {
    default List<CompetitionDto> execute() {
        return execute(null);
    }
}

package com.ligitabl.api.usecase.round.advancematchday;

import com.ligitabl.api.client.FootballDataApiError;

public sealed interface AdvanceMatchdayError {
    record CompetitionNotFound(String code) implements AdvanceMatchdayError {}

    record SeasonNotFound(String competitionCode) implements AdvanceMatchdayError {}

    record ApiError(FootballDataApiError error) implements AdvanceMatchdayError {}
}

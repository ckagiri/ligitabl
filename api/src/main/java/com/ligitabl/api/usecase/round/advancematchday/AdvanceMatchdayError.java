package com.ligitabl.api.usecase.round.advancematchday;

import com.ligitabl.api.usecase.match.shared.MatchFeedError;

public sealed interface AdvanceMatchdayError {
    record CompetitionNotFound(String code) implements AdvanceMatchdayError {}

    record SeasonNotFound(String competitionCode) implements AdvanceMatchdayError {}

    record ApiError(MatchFeedError error) implements AdvanceMatchdayError {}
}

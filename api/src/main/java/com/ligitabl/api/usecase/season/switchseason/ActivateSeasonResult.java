package com.ligitabl.api.usecase.season.switchseason;

import java.util.UUID;

public sealed interface ActivateSeasonResult
        permits ActivateSeasonResult.Ok,
                ActivateSeasonResult.CompetitionNotFound,
                ActivateSeasonResult.NoUpcomingSeason {
    record Ok(UUID newActiveSeasonId) implements ActivateSeasonResult {}

    record CompetitionNotFound(String slug) implements ActivateSeasonResult {}

    record NoUpcomingSeason(UUID competitionId) implements ActivateSeasonResult {}
}

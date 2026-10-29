package com.ligitabl.api.usecase.season.switchseason;

import java.util.UUID;

public sealed interface AutoActivateSeasonResult
        permits AutoActivateSeasonResult.Activated,
                AutoActivateSeasonResult.NotDue,
                AutoActivateSeasonResult.NoUpcomingSeason,
                AutoActivateSeasonResult.CompetitionNotFound {
    record Activated(UUID newActiveSeasonId, UUID formerSeasonId) implements AutoActivateSeasonResult {}

    record NotDue() implements AutoActivateSeasonResult {}

    record NoUpcomingSeason() implements AutoActivateSeasonResult {}

    record CompetitionNotFound(String slug) implements AutoActivateSeasonResult {}
}

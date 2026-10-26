package com.ligitabl.api.usecase.season.admin;

import java.util.UUID;

public sealed interface RevertSeasonResult
        permits RevertSeasonResult.Ok, RevertSeasonResult.CompetitionNotFound, RevertSeasonResult.NoFormerSeason {
    record Ok(UUID newActiveSeasonId) implements RevertSeasonResult {}

    record CompetitionNotFound(String slug) implements RevertSeasonResult {}

    record NoFormerSeason(UUID competitionId) implements RevertSeasonResult {}
}

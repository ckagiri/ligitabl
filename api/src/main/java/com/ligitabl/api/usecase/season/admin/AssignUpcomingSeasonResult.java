package com.ligitabl.api.usecase.season.admin;

import java.util.UUID;

public sealed interface AssignUpcomingSeasonResult
        permits AssignUpcomingSeasonResult.Ok,
                AssignUpcomingSeasonResult.CompetitionNotFound,
                AssignUpcomingSeasonResult.SeasonNotFound,
                AssignUpcomingSeasonResult.SeasonNotBelongToCompetition,
                AssignUpcomingSeasonResult.SeasonAlreadyCompleted {
    record Ok(UUID seasonId) implements AssignUpcomingSeasonResult {}

    record CompetitionNotFound(String slug) implements AssignUpcomingSeasonResult {}

    record SeasonNotFound(UUID seasonId) implements AssignUpcomingSeasonResult {}

    record SeasonNotBelongToCompetition(UUID seasonId, UUID competitionId) implements AssignUpcomingSeasonResult {}

    record SeasonAlreadyCompleted(UUID seasonId) implements AssignUpcomingSeasonResult {}
}

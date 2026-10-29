package com.ligitabl.api.usecase.season.updateseasondates;

import java.util.UUID;

public sealed interface UpdateSeasonDatesResult
        permits UpdateSeasonDatesResult.Ok,
                UpdateSeasonDatesResult.OutgoingSeasonNotFound,
                UpdateSeasonDatesResult.UpcomingSeasonNotFound,
                UpdateSeasonDatesResult.InvalidDateOrder {
    record Ok() implements UpdateSeasonDatesResult {}

    record OutgoingSeasonNotFound(UUID seasonId) implements UpdateSeasonDatesResult {}

    record UpcomingSeasonNotFound(UUID seasonId) implements UpdateSeasonDatesResult {}

    record InvalidDateOrder(String message) implements UpdateSeasonDatesResult {}
}

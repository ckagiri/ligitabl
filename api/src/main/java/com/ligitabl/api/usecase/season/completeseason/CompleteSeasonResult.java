package com.ligitabl.api.usecase.season.completeseason;

public sealed interface CompleteSeasonResult
        permits CompleteSeasonResult.Ok, CompleteSeasonResult.SeasonNotFound, CompleteSeasonResult.SeasonNotEligible {
    record Ok() implements CompleteSeasonResult {}

    record SeasonNotFound() implements CompleteSeasonResult {}

    record SeasonNotEligible(String reason) implements CompleteSeasonResult {}
}

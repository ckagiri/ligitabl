package com.ligitabl.api.rest.prediction.preseason;

import java.util.UUID;

public sealed interface PreSeasonRegistrationError {
    record SeasonNotFound() implements PreSeasonRegistrationError {}

    record NotPreSeason() implements PreSeasonRegistrationError {}

    record AlreadyJoined(UUID existingPredictionId) implements PreSeasonRegistrationError {}

    record TooManySwaps(int provided, int max) implements PreSeasonRegistrationError {}

    record SameTeam() implements PreSeasonRegistrationError {}

    record InvalidTeamCode(String code) implements PreSeasonRegistrationError {}

    record MainContestNotFound() implements PreSeasonRegistrationError {}

    record TransactionFailed(String reason) implements PreSeasonRegistrationError {}
}

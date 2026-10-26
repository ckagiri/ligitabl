package com.ligitabl.api.usecase.admin.deleteuser;

import java.util.UUID;

public sealed interface DeleteUserResult
        permits DeleteUserResult.Ok,
                DeleteUserResult.UserNotFound,
                DeleteUserResult.NotEligible,
                DeleteUserResult.OwnsContest {
    record Ok(UUID userId) implements DeleteUserResult {}

    record UserNotFound(UUID userId) implements DeleteUserResult {}

    record NotEligible(UUID userId, int pastSeasonPredictions, int currentSeasonSwaps) implements DeleteUserResult {}

    record OwnsContest(UUID userId) implements DeleteUserResult {}
}

package com.ligitabl.api.usecase.match.syncfromapi;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.shared.errors.UseCaseError;

public sealed interface SyncRoundError {
    record HierarchyError(UseCaseError cause) implements SyncRoundError {}

    record ApiError(FootballDataApiError cause) implements SyncRoundError {}
}

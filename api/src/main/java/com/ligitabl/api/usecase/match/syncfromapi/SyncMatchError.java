package com.ligitabl.api.usecase.match.syncfromapi;

import com.ligitabl.api.client.FootballDataApiError;

public sealed interface SyncMatchError {
    record NotFound(int clientId) implements SyncMatchError {}

    record AlreadyFinished(int clientId) implements SyncMatchError {}

    record ApiError(FootballDataApiError cause) implements SyncMatchError {}
}

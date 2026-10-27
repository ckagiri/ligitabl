package com.ligitabl.api.usecase.match.syncfromapi;

import com.ligitabl.api.usecase.match.shared.MatchFeedError;

public sealed interface SyncMatchError {
    record NotFound(int clientId) implements SyncMatchError {}

    record AlreadyFinished(int clientId) implements SyncMatchError {}

    record ApiError(MatchFeedError cause) implements SyncMatchError {}
}

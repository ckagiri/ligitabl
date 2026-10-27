package com.ligitabl.api.usecase.match.syncfromapi;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecase.match.shared.MatchFeedError;

public sealed interface SyncRoundError {
    record HierarchyError(UseCaseError cause) implements SyncRoundError {}

    record ApiError(MatchFeedError cause) implements SyncRoundError {}
}

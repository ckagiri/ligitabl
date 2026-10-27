package com.ligitabl.api.usecase.match.syncmatches;

import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecase.match.shared.MatchFeedError;

public sealed interface SyncMatchesError {
    record HierarchyError(UseCaseError error) implements SyncMatchesError {}

    record DataAccessError(MatchFeedError error) implements SyncMatchesError {}
}

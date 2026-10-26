package com.ligitabl.api.usecase.match.syncmatches;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.shared.errors.UseCaseError;

public sealed interface SyncMatchesError {
    record HierarchyError(UseCaseError error) implements SyncMatchesError {}

    record DataAccessError(FootballDataApiError error) implements SyncMatchesError {}
}

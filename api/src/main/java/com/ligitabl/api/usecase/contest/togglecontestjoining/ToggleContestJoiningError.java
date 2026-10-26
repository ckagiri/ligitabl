package com.ligitabl.api.usecase.contest.togglecontestjoining;

import java.util.UUID;

public sealed interface ToggleContestJoiningError {
    record ContestNotFound(UUID contestId) implements ToggleContestJoiningError {}

    record NotOwner(UUID userId, UUID contestId) implements ToggleContestJoiningError {}

    /** The contest belongs to a past (no longer active) season — historical, read-only. */
    record PastSeasonContest(UUID contestId) implements ToggleContestJoiningError {}
}

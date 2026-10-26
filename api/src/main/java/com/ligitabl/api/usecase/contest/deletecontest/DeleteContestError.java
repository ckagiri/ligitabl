package com.ligitabl.api.usecase.contest.deletecontest;

import java.util.UUID;

public sealed interface DeleteContestError {
    record ContestNotFound(UUID contestId) implements DeleteContestError {}

    record NotOwner(UUID userId, UUID contestId) implements DeleteContestError {}

    record DeleteBlocked(UUID contestId) implements DeleteContestError {}

    /** The contest belongs to a past (no longer active) season — historical, read-only. */
    record PastSeasonContest(UUID contestId) implements DeleteContestError {}
}

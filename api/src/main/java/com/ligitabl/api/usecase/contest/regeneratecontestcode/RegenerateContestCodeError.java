package com.ligitabl.api.usecase.contest.regeneratecontestcode;

import java.util.UUID;

public sealed interface RegenerateContestCodeError {
    record ContestNotFound(UUID contestId) implements RegenerateContestCodeError {}

    record NotOwner(UUID userId, UUID contestId) implements RegenerateContestCodeError {}

    /** The contest belongs to a past (no longer active) season — historical, read-only. */
    record PastSeasonContest(UUID contestId) implements RegenerateContestCodeError {}
}

package com.ligitabl.api.usecase.contest.leavecontest;

import java.util.UUID;

public sealed interface LeavePrivateContestError {
    record ContestNotFound(UUID contestId) implements LeavePrivateContestError {}

    record OwnerCannotLeave(UUID contestId) implements LeavePrivateContestError {}

    record NotAMember(UUID userId, UUID contestId) implements LeavePrivateContestError {}

    /** The contest belongs to a past (no longer active) season — historical, read-only. */
    record PastSeasonContest(UUID contestId) implements LeavePrivateContestError {}

    /** The contest's own join window has closed — membership is locked. */
    record JoinWindowClosed(UUID contestId) implements LeavePrivateContestError {}
}

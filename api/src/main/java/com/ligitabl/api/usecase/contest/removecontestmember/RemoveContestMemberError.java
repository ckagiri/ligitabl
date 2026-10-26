package com.ligitabl.api.usecase.contest.removecontestmember;

import java.util.UUID;

public sealed interface RemoveContestMemberError {
    record ContestNotFound(UUID contestId) implements RemoveContestMemberError {}

    record NotOwner(UUID requesterId, UUID contestId) implements RemoveContestMemberError {}

    record CannotRemoveOwner(UUID contestId) implements RemoveContestMemberError {}

    record NotAMember(UUID targetUserId, UUID contestId) implements RemoveContestMemberError {}

    /** The contest belongs to a past (no longer active) season — historical, read-only. */
    record PastSeasonContest(UUID contestId) implements RemoveContestMemberError {}

    /** The contest's own join window has closed — membership is locked. */
    record JoinWindowClosed(UUID contestId) implements RemoveContestMemberError {}
}

package com.ligitabl.api.rest.contest.getprivatecontest;

import java.util.UUID;

public sealed interface GetPrivateContestError {
    record ContestNotFound(UUID contestId) implements GetPrivateContestError {}

    record NotAMember(UUID userId, UUID contestId) implements GetPrivateContestError {}

    record SeasonNotFound() implements GetPrivateContestError {}

    record CompetitionNotFound(UUID seasonId) implements GetPrivateContestError {}

    record SegmentNotFound(String segmentCode) implements GetPrivateContestError {}
}

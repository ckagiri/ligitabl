package com.ligitabl.api.rest.contest;

import java.util.UUID;

public sealed interface GetPrivateContestError {
    record ContestNotFound(UUID contestId) implements GetPrivateContestError {}

    record NotAMember(UUID userId, UUID contestId) implements GetPrivateContestError {}

    record SeasonNotFound() implements GetPrivateContestError {}

    record SegmentNotFound(String segmentCode) implements GetPrivateContestError {}
}

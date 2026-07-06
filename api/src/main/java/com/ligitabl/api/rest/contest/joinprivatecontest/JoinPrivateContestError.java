package com.ligitabl.api.rest.contest.joinprivatecontest;

public sealed interface JoinPrivateContestError {
    record ContestNotFound(String joinCode) implements JoinPrivateContestError {}

    record NoPrediction() implements JoinPrivateContestError {}

    /** The contest's season is neither in play nor in pre-season (e.g. off-season, inactive, or a past season). */
    record SeasonNotJoinable() implements JoinPrivateContestError {}

    record ContestClosed() implements JoinPrivateContestError {}

    record MemberCapReached(int cap) implements JoinPrivateContestError {}

    record AlreadyMember() implements JoinPrivateContestError {}

    record JoinWindowClosed() implements JoinPrivateContestError {}

    record MaxContestsReached(int max) implements JoinPrivateContestError {}

    record CurrentRoundNotFound() implements JoinPrivateContestError {}

    record CompetitionNotFound() implements JoinPrivateContestError {}
}

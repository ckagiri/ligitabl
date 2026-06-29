package com.ligitabl.api.rest.contest.createprivatecontest;

public sealed interface CreatePrivateContestError {
    record CompetitionNotFound(String slug) implements CreatePrivateContestError {}

    record SeasonNotFound() implements CreatePrivateContestError {}

    record CurrentRoundNotFound() implements CreatePrivateContestError {}

    record InvalidFromSprint(String sprintCode) implements CreatePrivateContestError {}

    record InvalidToCombination(String fromCode, String toCode) implements CreatePrivateContestError {}

    record MaxContestsReached(int max) implements CreatePrivateContestError {}
}

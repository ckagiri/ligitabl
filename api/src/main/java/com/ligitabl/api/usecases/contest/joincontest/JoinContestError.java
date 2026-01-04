package com.ligitabl.api.usecases.contest.joincontest;

import java.util.List;
import java.util.UUID;

public sealed interface JoinContestError {
    record SeasonNotFound() implements JoinContestError {}

    record SeasonCompleted() implements JoinContestError {}

    record AlreadyJoined(UUID existingPredictionId) implements JoinContestError {}

    record InvalidTeamCount(int provided, int required) implements JoinContestError {}

    record DuplicatePositions(List<Integer> duplicates) implements JoinContestError {}

    record DuplicateTeamCodes(List<String> duplicates) implements JoinContestError {}

    record InvalidTeamCodes(List<String> invalidCodes) implements JoinContestError {}

    record SeasonEnded(int currentRound, int maxRounds) implements JoinContestError {}

    record DefaultContestNotFound() implements JoinContestError {}

    record TransactionFailed(String reason) implements JoinContestError {}
}

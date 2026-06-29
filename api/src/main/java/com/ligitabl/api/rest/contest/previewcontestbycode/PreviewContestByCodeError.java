package com.ligitabl.api.rest.contest.previewcontestbycode;

public sealed interface PreviewContestByCodeError {
    record ContestNotFound(String joinCode) implements PreviewContestByCodeError {}

    record ContestClosed() implements PreviewContestByCodeError {}

    record CompetitionNotFound(String seasonId) implements PreviewContestByCodeError {}
}

package com.ligitabl.api.rest.contest.renamecontest;

import java.util.UUID;

public sealed interface RenameContestError {
    record ContestNotFound(UUID contestId) implements RenameContestError {}

    record NotOwner(UUID contestId) implements RenameContestError {}

    record BlankName() implements RenameContestError {}

    /** This owner already has another contest with this exact name in this season. */
    record NameConflict(String name) implements RenameContestError {}
}

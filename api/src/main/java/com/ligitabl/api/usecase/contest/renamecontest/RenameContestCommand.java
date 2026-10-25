package com.ligitabl.api.usecase.contest.renamecontest;

import java.util.UUID;

public record RenameContestCommand(UUID userId, UUID contestId, String name) {}

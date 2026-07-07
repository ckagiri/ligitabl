package com.ligitabl.api.rest.contest.renamecontest;

import java.util.UUID;

public record RenameContestCommand(UUID userId, UUID contestId, String name) {}

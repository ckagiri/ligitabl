package com.ligitabl.api.usecase.contest.regeneratecontestcode;

import java.util.UUID;

public record RegenerateContestCodeResult(UUID contestId, String joinCode) {}

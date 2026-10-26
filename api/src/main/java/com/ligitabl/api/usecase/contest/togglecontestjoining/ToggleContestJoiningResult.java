package com.ligitabl.api.usecase.contest.togglecontestjoining;

import java.util.UUID;

public record ToggleContestJoiningResult(UUID contestId, boolean isOpen) {}

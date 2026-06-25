package com.ligitabl.api.rest.contest;

import java.util.UUID;

public record CreatePrivateContestCommand(
        UUID userId,
        String name,
        Integer maxEntries,
        String fromSprintCode,
        String toSprintCode,
        String competitionSlug) {}

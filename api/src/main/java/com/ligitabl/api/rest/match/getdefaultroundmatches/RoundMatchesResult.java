package com.ligitabl.api.rest.match.getdefaultroundmatches;

import java.util.List;
import java.util.UUID;

import com.ligitabl.api.rest.match.MatchDto;

public record RoundMatchesResult(
        UUID seasonId,
        String seasonSlug,
        int viewingRound,
        int currentRound,
        int lastRound,
        List<MatchDto> matches,
        boolean roundFinalized,
        boolean seasonInSetupMode,
        boolean standingsFinalised) {}

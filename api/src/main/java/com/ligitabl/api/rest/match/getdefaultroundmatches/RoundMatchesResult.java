package com.ligitabl.api.rest.match.getdefaultroundmatches;

import java.util.List;
import java.util.UUID;

import com.ligitabl.api.rest.match.MatchDto;

public record RoundMatchesResult(
        UUID seasonId,
        int currentRound,
        int latestRound,
        List<MatchDto> matches) {}

package com.ligitabl.api.rest.standings;

import java.util.List;
import java.util.UUID;

public record RoundStandingsResult(
        UUID seasonId,
        int viewingRound,
        int currentRound,
        int lastRound,
        List<StandingsEntryDto> standings) {}

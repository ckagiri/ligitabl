package com.ligitabl.api.rest.standings;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.api.web.shared.dto.FixtureDto;

public record RoundStandingsResult(
        UUID seasonId,
        int viewingRound,
        int currentRound,
        int lastRound,
        List<StandingsEntryDto> standings,
        Map<String, List<FixtureDto>> nextFixtures) {}

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
        Map<String, List<FixtureDto>> nextFixtures) {

    /**
     * True while every fixture in the round is still ahead — nothing kicked off, nothing scored.
     */
    public boolean allFixturesUpcoming() {
        if (nextFixtures == null || nextFixtures.isEmpty()) {
            return true;
        }

        return nextFixtures.values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .noneMatch(fixture -> fixture.hasScore()
                        || "LIVE".equals(fixture.status())
                        || "FINISHED".equals(fixture.status()));
    }
}

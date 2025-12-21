package com.ligitabl.model.domain.standings.formatter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.StandingDto;

/**
 * Formats standings as JSON.
 */
public class JsonFormatter implements StandingFormatter {

    @Override
    public String format(List<Standing> standings, Map<UUID, Team> teams) {
        String standingsJson = standings.stream()
                .map(s -> StandingDto.from(s, getTeamName(s.stats().teamId(), teams)))
                .map(this::toJson)
                .collect(Collectors.joining(",\n    "));

        return """
            {
              "standings": [
                %s
              ]
            }
            """
                .formatted(standingsJson);
    }

    private String getTeamName(UUID teamId, Map<UUID, Team> teams) {
        return teams.containsKey(teamId) ? teams.get(teamId).getShortName() : "Unknown";
    }

    private String toJson(StandingDto dto) {
        return """
            {"position": %d, "team": "%s", "points": %d, "gd": %d, "home": {"w": %d, "d": %d, "l": %d}, "away": {"w": %d, "d": %d, "l": %d}}"""
                .formatted(
                        dto.position(),
                        dto.teamName(),
                        dto.points(),
                        dto.goalDifference(),
                        dto.homeStats().won(),
                        dto.homeStats().drawn(),
                        dto.homeStats().lost(),
                        dto.awayStats().won(),
                        dto.awayStats().drawn(),
                        dto.awayStats().lost());
    }
}

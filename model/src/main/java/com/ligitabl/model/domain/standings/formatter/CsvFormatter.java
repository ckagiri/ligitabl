package com.ligitabl.model.domain.standings.formatter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Formats standings as CSV.
 */
public class CsvFormatter implements StandingFormatter {

    @Override
    public String format(List<Standing> standings, Map<UUID, Team> teams) {
        StringBuilder sb = new StringBuilder();
        sb.append("Position,Team,Played,Won,Drawn,Lost,GF,GA,GD,Points\n");

        for (Standing s : standings) {
            TeamStats stats = s.stats();
            String name = teams.containsKey(stats.teamId())
                    ? teams.get(stats.teamId()).getShortName()
                    : "Unknown";

            sb.append(String.format(
                    "%d,\"%s\",%d,%d,%d,%d,%d,%d,%d,%d\n",
                    s.position(),
                    name,
                    stats.played(),
                    stats.won(),
                    stats.drawn(),
                    stats.lost(),
                    stats.goalsFor(),
                    stats.goalsAgainst(),
                    stats.goalDiff(),
                    stats.points()));
        }

        return sb.toString();
    }
}

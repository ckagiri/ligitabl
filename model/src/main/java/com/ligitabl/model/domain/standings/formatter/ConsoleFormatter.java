package com.ligitabl.model.domain.standings.formatter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Formats standings as a console table.
 */
public class ConsoleFormatter implements StandingFormatter {

    @Override
    public String format(List<Standing> standings, Map<UUID, Team> teams) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pos  Team             Pld    W    D    L   GF   GA   GD  Pts\n");
        sb.append("---  ---------------  ---  ---  ---  ---  ---  ---  ---  ---\n");

        for (Standing s : standings) {
            TeamStats stats = s.stats();
            String name = teams.containsKey(stats.teamId())
                    ? teams.get(stats.teamId()).getShortName()
                    : "Unknown";

            // Truncate if too long
            if (name.length() > 15) {
                name = name.substring(0, 12) + "...";
            }

            sb.append(String.format(
                    "%3d  %-15s  %3d  %3d  %3d  %3d  %3d  %3d  %3d  %3d\n",
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

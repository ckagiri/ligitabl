package com.ligitabl.model.domain.standings.formatter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Formats standings as Markdown table.
 */
public class MarkdownFormatter implements StandingFormatter {

    @Override
    public String format(List<Standing> standings, Map<UUID, Team> teams) {
        StringBuilder md = new StringBuilder();
        md.append("| Pos | Team | Pld | W | D | L | GF | GA | GD | Pts |\n");
        md.append("|-----|------|-----|---|---|---|----|----|----|-----|\n");

        for (Standing s : standings) {
            TeamStats stats = s.stats();
            String name = teams.containsKey(stats.teamId())
                    ? teams.get(stats.teamId()).getShortName()
                    : "Unknown";

            md.append(String.format(
                    "| %d | %s | %d | %d | %d | %d | %d | %d | %+d | %d |\n",
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

        return md.toString();
    }
}

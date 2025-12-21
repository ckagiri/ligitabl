package com.ligitabl.model.domain.standings.formatter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.stats.Standing;

/**
 * Strategy pattern for formatting standings in different output formats.
 * Implementations: ConsoleFormatter, JsonFormatter, CsvFormatter, MarkdownFormatter
 */
public interface StandingFormatter {

    /**
     * Format standings for output.
     *
     * @param standings List of standings to format
     * @param teams Map of team ID to Team object
     * @return Formatted string
     */
    String format(List<Standing> standings, Map<UUID, Team> teams);
}

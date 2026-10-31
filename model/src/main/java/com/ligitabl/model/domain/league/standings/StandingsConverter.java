package com.ligitabl.model.domain.league.standings;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.league.Standings;
import com.ligitabl.model.domain.league.StandingsMetadata;
import com.ligitabl.model.domain.league.StandingsTeamRank;
import com.ligitabl.model.domain.league.Team;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.domain.league.standings.stats.Standing;
import com.ligitabl.model.domain.league.standings.stats.TeamStats;
import com.ligitabl.model.domain.league.standings.table.LeagueTable;

public final class StandingsConverter {
    private StandingsConverter() {
        throw new AssertionError("No instances");
    }

    public static Standings convert(LeagueTable table, UUID seasonId, int roundPosition) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(seasonId);

        var rankings = convert(table.getStandings(), table.getTeams());
        return Standings.create(seasonId, roundPosition, rankings);
    }

    public static List<StandingsTeamRank> convert(List<Standing> standings, List<Team> teams) {
        Map<UUID, String> teamCodeMap = teams.stream().collect(Collectors.toMap(t -> t.getId(), Team::getCode));

        return standings.stream().map(s -> toStandingsTeamRank(s, teamCodeMap)).toList();
    }

    private static StandingsTeamRank toStandingsTeamRank(Standing standing, Map<UUID, String> teamCodeMap) {
        TeamStats stats = standing.stats();
        String teamCode =
                teamCodeMap.getOrDefault(stats.teamId(), stats.teamId().toString());
        TeamRank rank = TeamRank.of(teamCode, standing.position());
        StandingsMetadata meta = new StandingsMetadata(
                stats.played(),
                stats.won(),
                stats.drawn(),
                stats.lost(),
                stats.points(),
                stats.goalsFor(),
                stats.goalsAgainst(),
                stats.goalDiff());
        return StandingsTeamRank.builder().ranking(rank).metadata(meta).build();
    }
}

package com.ligitabl.model.domain.standings;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;
import com.ligitabl.model.domain.standings.table.LeagueTable;

public final class StandingsConverter {
    private StandingsConverter() {
        throw new AssertionError("No instances");
    }

    public static Standings convert(LeagueTable table, UUID seasonId, int roundPosition) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(seasonId);

        Map<UUID, String> teamCodeMap =
                table.getTeams().stream().collect(Collectors.toMap(t -> t.getId(), t -> t.getShortName()));

        List<StandingsTeamRank> rankings = table.getStandings().stream()
                .map(s -> toStandingsTeamRank(s, teamCodeMap))
                .toList();

        return Standings.create(seasonId, roundPosition, rankings);
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

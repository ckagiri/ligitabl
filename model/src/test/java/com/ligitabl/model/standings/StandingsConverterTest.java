package com.ligitabl.model.standings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.StandingsConverter;
import com.ligitabl.model.domain.standings.ranking.RankingStrategy;
import com.ligitabl.model.domain.standings.table.LeagueTable;

class StandingsConverterTest {

    @Test
    void convertsLeagueTableToStandings() {
        // Create simple teams (use model Team builder)
        Team t1 = Team.builder()
                .id(UUID.randomUUID())
                .name("Arsenal")
                .shortName("ARS")
                .slug(null)
                .tla("ARS")
                .build();
        Team t2 = Team.builder()
                .id(UUID.randomUUID())
                .name("Man City")
                .shortName("MCI")
                .slug(null)
                .tla("MCI")
                .build();

        var seasonId = UUID.randomUUID();
        var roundId = UUID.randomUUID();
        // Create matches - use minimal Match builder with Score where needed
        Match m1 = Match.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundId(roundId)
                .homeTeamId(t1.getId())
                .awayTeamId(t2.getId())
                .build();

        LeagueTable table = new LeagueTable(List.of(t1, t2), List.of(m1), RankingStrategy.ENGLISH_PREMIER_LEAGUE);
        var standings = StandingsConverter.convert(table, seasonId, 1);

        assertNotNull(standings);
        assertEquals(seasonId, standings.getSeasonId());
        assertEquals(2, standings.teamCount());
    }
}

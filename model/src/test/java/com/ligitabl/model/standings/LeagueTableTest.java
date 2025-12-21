package com.ligitabl.model.standings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.standings.ranking.RankingStrategy;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.table.LeagueTable;

class LeagueTableTest {

    @Test
    void validTableShouldCreate() {
        Team t1 = Team.builder()
                .id(UUID.randomUUID())
                .name("Team 1")
                .shortName("T1")
                .slug(null)
                .tla("T01")
                .build();
        Team t2 = Team.builder()
                .id(UUID.randomUUID())
                .name("Team 2")
                .shortName("T2")
                .slug(null)
                .tla("T02")
                .build();

        Match m = Match.builder()
                .id(UUID.randomUUID())
                .homeTeamId(t1.getId())
                .awayTeamId(t2.getId())
                .score(Score.builder().homeGoals(2).awayGoals(1).build())
                .status(null)
                .build();

        LeagueTable table = new LeagueTable(List.of(t1, t2), List.of(m), RankingStrategy.STANDARD);

        assertEquals(2, table.getStandings().size());
        Standing s = table.getStandings().stream()
                .filter(x -> x.stats().teamId().equals(t1.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, s.stats().points());
    }

    @Test
    void alphabeticalByShortNameShouldWork() {
        Team t1 = Team.builder()
                .id(UUID.randomUUID())
                .name("Team Z")
                .shortName("Zed")
                .slug(null)
                .tla("TZ")
                .build();
        Team t2 = Team.builder()
                .id(UUID.randomUUID())
                .name("Team A")
                .shortName("Ant")
                .slug(null)
                .tla("TA")
                .build();

        LeagueTable table = new LeagueTable(List.of(t1, t2), List.of(), RankingStrategy.STANDARD);

        // Should be sorted alphabetically by shortName: Ant, Zed
        assertEquals(t2.getId(), table.getStandings().get(0).stats().teamId());
        assertEquals(t1.getId(), table.getStandings().get(1).stats().teamId());
    }
}

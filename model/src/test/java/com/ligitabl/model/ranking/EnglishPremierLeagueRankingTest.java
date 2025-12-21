package com.ligitabl.model.ranking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.domain.standings.ranking.RankingStrategy;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.table.LeagueTable;

class EnglishPremierLeagueRankingTest {

    private UUID id(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes());
    }

    private Team team(String shortName, String tla) {
        return Team.builder()
                .id(UUID.randomUUID())
                .name(shortName)
                .shortName(shortName)
                .slug(TeamSlug.of(tla))
                .tla(tla)
                .build();
    }

    private Match match(UUID homeTeamId, UUID awayTeamId, int homeGoals, int awayGoals) {
        return match(null, homeTeamId, awayTeamId, homeGoals, awayGoals);
    }

    private Match match(UUID roundId, UUID homeTeamId, UUID awayTeamId, int homeGoals, int awayGoals) {
        return Match.builder()
                .id(UUID.randomUUID())
                .seasonId(UUID.randomUUID())
                .roundId(roundId != null ? roundId : UUID.randomUUID())
                .homeTeamId(homeTeamId)
                .awayTeamId(awayTeamId)
                .score(Score.builder().homeGoals(homeGoals).awayGoals(awayGoals).build())
                .build();
    }

    @Test
    void ranksTeamsByPointsFirst() {
        Team ars = team("Arsenal", "ARS");
        Team mci = team("Man City", "MCI");
        Team liv = team("Liverpool", "LIV");

        List<Match> matches = List.of(
                // Arsenal wins both
                match(ars.getId(), mci.getId(), 2, 1),
                match(liv.getId(), ars.getId(), 0, 2),
                // Liverpool draws with City
                match(mci.getId(), liv.getId(), 1, 1));

        LeagueTable table = new LeagueTable(List.of(ars, mci, liv), matches, RankingStrategy.ENGLISH_PREMIER_LEAGUE);
        List<Standing> standings = table.getStandings();

        // Debug output to help diagnose ordering/points
        standings.forEach(s -> System.out.println(s));

        // Arsenal: 6 points (2 wins)
        // Liverpool: 1 point (1 draw)
        // Man City: 1 point (1 draw)

        // Arsenal should have 6 points
        Standing arsStanding = standings.stream()
                .filter(s -> s.stats().teamId().equals(ars.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(6, arsStanding.stats().points());
        assertEquals(1, arsStanding.position());

        // Liverpool vs Man City tied on points (1 each). Alphabetical tie-break places Liverpool before Man City
        Standing livStanding = standings.stream()
                .filter(s -> s.stats().teamId().equals(liv.getId()))
                .findFirst()
                .orElseThrow();
        Standing cityStanding = standings.stream()
                .filter(s -> s.stats().teamId().equals(mci.getId()))
                .findFirst()
                .orElseThrow();
        // Goal difference tiebreak should favor Man City here
        assertTrue(cityStanding.position() < livStanding.position());
        assertEquals(3, standings.size());
    }
}

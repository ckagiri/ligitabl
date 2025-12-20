package com.ligitabl.model.standings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.standings.stats.TeamStats;
import com.ligitabl.model.domain.standings.table.TeamMatchView;

class TeamStatsTest {

    @Test
    void emptyStatsShouldHaveZeroValues() {
        var team1 = UUID.randomUUID();
        TeamStats stats = TeamStats.empty(team1);
        assertEquals(team1, stats.teamId());
        assertEquals(0, stats.played());
        assertEquals(0, stats.points());
        assertEquals(0, stats.goalsFor());
    }

    @Test
    void withMatchShouldAccumulate() {
        var team1 = UUID.randomUUID();
        var team2 = UUID.randomUUID();

        TeamStats stats = TeamStats.empty(team1);
        TeamMatchView view = new TeamMatchView(team1, team2, 2, 1, true);

        TeamStats updated = stats.withMatch(view);

        assertEquals(1, updated.played());
        assertEquals(1, updated.won());
        assertEquals(0, updated.drawn());
        assertEquals(0, updated.lost());
        assertEquals(2, updated.goalsFor());
        assertEquals(1, updated.goalsAgainst());
        assertEquals(3, updated.points());
        assertEquals(1, updated.homePlayed());
        assertEquals(3, updated.homePoints());
    }

    @Test
    void inconsistentPlayedShouldThrow() {
        var team1 = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TeamStats(team1, 10, 5, 3, 1, 20, 10, 5, 5, 5, 15, 15));
    }

    @Test
    void helperMethodsShouldCalculate() {
        var team1 = UUID.randomUUID();
        TeamStats stats = new TeamStats(team1, 10, 7, 2, 1, 25, 10, 12, 5, 5, 15, 14);

        assertEquals(2.3, stats.pointsPerGame(), 0.01);
        assertEquals(0.7, stats.winRate(), 0.01);
        assertEquals(2.5, stats.goalsForPerGame(), 0.01);
        assertEquals("7-2-1", stats.formattedRecord());
    }
}

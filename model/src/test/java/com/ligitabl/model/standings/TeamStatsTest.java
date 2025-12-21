package com.ligitabl.model.standings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.TeamMatchView;
import com.ligitabl.model.domain.standings.stats.TeamStats;

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
}

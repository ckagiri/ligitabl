package com.ligitabl.model.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TMatch;
import com.ligitabl.model.db.tables.TRound;
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.model.db.tables.TTeam;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.infra.MatchPersistenceAdapter;

@Tag("integration")
class MatchRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static MatchRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        jdbc = TestDbConnections.open();
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new MatchPersistenceAdapter(dsl);

        // Clean slate (respect FK order)
        TestDbCleaner.truncatePublicTables(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @Test
    void findByRoundId_returns_matches_ordered_by_kickoff() throws Exception {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();

        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, competitionId)
                .set(TCompetition.T_COMPETITION.C_NAME, "Premier League")
                .set(TCompetition.T_COMPETITION.C_SLUG, "premier-league")
                .set(TCompetition.T_COMPETITION.C_CODE, "PL")
                .execute();

        dsl.insertInto(TSeason.T_SEASON)
                .set(TSeason.T_SEASON.PK_ID, seasonId)
                .set(TSeason.T_SEASON.C_CLIENT_ID, 1)
                .set(TSeason.T_SEASON.FK_COMPETITION_ID, competitionId)
                .set(TSeason.T_SEASON.C_NAME, "2024/25")
                .set(TSeason.T_SEASON.C_SLUG, "2024-25")
                .set(TSeason.T_SEASON.C_START_DATE, LocalDate.of(2024, 8, 1))
                .set(TSeason.T_SEASON.C_END_DATE, LocalDate.of(2025, 5, 31))
                .set(TSeason.T_SEASON.C_MAX_ROUNDS, 38)
                .set(TSeason.T_SEASON.C_CURRENT_MATCH_DAY, 1)
                .execute();

        dsl.insertInto(TTeam.T_TEAM)
                .set(TTeam.T_TEAM.PK_ID, homeTeamId)
                .set(TTeam.T_TEAM.C_NAME, "Home Team")
                .set(TTeam.T_TEAM.C_SLUG, "home-team")
                .set(TTeam.T_TEAM.C_SHORT_NAME, "HOME")
                .set(TTeam.T_TEAM.C_TLA, "HOM")
                .execute();

        dsl.insertInto(TTeam.T_TEAM)
                .set(TTeam.T_TEAM.PK_ID, awayTeamId)
                .set(TTeam.T_TEAM.C_NAME, "Away Team")
                .set(TTeam.T_TEAM.C_SLUG, "away-team")
                .set(TTeam.T_TEAM.C_SHORT_NAME, "AWAY")
                .set(TTeam.T_TEAM.C_TLA, "AWY")
                .execute();

        dsl.insertInto(TRound.T_ROUND)
                .set(TRound.T_ROUND.PK_ID, roundId)
                .set(TRound.T_ROUND.FK_SEASON_ID, seasonId)
                .set(TRound.T_ROUND.C_NAME, "Matchday 1")
                .set(TRound.T_ROUND.C_SLUG, "md-1")
                .set(TRound.T_ROUND.C_POSITION, 1)
                .execute();

        UUID match1Id = UUID.randomUUID();
        UUID match2Id = UUID.randomUUID();

        ObjectMapper mapper = new ObjectMapper();
        JSONB scoreJson = JSONB.jsonb(mapper.writeValueAsString(
                Score.builder().homeGoals(2).awayGoals(1).build()));

        OffsetDateTime kickOff1 = OffsetDateTime.of(2024, 8, 10, 15, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime kickOff2 = OffsetDateTime.of(2024, 8, 11, 15, 0, 0, 0, ZoneOffset.UTC);

        dsl.insertInto(TMatch.T_MATCH)
                .set(TMatch.T_MATCH.PK_ID, match2Id)
                .set(TMatch.T_MATCH.C_CLIENT_ID, 1)
                .set(TMatch.T_MATCH.FK_SEASON_ID, seasonId)
                .set(TMatch.T_MATCH.FK_ROUND_ID, roundId)
                .set(TMatch.T_MATCH.FK_HOME_TEAM_ID, homeTeamId)
                .set(TMatch.T_MATCH.FK_AWAY_TEAM_ID, awayTeamId)
                .set(TMatch.T_MATCH.C_SLUG, "match-2")
                .set(TMatch.T_MATCH.C_STATUS, "FINISHED")
                .set(TMatch.T_MATCH.C_KICK_OFF, kickOff2)
                .set(TMatch.T_MATCH.C_VENUE, "Stadium 2")
                .set(TMatch.T_MATCH.C_MATCHDAY, 1)
                .set(TMatch.T_MATCH.C_SCORE, scoreJson)
                .execute();

        dsl.insertInto(TMatch.T_MATCH)
                .set(TMatch.T_MATCH.PK_ID, match1Id)
                .set(TMatch.T_MATCH.C_CLIENT_ID, 1)
                .set(TMatch.T_MATCH.FK_SEASON_ID, seasonId)
                .set(TMatch.T_MATCH.FK_ROUND_ID, roundId)
                .set(TMatch.T_MATCH.FK_HOME_TEAM_ID, homeTeamId)
                .set(TMatch.T_MATCH.FK_AWAY_TEAM_ID, awayTeamId)
                .set(TMatch.T_MATCH.C_SLUG, "match-1")
                .set(TMatch.T_MATCH.C_STATUS, "FINISHED")
                .set(TMatch.T_MATCH.C_KICK_OFF, kickOff1)
                .set(TMatch.T_MATCH.C_VENUE, "Stadium 1")
                .set(TMatch.T_MATCH.C_MATCHDAY, 1)
                .set(TMatch.T_MATCH.C_SCORE, scoreJson)
                .execute();

        List<Match> matches = repo.findByRoundId(roundId);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getId()).isEqualTo(match1Id);
        assertThat(matches.get(1).getId()).isEqualTo(match2Id);
        assertThat(matches.getFirst().getScore()).isNotNull();
        assertThat(matches.getFirst().getScore().getHomeGoals()).isEqualTo(2);
        assertThat(matches.getFirst().getScore().getAwayGoals()).isEqualTo(1);
    }

    @Test
    void findBySeasonId_returns_all_season_matches_ordered_by_kickoff_nulls_last() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID otherSeasonId = UUID.randomUUID();
        UUID round1Id = UUID.randomUUID();
        UUID round2Id = UUID.randomUUID();
        UUID otherRoundId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();

        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, competitionId)
                .set(TCompetition.T_COMPETITION.C_NAME, "Season Query League")
                .set(TCompetition.T_COMPETITION.C_SLUG, "season-query-league")
                .set(TCompetition.T_COMPETITION.C_CODE, "SQL")
                .execute();

        insertSeason(seasonId, competitionId, 100, "2025/26", "sq-2025-26");
        insertSeason(otherSeasonId, competitionId, 101, "2026/27", "sq-2026-27");

        dsl.insertInto(TTeam.T_TEAM)
                .set(TTeam.T_TEAM.PK_ID, homeTeamId)
                .set(TTeam.T_TEAM.C_NAME, "Season Home")
                .set(TTeam.T_TEAM.C_SLUG, "season-home")
                .set(TTeam.T_TEAM.C_SHORT_NAME, "SHOME")
                .set(TTeam.T_TEAM.C_TLA, "SHO")
                .execute();
        dsl.insertInto(TTeam.T_TEAM)
                .set(TTeam.T_TEAM.PK_ID, awayTeamId)
                .set(TTeam.T_TEAM.C_NAME, "Season Away")
                .set(TTeam.T_TEAM.C_SLUG, "season-away")
                .set(TTeam.T_TEAM.C_SHORT_NAME, "SAWAY")
                .set(TTeam.T_TEAM.C_TLA, "SAW")
                .execute();

        insertRound(round1Id, seasonId, 1, "sq-md-1");
        insertRound(round2Id, seasonId, 2, "sq-md-2");
        insertRound(otherRoundId, otherSeasonId, 1, "sq-other-md-1");

        UUID laterMatch = UUID.randomUUID();
        UUID earlierMatch = UUID.randomUUID();
        UUID unscheduledMatch = UUID.randomUUID();
        UUID otherSeasonMatch = UUID.randomUUID();

        OffsetDateTime earlier = OffsetDateTime.of(2025, 8, 16, 15, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime later = OffsetDateTime.of(2025, 8, 23, 15, 0, 0, 0, ZoneOffset.UTC);

        insertMatch(laterMatch, 201, seasonId, round2Id, homeTeamId, awayTeamId, "sq-match-later", later);
        insertMatch(earlierMatch, 202, seasonId, round1Id, homeTeamId, awayTeamId, "sq-match-earlier", earlier);
        insertMatch(unscheduledMatch, 203, seasonId, round2Id, homeTeamId, awayTeamId, "sq-match-tbd", null);
        insertMatch(
                otherSeasonMatch, 204, otherSeasonId, otherRoundId, homeTeamId, awayTeamId, "sq-match-other", earlier);

        List<Match> matches = repo.findBySeasonId(seasonId);

        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).getId()).isEqualTo(earlierMatch);
        assertThat(matches.get(1).getId()).isEqualTo(laterMatch);
        assertThat(matches.get(2).getId()).isEqualTo(unscheduledMatch);
        assertThat(matches).extracting(Match::getSeasonId).containsOnly(seasonId);
    }

    private static void insertSeason(UUID seasonId, UUID competitionId, int clientId, String name, String slug) {
        dsl.insertInto(TSeason.T_SEASON)
                .set(TSeason.T_SEASON.PK_ID, seasonId)
                .set(TSeason.T_SEASON.C_CLIENT_ID, clientId)
                .set(TSeason.T_SEASON.FK_COMPETITION_ID, competitionId)
                .set(TSeason.T_SEASON.C_NAME, name)
                .set(TSeason.T_SEASON.C_SLUG, slug)
                .set(TSeason.T_SEASON.C_START_DATE, LocalDate.of(2025, 8, 1))
                .set(TSeason.T_SEASON.C_END_DATE, LocalDate.of(2026, 5, 31))
                .set(TSeason.T_SEASON.C_MAX_ROUNDS, 38)
                .set(TSeason.T_SEASON.C_CURRENT_MATCH_DAY, 1)
                .execute();
    }

    private static void insertRound(UUID roundId, UUID seasonId, int position, String slug) {
        dsl.insertInto(TRound.T_ROUND)
                .set(TRound.T_ROUND.PK_ID, roundId)
                .set(TRound.T_ROUND.FK_SEASON_ID, seasonId)
                .set(TRound.T_ROUND.C_NAME, "Matchday " + position)
                .set(TRound.T_ROUND.C_SLUG, slug)
                .set(TRound.T_ROUND.C_POSITION, position)
                .execute();
    }

    private static void insertMatch(
            UUID matchId,
            int clientId,
            UUID seasonId,
            UUID roundId,
            UUID homeTeamId,
            UUID awayTeamId,
            String slug,
            OffsetDateTime kickOff) {
        dsl.insertInto(TMatch.T_MATCH)
                .set(TMatch.T_MATCH.PK_ID, matchId)
                .set(TMatch.T_MATCH.C_CLIENT_ID, clientId)
                .set(TMatch.T_MATCH.FK_SEASON_ID, seasonId)
                .set(TMatch.T_MATCH.FK_ROUND_ID, roundId)
                .set(TMatch.T_MATCH.FK_HOME_TEAM_ID, homeTeamId)
                .set(TMatch.T_MATCH.FK_AWAY_TEAM_ID, awayTeamId)
                .set(TMatch.T_MATCH.C_SLUG, slug)
                .set(TMatch.T_MATCH.C_STATUS, "SCHEDULED")
                .set(TMatch.T_MATCH.C_KICK_OFF, kickOff)
                .set(TMatch.T_MATCH.C_MATCHDAY, 1)
                .execute();
    }
}

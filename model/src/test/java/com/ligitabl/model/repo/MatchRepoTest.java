package com.ligitabl.model.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.JSONB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

class MatchRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static MatchRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "55432");
        String db = System.getenv().getOrDefault("DB_NAME", "ligitabl");
        String user = System.getenv().getOrDefault("DB_USER", "ligitabl");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "ligitabl");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        jdbc = DriverManager.getConnection(url, user, password);
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new MatchPersistenceAdapter(dsl);

        // Clean slate (respect FK order)
        dsl.deleteFrom(TMatch.T_MATCH).execute();
        dsl.deleteFrom(TTeam.T_TEAM).execute();
        dsl.deleteFrom(TRound.T_ROUND).execute();
        dsl.deleteFrom(TSeason.T_SEASON).execute();
        dsl.deleteFrom(TCompetition.T_COMPETITION).execute();
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
        JSONB scoreJson = JSONB.jsonb(mapper.writeValueAsString(Score.builder()
                .homeTeam(2)
                .awayTeam(1)
                .build()));

        OffsetDateTime kickOff1 = OffsetDateTime.of(2024, 8, 10, 15, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime kickOff2 = OffsetDateTime.of(2024, 8, 11, 15, 0, 0, 0, ZoneOffset.UTC);

        dsl.insertInto(TMatch.T_MATCH)
                .set(TMatch.T_MATCH.PK_ID, match2Id)
                .set(TMatch.T_MATCH.C_CLIENT_ID, 1)
                .set(TMatch.T_MATCH.FK_ROUND_ID, roundId)
            .set(TMatch.T_MATCH.FK_HOME_TEAM_ID, homeTeamId)
            .set(TMatch.T_MATCH.FK_AWAY_TEAM_ID, awayTeamId)
                .set(TMatch.T_MATCH.C_NAME, "Match 2")
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
                .set(TMatch.T_MATCH.FK_ROUND_ID, roundId)
            .set(TMatch.T_MATCH.FK_HOME_TEAM_ID, homeTeamId)
            .set(TMatch.T_MATCH.FK_AWAY_TEAM_ID, awayTeamId)
                .set(TMatch.T_MATCH.C_NAME, "Match 1")
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
        assertThat(matches.get(0).getScore()).isNotNull();
        assertThat(matches.get(0).getScore().getHomeTeam()).isEqualTo(2);
        assertThat(matches.get(0).getScore().getAwayTeam()).isEqualTo(1);
    }
}

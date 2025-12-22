package com.ligitabl.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record4;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TMatch;
import com.ligitabl.model.db.tables.TRound;
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.seed.testsupport.AbstractSeedPostgresIT;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class MatchSeedingIntegrationTest extends AbstractSeedPostgresIT {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // Force demo seeding so MatchSeeder runs automatically.
        System.setProperty("seed.main", "seeding/demo-main.yaml");
    }

    @Autowired DSLContext dsl;

    @AfterAll
    static void cleanupSeedMainProperty() {
        System.clearProperty("seed.main");
    }

    @Test
    void matchSeeding_enforcesWeekendKickoffs_and_finishedRoundsHaveScores() {
        var competition = dsl.selectFrom(TCompetition.T_COMPETITION)
                .where(TCompetition.T_COMPETITION.C_SLUG.eq("super-premier-league"))
                .fetchAny();
        assertThat(competition).as("super-premier-league competition").isNotNull();

        var season = dsl.selectFrom(TSeason.T_SEASON)
                .where(TSeason.T_SEASON.FK_COMPETITION_ID.eq(competition.getId())
                        .and(TSeason.T_SEASON.C_SLUG.eq("2025")))
                .fetchAny();
        assertThat(season).as("demo season").isNotNull();

        LocalDate startDate = season.getStartDate();
        int finishedRounds = 19; // demo-season.yaml

        var rows = dsl.select(
                        TRound.T_ROUND.C_POSITION,
                        TMatch.T_MATCH.C_STATUS,
                        TMatch.T_MATCH.C_SCORE,
                        TMatch.T_MATCH.C_KICK_OFF)
                .from(TMatch.T_MATCH
                        .join(TRound.T_ROUND).on(TMatch.T_MATCH.FK_ROUND_ID.eq(TRound.T_ROUND.PK_ID)))
                .where(TRound.T_ROUND.FK_SEASON_ID.eq(season.getId()))
                .fetch();

        assertThat(rows).as("seeded matches").isNotEmpty();

        // Kickoff + status/score semantics
                for (Record4<Integer, String, JSONB, OffsetDateTime> row : rows) {
            Integer roundPosition = row.value1();
            String status = row.value2();
                        JSONB scoreJson = row.value3();
                        OffsetDateTime kickoff = row.value4();

            assertThat(roundPosition).isNotNull();
            assertThat(kickoff).as("kickoff time").isNotNull();
            assertThat(kickoff.toLocalDate())
                    .as("kickoff date >= season start")
                    .isAfterOrEqualTo(startDate);

            DayOfWeek dow = kickoff.getDayOfWeek();
            assertThat(dow)
                    .as("kickoff day is weekend")
                    .isIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

            if (roundPosition <= finishedRounds) {
                assertThat(status).isEqualTo("FINISHED");
                                assertThat(scoreJson).as("score for finished rounds").isNotNull();

                                Map<String, Object> score = parseScore(scoreJson.data());
                                int home = asInt(score.get("homeGoals"));
                                int away = asInt(score.get("awayGoals"));
                assertThat(home).isGreaterThanOrEqualTo(0);
                assertThat(away).isGreaterThanOrEqualTo(0);
            } else {
                assertThat(status).isEqualTo("SCHEDULED");
                                assertThat(scoreJson).as("no score for scheduled rounds").isNull();
            }
        }

        // Ensure we have matches across all 22 rounds (12 teams -> 6 matches per round)
        int distinctRounds = dsl.selectDistinct(TRound.T_ROUND.C_POSITION)
                .from(TMatch.T_MATCH.join(TRound.T_ROUND).on(TMatch.T_MATCH.FK_ROUND_ID.eq(TRound.T_ROUND.PK_ID)))
                .where(TRound.T_ROUND.FK_SEASON_ID.eq(season.getId()))
                .fetch(TRound.T_ROUND.C_POSITION)
                .size();

        assertThat(distinctRounds).as("round coverage").isEqualTo(22);
    }

        private static Map<String, Object> parseScore(String json) {
                try {
                        return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                        throw new AssertionError("Failed to parse score JSON: " + json, e);
                }
        }

        private static int asInt(Object value) {
                if (value instanceof Integer i) {
                        return i;
                }
                if (value instanceof Number n) {
                        return n.intValue();
                }
                if (value instanceof String s) {
                        return Integer.parseInt(s);
                }
                throw new AssertionError("Expected numeric score value but got: " + value);
        }
}
